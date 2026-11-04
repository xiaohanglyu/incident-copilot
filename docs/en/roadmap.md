# Roadmap

**English** · [简体中文](../zh-CN/roadmap.md) · [Project overview](../../README.md)

The project can investigate sample incidents. Next comes evaluation, followed by workflow
improvements, review and audit capabilities, and live integrations.
See [project overview](../../README.md).

## Completed

- Spring Boot backend, query tools, and structured reports.
- Sample incidents for connection pool exhaustion and Kafka consumer lag.
- Service descriptions and time filtering for metrics and logs.
- Markdown runbook chunking, local embeddings, and retrieval.
- Local configuration for Gemma.
- Backend recording of tool arguments, results, and duration.
- English and Chinese web UI with SSE progress updates.

These features form a working demo. They do not establish diagnostic accuracy, faster
diagnosis, or production readiness.

## Planned stages

| Order | Stage | Expected result |
|---|---|---|
| 1 | Evaluation set | Compare models, prompts, and workflows |
| 2 | Multi-round investigation | Gather follow-up evidence, retrieve again, and apply stopping conditions |
| 3 | Service context | Show suspect changes and service owners |
| 4 | Review, audit, and data protection | Store investigations, record approvals, and mask model input |
| 5 | Live data sources | Query monitoring, logging, and deployment systems |
| 6 | Persistent knowledge base | Update runbooks and lessons from incidents continuously |
| 7 | Operations and cost controls | Track usage, enforce budgets, and trace calls |

## 1. Build the evaluation set

Record the current workflow's performance so later changes have a baseline.

- [ ] Record expected causes, required evidence, and data sources for each scenario.
- [ ] Include vague questions, omitted service names, and inaccurate terminology.
- [ ] Add unrelated deployments, upstream failures, and insufficient-evidence scenarios.
- [ ] Add scenarios adapted from public postmortems.
- [ ] Check cause accuracy, evidence support, tool use, runbook retrieval, and repeatability.
- [ ] Record duration and model usage; compare the same model without tools and with the current workflow.
- [ ] Provide a repeatable evaluation command for prompt and model changes.

Collect feedback from practicing on-call engineers as part of evaluation. Measure
diagnosis time separately from MTTR: assessing recovery improvements requires
observing human review, decisions, and recovery actions too.

## 2. Add multi-round investigation

The current workflow makes one `ChatClient.call()`, with tool exchanges managed by
Spring AI. The next version would explicitly manage evidence gathering, hypotheses,
and follow-up investigation in application code.

- [ ] Track candidate causes with supporting and contradictory evidence.
- [ ] Query missing evidence and retrieve runbooks again as needed.
- [ ] Rank candidate causes with an explanation, allowing insufficient evidence as an outcome.
- [ ] Set round, token, and duration budgets.
- [ ] Compare both workflows on the same evaluation set, including accuracy gains and added cost.

## 3. Add service context

- [ ] Show diffs or configuration changes for suspect deployments.
- [ ] Provide service owners and escalation contacts.

## 4. Add review, audit, and data protection

- [ ] Persist investigation inputs, tool results, retrieved excerpts, and reports.
- [ ] Store recommendations as reviewable records.
- [ ] Record approvals, rejections, reviewer identity, and time.
- [ ] Process sensitive data before tool results reach the model.

Approval applies to recommendations; tools remain read-only. Automatic remediation is
not currently planned.

## 5. Connect live data sources

- [ ] Connect metrics tools to Prometheus.
- [ ] Connect log tools to Loki, translating keyword and time filters into Loki queries.
- [ ] Connect change tools to GitHub or deployment pipelines.
- [ ] Configure credentials, time ranges, and query mappings for each source.
- [ ] Verify time alignment and field meanings across sources.
- [ ] Assess subsets of datasets such as OpenRCA for adapters and external evaluation.

Keep existing tool interfaces where practical to limit orchestration changes. Permissions
and API limitations need validation when connecting each real system.

## 6. Persist the knowledge base

- [ ] Evaluate pgvector as a replacement for the in-memory store.
- [ ] Provide ingestion and update interfaces rather than indexing only at startup.
- [ ] Turn reviewed incident lessons into reusable knowledge entries.

## 7. Add operations and cost controls

SSE progress updates are available. Remaining work:

- [ ] Track tokens, duration, and cost per investigation.
- [ ] Apply investigation budgets and handle limits.
- [ ] Trace model requests and tool calls.
- [ ] Assess exposing the same read-only tools to external agents through MCP.
