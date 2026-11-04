# Development and configuration

**English** · [简体中文](../zh-CN/development.md) · [Project overview](../../README.md)

Start with the local Gemma setup below, then use the later sections for frontend work,
tests, and other model endpoints. Run commands from the repository root unless stated
otherwise.

## Run with a local model

You need JDK 25, Maven, and a running model server with tool-calling support. The local
setup uses `gemma-4-26b-q4_k_m.gguf` through an OpenAI-compatible API. Start the model
server separately; this project does not download or launch Gemma.

Create or edit `src/main/resources/application-local.yml`:

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8081
      api-key: not-needed
      chat:
        options:
          model: gemma-4-26b-q4_k_m.gguf
```

Replace the endpoint and model identifier with values from your server. Port `8081` is
an example; the application itself uses `8080`. Replace `not-needed` if the server
requires authentication. The `openai` configuration key refers to the compatible
protocol, not a requirement to use an OpenAI model. This file is gitignored, so a fresh
clone needs its own copy.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open [localhost:8080](http://localhost:8080) and try “Why are Kafka messages backing up?”
The tools read sample data; no database, Kafka, or monitoring connection is needed.

Maven installs Node under `target/`, installs frontend dependencies, and builds the page.
A separate Node installation is not needed for this step. The knowledge base uses a
separate embedding model, whose first load may require a download. If that fails, use
the local-file configuration below.

## Frontend development

Start the backend as above. In another terminal, run the following commands. Running
frontend scripts directly requires a local Node 22 and npm installation.

```bash
cd web
npm ci
npm run dev
```

The development page defaults to `http://localhost:5173`; `/api` requests are proxied to
backend port `8080`. When working only on the backend with an existing frontend bundle,
you can skip Maven's frontend steps:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dskip.installnodenpm -Dskip.npm
```

## Test and package

Backend tests:

```bash
mvn test -Dskip.installnodenpm -Dskip.npm
```

Frontend checks and tests, from `web/`:

```bash
npm run typecheck
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

Playwright builds the frontend and starts a preview server. API responses are stubbed,
so these tests do not measure the real model's diagnostic ability. Backend tests also
do not constitute an evaluation of diagnosis accuracy.

Package and run:

```bash
mvn package
java -jar target/incident-copilot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

After changing resources, rerun Maven. An existing jar does not read changes in the
source tree. A resource-directory `application-local.yml` can also be included in a
locally built jar; inspect configuration before sharing build artifacts.

## Configure another model

Without the `local` profile, `application.yml` uses the environment variables below.
These fallback values belong to the base configuration, not the local Gemma setup.
With `local` enabled, matching settings in the local file override these placeholders.

| Environment variable | Base fallback | Purpose |
|---|---|---|
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Endpoint for the compatible model service |
| `OPENAI_API_KEY` | Empty | Credentials; a service without authentication can use a placeholder |
| `INCIDENT_COPILOT_MODEL` | `gpt-4o-mini` | Server's model identifier; tool calling is required |
| `INCIDENT_COPILOT_TEMPERATURE` | `0.2` | Generation temperature; lower values do not guarantee identical output |

For example, configure a local server without activating the local profile:

```bash
export OPENAI_BASE_URL=http://localhost:8081
export OPENAI_API_KEY=not-needed
export INCIDENT_COPILOT_MODEL=gemma-4-26b-q4_k_m.gguf
mvn spring-boot:run
```

## Application settings

These settings are under `incident-copilot` in `application.yml`:

| Property | Default | Purpose |
|---|---|---|
| `system-prompt` | `classpath:/prompts/investigation-system.md` | Model investigation instructions |
| `knowledge-location` | `classpath:/knowledge/*.md` | Runbooks read at startup |
| `top-k` | `5` | Maximum number of knowledge excerpts retrieved per investigation |
| `window-lookback` | `1h` | Backward extension of metrics and log query start times |
| `default-zone` | `Asia/Shanghai` | Time zone for fixture timestamps without an offset |

## If the embedding model cannot download

Chat and embedding models load separately. A working Gemma endpoint does not prevent
startup failure when embedding files cannot be downloaded. Fetch them in advance when
network access is available:

```bash
mkdir -p models
curl -fL -o models/tokenizer.json \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json
curl -fL -o models/model.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
```

Merge the following under the existing `spring.ai` section in `application-local.yml`.
Do not add a second top-level `spring` key:

```yaml
embedding:
  transformer:
    onnx:
      model-uri: file:./models/model.onnx
    tokenizer:
      uri: file:./models/tokenizer.json
```

`models/` is gitignored. These relative paths assume the application starts from the
repository root. Once the files are present, embedding does not need a remote service.
Build downloads and chat connections still depend on their own configuration and caches.
