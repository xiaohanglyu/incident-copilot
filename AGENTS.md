# AGENTS.md

Instructions for AI coding agents working in this repository. Human contributors can read
it too; the README and `docs/` remain the source of truth for behaviour and setup.

## What this project is

An AI incident investigation assistant. An on-call engineer describes a symptom; the
agent queries metrics, logs and recent changes through **read-only** tools, retrieves
runbooks, and returns a structured `Report` (cause, evidence, verification, mitigation)
for a human to review. Nothing is executed on the engineer's behalf.

- Backend: Java 25, Spring Boot 4, Spring AI 2 (OpenAI-compatible chat, in-process ONNX
  embeddings, `SimpleVectorStore`). Package root `dev.xiaohanglyu.incidentcopilot`.
- Frontend: `web/` — React 19 + TypeScript + Vite, no UI or state library.
- Data: v1 tools read fixtures from `src/main/resources/fixtures/`, not live systems.

## Layout

```text
src/main/java/dev/xiaohanglyu/incidentcopilot/
  investigation/   HTTP API (JSON + SSE), InvestigationService, Report/Evidence records
  knowledge/       Runbook indexing and retrieval
  tools/           @Tool classes the model can call, FixtureLoader, ToolCallLog
  shared/config/   CopilotProperties (binds `incident-copilot.*`)
src/main/resources/
  prompts/         System prompt for the investigation
  knowledge/       Runbooks (Markdown), indexed at startup
  fixtures/        One directory per service; see fixtures/README.md
web/src/
  api/             Request, SSE parsing, types mirroring the backend records
  components/      Presentational components
  investigation.ts Reducer for the investigation state; useInvestigation.ts drives it
  i18n.ts          All user-facing strings, English and Chinese
docs/en, docs/zh-CN  Architecture, API, development, extending, roadmap
```

## Commands

Run from the repository root unless noted.

```bash
# Backend tests (skip the frontend build)
mvn test -Dskip.installnodenpm -Dskip.npm

# Run locally (needs src/main/resources/application-local.yml, gitignored)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

From `web/`:

```bash
npm run typecheck
npm run lint
npm test
npm run test:e2e   # Playwright, API stubbed
```

Before calling a change done, run the checks for the side you touched. CI
(`.github/workflows/ci.yml`) runs all of the above.

## Design rules — do not break these

1. **Tools are read-only.** No tool may change production state (rollback, restart,
   scale, write). Suggested actions go in the report for a human to carry out.
2. **The trail is recorded, not narrated.** Every tool records its call through
   `ToolCallLog.record(...)`. `Report` has no field for the model's own account of its
   steps; do not add one.
3. **Ticket fields are leads, not filters.** `since`/`until` and `services` are context
   for the model. Never use them to narrow what tools return.
4. **Time windows widen backwards** by `window-lookback`; `changes.txt` is never
   filtered by time. Causes precede the symptom.
5. **Compare instants, not strings.** Fixtures mix timezones and one scenario crosses
   midnight on purpose. Parse to `Instant`/`OffsetDateTime`; never compare `HH:mm` text.
6. **Retrieval stays explicit** in `InvestigationService` (not an advisor) so retrieved
   snippets can be cited in the report.
7. **No new dependencies** (Maven or npm) without stating why in the change description.

## Java style

Follow the existing code; `tools/LogSearchTool.java` and `tools/ToolCallLog.java` are
good references.

- 4-space indent, 8-space continuation, lines under ~100 characters.
- Constructor injection with `private final` fields. No field injection, no Lombok.
  Tool constructors are package-private.
- Data carriers are `record`s; configuration binds to a record
  (`CopilotProperties`). Add new settings there and to `application.yml`.
- Explicit imports, no wildcards; static imports first.
- Use modern Java where it helps: text blocks with `.formatted(...)`, `ScopedValue`,
  virtual threads, `List.copyOf`.
- Errors: fail loudly with a message that names the thing that went wrong
  (`IllegalStateException("Tool " + tool + " ran outside an investigation...")`).
  Tools asked about unknown input should say so and list valid options instead of
  returning another service's data or an empty string.
- `@Tool` and `@ToolParam` descriptions are prompts the model reads. Keep them precise:
  units, formats (ISO 8601), what omitting a parameter means, and which tool to call
  first.

## TypeScript / React style

- 2-space indent, single quotes, semicolons, named exports.
- Function components and hooks only. Shared state goes through the reducer in
  `investigation.ts`; keep components presentational.
- `api/types.ts` mirrors the backend records. Change both sides together.
- Every user-facing string lives in `i18n.ts` with **both** `en` and `zh` entries.
- Styling uses the CSS variables in `styles.css` (light and dark). Do not hard-code
  colours.

## Comments and docs

- Comments explain **why** — the constraint, the failure it prevents, the non-obvious
  consequence. Do not restate what the code does. Javadoc the class's purpose and any
  surprising contract; skip boilerplate Javadoc on obvious getters and constructors.
- Docs are bilingual. When changing `README.md` or anything in `docs/en/`, update the
  matching `README.zh-CN.md` / `docs/zh-CN/` file in the same change.
- Do not claim capabilities the project lacks (approval workflow, persistent audit, data
  masking, measured MTTR improvement). The README states their status; keep it honest.

## Fixtures, runbooks, prompts

- Read `src/main/resources/fixtures/README.md` before touching fixtures. Scenario
  timestamps must form a consistent causal timeline, and each scenario keeps at least one
  deliberately normal signal.
- `profile.md` describes what telemetry *means* (units, formats, timezones, baselines).
  Diagnostic reasoning belongs in `resources/knowledge/` runbooks, not in profiles.
- A new scenario needs a matching runbook, or retrieval contributes nothing.
- Edits to `prompts/investigation-system.md` change model behaviour; mention them
  explicitly in the change description.

## Tests

- Backend: JUnit 5 + AssertJ. Test names are sentences in camelCase describing
  behaviour (`keepsConcurrentInvestigationsApart`). Prefer constructing classes directly
  over starting a Spring context.
- Frontend: Vitest + Testing Library for units, Playwright for end-to-end with the API
  stubbed.
- Tests check plumbing, not diagnosis quality. Do not write tests that assert on real
  model output.

## Git

- Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.
- Never commit `application-local.yml`, `config/`, `models/`, `decisions/`, `.env*`,
  `CLAUDE.local.md`, or anything under `target/`.
