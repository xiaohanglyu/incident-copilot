# incident-copilot

**English** · [简体中文](README.zh-CN.md)

An incident investigator built on Spring AI. Describe a production symptom in one
sentence; the agent calls read-only diagnostic tools, consults a local knowledge base,
and returns a structured hypothesis for a human to review.

> v1 is a self-contained demo. The diagnostic tools serve fixed sample data from
> `resources/fixtures`, so the whole thing runs with nothing but an API key — no
> database, no external services.

## What it does

The agent does not answer from the prompt alone. It investigates first:

```
User: "kafka message backlog, what is going on?"
  │
  ├─ listServices()       checkout-service, order-consumer
  ├─ getMetrics()         lag 180 → 1.2M · heap 48% → 95% · CPU falling
  ├─ searchLogs()         OutOfMemoryError · consumer group rebalancing
  ├─ getRecentChanges()   deploy v4.2.0 added an unevicted in-memory map
  └─ knowledge base       runbook: Kafka consumer lag
  │
  ▼
Report  ─  most likely cause · evidence · verification · mitigation
  │
  ▼
Human review
```

The question named no service, so the agent asked what it could look at, picked the one
matching the symptom, and investigated that. Every tool is read-only, and the report is a
proposal — nothing is executed on its own.

## Example

Open `http://localhost:8080` for a small page — English and Chinese, with the full
investigation trail expandable under the report. Or call the endpoint directly:

```bash
curl -X POST localhost:8080/api/investigate \
  -H 'Content-Type: application/json' \
  -d '{
        "query": "checkout-service response time suddenly spiked",
        "since": "2026-08-24T14:25:00+08:00",
        "services": ["checkout-service"]
      }'
```

Only `query` is required. `since` / `until` and `services` are what a ticket carries
anyway — supplied as leads, never as filters. Left out, the agent works out which service
to look at and how wide a window to pull.

```json
{
  "report": {
    "mostLikelyCause": "Connection pool exhaustion caused by v2025.8.19, which added a join
                        and removed the LIMIT clause in OrderRepository.findRecentByCustomer.",
    "confidence": 0.95,
    "evidence": [
      { "source": "getMetrics",       "detail": "p99 rose 118ms → 3.4s and the pool hit 50/50 from 14:25, CPU and memory flat" },
      { "source": "searchLogs",       "detail": "HikariPool connection timeouts start at 14:26:03" },
      { "source": "getRecentChanges", "detail": "Deploy at 14:20 modified OrderRepository.findRecentByCustomer" },
      { "source": "knowledge",        "detail": "Runbook: rising latency with flat CPU and a saturated pool is pool exhaustion" }
    ],
    "suggestedVerification": ["Check query latency for the modified SQL"],
    "suggestedMitigation": ["Roll back checkout-service to v2025.8.18"]
  },
  "toolCalls": [
    { "tool": "describeService", "arguments": "service=checkout-service", "millis": 1, "result": "…" },
    { "tool": "getMetrics", "arguments": "service=checkout-service, from=2026-08-24T14:00:00+08:00, to=2026-08-24T15:00:00+08:00", "millis": 3, "result": "…" },
    { "tool": "searchLogs", "arguments": "service=checkout-service, keyword=Connection is not available, from=…", "millis": 1, "result": "…" },
    { "tool": "getRecentChanges", "arguments": "service=checkout-service", "millis": 1, "result": "…" }
  ],
  "retrievedKnowledge": [
    { "source": "connection-pool-exhaustion.md", "content": "…" }
  ]
}
```

`report` is what the model concluded. `toolCalls` and `retrievedKnowledge` are what
actually happened, recorded server-side while the tools ran — so a reader can check the
conclusion against the data instead of taking the model's word for its own steps.

Note the first two calls. The agent looked up what checkout-service's fields mean before
reading them, then pulled a window starting at 14:00 — earlier than the 14:25 onset it
was given, because the cause of an incident precedes the symptom.

## How it works

Three mechanisms, one request:

| | |
|---|---|
| **Tool calling** | The model decides which tools to call, with what arguments, and in what order. Tools are plain Spring beans annotated with `@Tool`. It looks up what a service's fields *mean* before reading them, and narrows or widens the time window as the investigation goes. |
| **Retrieval** | Runbooks are embedded into a vector store at startup and retrieved explicitly rather than through an advisor, which keeps the matched documents in hand so they can be cited. |
| **Structured output** | The report is bound to a Java record, so the response shape is stable across runs. |

Four behaviours matter more than they look.

A tool asked about a service it has no data for **says so and lists what it does have**,
instead of returning another service's data — otherwise the model learns that whatever
comes back must be relevant and will bend it to fit the question.

**A requested time window is widened backwards** before any filtering. A reported onset is
when a human noticed; the cause is earlier. Report the checkout incident as starting at
14:25 and a literal reading hides the 14:20 deployment that caused it.

**Field semantics come from a tool, not from inference.** Log format the model handles
unaided — JSON and Logback text are both in its training data many times over. What it
cannot know is that a timestamp carries no offset and is Asia/Shanghai, or that
`heap_used%` is a share of the pod limit. Reading the format correctly and the unit
wrongly is a failure with no outward sign, so `describeService` is a tool call rather
than a hope.

**The steps are recorded as the tools run**, not reported by the model afterwards — a
self-reported trail is worth exactly as much as the rest of the model's output.

## Tech stack

| | |
|---|---|
| Java 25 · Spring Boot 4.1 | Records for the domain model, virtual threads available for tool fan-out |
| Spring AI 2.0 | Tool calling, structured output binding, vector store abstraction |
| OpenAI-compatible chat API | Any gateway works — configured by URL and key |
| all-MiniLM-L6-v2 via ONNX Runtime | Embeddings computed in-process, 384 dimensions |
| SimpleVectorStore | In-memory index, rebuilt at startup |
| Maven | Build and dependency management |

## Architecture

```
dev.xiaohanglyu.incidentcopilot
├── investigation    orchestration and domain model — Report, Evidence, InvestigationResult
├── tools            adapter layer, one class per data source, plus the call log
├── knowledge        embedding, indexing, retrieval
└── shared           configuration
```

```
resources
├── prompts/         the system prompt — editable without touching Java
├── knowledge/       runbooks — what a pattern means, across services (RAG)
├── fixtures/        per service: sample telemetry, plus profile.md — what this
│                    service's fields mean (looked up by name, not retrieved)
└── static/          the web UI
```

Dependencies run one way: `investigation → { tools, knowledge } → shared`. The adapter
layer never depends on the orchestrator, so adding a data source means adding a file
rather than editing one.

## Quick start

Requires JDK 25, Maven, and an API key for any OpenAI-compatible endpoint.

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

On first run the embedding model (~90 MB) is downloaded from GitHub and cached locally.
Later runs start straight away.

### Restricted networks

If `raw.githubusercontent.com` is unreachable — a proxy, a firewall, or TLS
interception — startup fails while loading the embedding model. Fetch the two files
once from Hugging Face instead:

```bash
mkdir -p models
curl -Lo models/tokenizer.json \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json
curl -Lo models/model.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
```

Then point the application at them:

```yaml
spring:
  ai:
    embedding:
      transformer:
        onnx:
          model-uri: file:./models/model.onnx
        tokenizer:
          uri: file:./models/tokenizer.json
```

`models/` is gitignored. With this in place startup needs no network at all, and the
only remaining outbound traffic is the chat API.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | — | Required. |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Point at any compatible gateway. |
| `INCIDENT_COPILOT_MODEL` | `gpt-4o-mini` | Chat model. Must support tool calling. |
| `INCIDENT_COPILOT_TEMPERATURE` | `0.2` | Lower values keep reports reproducible. |

Embeddings are computed in-process — no key, no network, no separate service.

Application-level settings live under `incident-copilot` in `application.yml`:

| Property | Default | Purpose |
|---|---|---|
| `system-prompt` | `classpath:/prompts/investigation-system.md` | The investigator's instructions. Edit the file, restart, no rebuild of Java. |
| `knowledge-location` | `classpath:/knowledge/*.md` | Markdown indexed at startup. |
| `top-k` | `5` | Knowledge chunks retrieved per investigation. |
| `window-lookback` | `1h` | How far back a requested window is widened. |
| `default-zone` | `Asia/Shanghai` | Zone for timestamps that carry no offset. |

Machine-specific values belong in `src/main/resources/application-local.yml`, which is
gitignored. Run it with `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

## Adding your own knowledge

Drop Markdown files into `src/main/resources/knowledge/`. They are split on level-two
headings, embedded, and indexed at startup. The vector store is in-memory, so the index
is rebuilt on every launch and the directory is the single source of truth.

## Adding your own scenario

Each service the tools know about is a directory under `src/main/resources/fixtures/`
holding `metrics.txt`, `logs.txt` and `changes.txt`. Two ship with the project:
`checkout-service` (connection pool exhaustion) and `order-consumer` (Kafka lag from an
unbounded cache). Adding a third is three text files — no code.

The three files of a scenario must stay consistent with each other; see
[fixtures/README.md](src/main/resources/fixtures/README.md) for why and how.

## Design notes

**Embeddings run in-process.** A remote chat API plus a local embedding model keeps
setup to one environment variable. The model produces 384-dimensional vectors; changing
it later means rebuilding the entire index, so the choice is fixed early.

**Retrieval is explicit, not an advisor.** Calling the knowledge service directly costs a
few more lines than wiring a retrieval advisor, but it keeps the matched documents in
hand — which is what allows the report to cite them, and what will allow re-retrieval
once the agent runs multiple rounds.

**One class per data source.** Three tools returning fixed data would fit in a single
class today, but each real source will bring its own client, credentials, and query
language.

**Runbooks are retrieved; service profiles are looked up.** What a pattern means
generalises across services, so it lives in the knowledge base and is retrieved by
similarity. What a specific service's fields mean does not generalise, and retrieval by
symptom would not surface it reliably — so it is a tool keyed by service name. Letting the
two overlap is not harmless: a profile that repeats runbook reasoning arrives as a tool
result, outweighs the retrieved text, and the report quietly stops citing the runbook at
all.

**The trail is recorded, not reported.** Tools write to a request-scoped log as they
run. The alternative — asking the model to list its own steps — produces a field that
looks like evidence but is generated by the same process it is meant to check.

**No persistence.** Investigations are not stored. Adding storage is additive and
deliberately deferred.

## License

MIT
