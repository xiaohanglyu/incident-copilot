# Roadmap

**English** · [简体中文](ROADMAP.zh-CN.md)

## Destination

An agent that investigates a production incident the way an on-call engineer does:
pull data from the systems that hold it, correlate across them, form ranked hypotheses,
and propose actions that a human approves or rejects.

```
Prometheus · Kubernetes · Loki · Elasticsearch · Datadog · GitHub · PagerDuty
                              │
                       Tool / adapter layer
                              │
                            Agent
                    ┌─────────┴─────────┐
              collect data        run diagnostics
                    └─────────┬─────────┘
                          Evidence
                              │
                         Hypotheses
                              │
                      Proposed actions
                              │
                        Human review
                              │
                       approve / reject
```

The agent never writes. Every action leaves the system as a proposal.

## Status

| Phase | | |
|---|---|---|
| 0 · Skeleton | Packages, dependencies, contracts | ✅ Done |
| 1 · Working demo | Tool calling, retrieval, stable report | 🚧 In progress |
| 2 · Real adapters | Replace fixtures with live data sources | ⬜ |
| 3 · Durable knowledge | pgvector, real postmortem ingestion | ⬜ |
| 4 · Agent loop | Multi-round investigation | ⬜ |
| 5 · Review flow | Persisted investigations, approve / reject | ⬜ |
| 6 · Operability | Streaming, budgets, observability | ⬜ |

## Phase 0 — Skeleton ✅

- [x] Package structure with one-way dependencies: `investigation → { tools, knowledge } → shared`
- [x] `Report` / `Evidence` records matching the documented API contract
- [x] Three `@Tool` classes declared, one per data source
- [x] Knowledge service, indexer and vector store configuration declared
- [x] In-process embedding chosen and wired — 384 dimensions, fixed early
- [x] Gitignored local profile for testing against a self-hosted model
- [x] README in English and Chinese

Every method body is still `TODO`. The project compiles; it does not run.

## Phase 1 — Working demo 🚧

The three things this project exists to show, end to end.

- [ ] Fixture data for the three tools, mutually consistent — timestamps, metric
      inflection and deployment time must line up, or the model cannot reach the
      intended conclusion
- [ ] Markdown chunking, embedding and indexing at startup
- [ ] Explicit retrieval feeding the prompt, with hits citable in the report
- [ ] Structured output bound to `Report`
- [ ] One documented example request that produces a stable answer

Exit criterion: the same query returns the same shape and the same conclusion
across repeated runs.

## Phase 2 — Real adapters

- [ ] Prometheus behind `MetricsTool`
- [ ] Loki behind `LogSearchTool`
- [ ] GitHub behind `ChangeHistoryTool`
- [ ] Time window alignment across sources — the hard part, and what makes
      correlation possible at all
- [ ] Credentials and per-source configuration

## Phase 3 — Durable knowledge

- [ ] pgvector replaces the in-memory store
- [ ] Ingestion API for postmortems, replacing startup-only indexing
- [ ] Resolved incidents feed back into the knowledge base

## Phase 4 — Agent loop

- [ ] Collect → hypothesize → collect again, instead of a single pass
- [ ] Ranked hypotheses, with "not enough evidence" as a valid outcome
- [ ] Re-retrieval driven by each hypothesis
- [ ] Iteration and token budgets

## Phase 5 — Review flow

- [ ] Investigations persisted rather than recomputed
- [ ] Proposed actions become reviewable records
- [ ] Approve / reject, with an audit trail

## Phase 6 — Operability

- [ ] Streaming or polling for long investigations
- [ ] Token and cost accounting per investigation
- [ ] Tracing across tool calls
- [ ] Optional: expose the same tools over MCP so external agents can drive them

