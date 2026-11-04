# Architecture and design

**English** · [简体中文](../zh-CN/architecture.md) · [Project overview](../../README.md)

The application has a Spring Boot backend and a React frontend. The backend retrieves
runbooks, exposes query tools to the model, and returns a structured report. The frontend
shows progress, tool results, and the report. Tools currently read bundled sample files;
the local chat model setup uses Gemma.

## Request flow

```text
Browser / API client
    → InvestigationController: accepts the question and optional time and service clues
    → InvestigationService: retrieves runbooks and builds model input
    → ChatClient: Spring AI handles exchanges between the model and tools
        → Service catalog, service description, metrics, logs, and change tools
        → FixtureLoader: reads sample files
        → ToolCallLog: records each tool call
    → Report: parses the model's cause, evidence, and suggestions
    → InvestigationResult: combines the report, tool records, and retrieved excerpts
    → Client
```

Knowledge retrieval runs once before the model call. It is not repeated as new evidence
arrives. The model chooses tools and arguments. The system prompt asks it to read service
descriptions before interpreting metrics or logs; code does not enforce that order.

The orchestrator makes one `ChatClient.call()`, which can involve multiple model requests
and tool exchanges managed by Spring AI. The planned multi-round workflow means explicit
application control over hypotheses, follow-up queries, retrieval, and stopping conditions.

Retrieval is called explicitly by the orchestrator. Matched excerpts are retained
in the final response so readers can check report citations.

## Data and tools

| Tool | Purpose | Current source |
|---|---|---|
| `listServices` | List available services | Service directories under fixtures |
| `describeService` | Read stack, field units, time zone, and baselines | `profile.md` |
| `getMetrics` | Query metrics | `metrics.txt` |
| `searchLogs` | Query logs | `logs.txt` |
| `getRecentChanges` | Query deployments and changes | `changes.txt` |

Metrics and logs can be filtered by the time window passed to the tool. The start is
extended backward by `window-lookback`, one hour by default, to include data before the
symptom. Change history is always returned in full. If a window has no data, the tool
returns the available range. An unmatched service returns the known service names.

Service resolution supports exact matches, case normalization, and substring matches.

Tools are split by query capability so each can handle its own live client, credentials,
and query syntax. Extending the time window helps find earlier clues; it does not prove
that the latest deployment caused the incident. Reported service names are also leads;
cross-service investigation still needs evaluation.

## Knowledge base

At startup, `KnowledgeIndexer` reads Markdown runbooks and splits them at level-two
headings. An in-process all-MiniLM-L6-v2 model creates 384-dimensional embeddings for
`SimpleVectorStore`. The index is held in memory and rebuilt at each startup. Chat and
embedding models are configured separately; Gemma does not generate the knowledge embeddings.

Service descriptions and runbooks are maintained separately. Descriptions explain a
service's fields, units, and baselines and are looked up by name. Runbooks explain how
to investigate a class of symptoms and are retrieved using the question. A pool limit
of 50 belongs in the service description; distinguishing slow queries from connection
leaks belongs in a runbook.

Local embedding avoids an extra service dependency. Replacing the embedding model
requires regenerating vectors and checking storage dimensions.

## Results and streaming

The regular endpoint returns JSON after the investigation finishes. The SSE endpoint
sends retrieved excerpts, completed tool calls, and a final result or error. Streaming
investigations run on virtual threads with a five-minute stream timeout.
See the [API reference](api.md).

During execution, tools write arguments, results, and duration to `ToolCallLog`.
`ScopedValue` separates each investigation's records. Records are returned with the
result as material for verification. They are not persisted, and the backend has no
history query feature. Correct citations and consideration of contradictory evidence
still need human review or future evaluation checks.

## Code locations

| Directory | Responsibility |
|---|---|
| `src/main/java/dev/xiaohanglyu/incidentcopilot/investigation/` | Endpoints, orchestration, report and response models |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/tools/` | Tools, fixture loading, time filtering, call records |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/knowledge/` | Markdown indexing, vector storage, retrieval |
| `src/main/java/dev/xiaohanglyu/incidentcopilot/shared/` | Application configuration |
| `src/main/resources/prompts/` | System prompt |
| `src/main/resources/knowledge/` | Runbooks |
| `src/main/resources/fixtures/` | Service descriptions and sample data |
| `web/src/` | React and TypeScript frontend, including the SSE client |
| `web/e2e/` | Playwright browser tests |

Maven builds the backend with Java 25, Spring Boot 4.1, and Spring AI 2.0. Vite writes the
frontend bundle to `target/classes/static/`, which Spring Boot serves as part of the
packaged application. Tools and knowledge services do not depend on the orchestrator.
Live adapters will need clients, configuration, and query mappings, while aiming to keep
the model-facing tool interfaces stable.

See [development](development.md) for build and run instructions and the
[roadmap](roadmap.md) for planned work.
