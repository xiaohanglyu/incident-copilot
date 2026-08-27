# Fixtures

Sample data the diagnostic tools return in v1. One directory per service, holding
`metrics.txt`, `logs.txt`, `changes.txt` and `profile.md`. Editing these changes what the
agent sees — no Java, no code changes.

Directory names are the service names. `listServices` reads them at startup, and a tool
asked about a service that has no directory says so and lists the ones that do, rather
than returning another service's data.

## profile.md

What the service's telemetry *means*: stack, log format and timezone, field units, enum
values, baselines, error signatures, dependencies.

Write down what the model **cannot** infer. It reads JSON and Logback output fine without
help — that is not worth a line. It cannot know that a timestamp has no offset and is
Asia/Shanghai, that `heap_used%` is a share of the pod limit rather than of `GOMEMLIMIT`,
or that 300ms p99 is abnormal *here*. Getting the format right and the unit wrong is a
failure with no outward sign.

Keep diagnostic reasoning out. "Rising latency with flat CPU and a saturated pool means
pool exhaustion" is a runbook statement and belongs in `resources/knowledge/`, where it
applies to every service. Duplicating it here is not harmless: profiles arrive as tool
results and outweigh retrieved text, so the agent stops citing the runbook and the
retrieval half of the system goes quiet.

## Scenarios

### checkout-service — connection pool exhaustion

Java · Logback · timestamps `yyyy-MM-dd HH:mm:ss.SSS`, **no offset**.

| Time | Where it shows up |
|---|---|
| 2026-08-24 14:20 | `changes.txt` — deploy v2025.8.19, query modified |
| 2026-08-24 14:25 | `metrics.txt` — latency climbing, pool at 44/50 |
| 2026-08-24 14:26 | `logs.txt` — first HikariPool timeout |
| 2026-08-24 14:30 | `metrics.txt` — pool saturated 50/50, 5xx at 4.1% |

CPU and memory stay flat throughout, on purpose: it lets the agent rule out resource
exhaustion and point at waiting instead.

### order-consumer — Kafka lag from an unbounded cache

Go · zerolog JSON · RFC3339 timestamps **with `+08:00`**. This scenario **crosses
midnight** — deliberately. Any implementation that compares `HH:MM` as text gets it
wrong, so this is the acceptance test for date handling being real.

| Time | Where it shows up |
|---|---|
| 2026-08-23 23:40 | `changes.txt` — deploy v4.2.0 adds a never-evicted map |
| 2026-08-23 23:50 | `metrics.txt` — heap climbing, lag still small |
| 2026-08-24 00:06 | `logs.txt` — first OutOfMemoryError, first rebalance |
| 2026-08-24 00:10+ | `metrics.txt` — restarts and rebalances accumulate, lag explodes |

CPU *falls* as lag grows, and the produce rate stays flat. Both are deliberate: they rule
out "more traffic" and "consumer is busy", leaving "consumer keeps dying".

The two services use different timestamp formats and timezone conventions on purpose.
That is the realistic case, and it is what makes each `profile.md` worth reading.

## Time filtering

`metrics.txt` and `logs.txt` are filtered by the window the tool was given. Two rules
matter:

- **The window is widened backwards** by `incident-copilot.window-lookback` (1h). A
  reported onset is when a human noticed; the cause is earlier. Without this, the 14:20
  deploy is invisible to anyone reporting the symptom at 14:25.
- **`changes.txt` is never filtered.** Change history exists to show what happened
  *before* the symptom. Clipping it to the incident window deletes the answer.

A window with no data returns the available range rather than an empty result, so the
agent can retry instead of concluding that nothing happened.

## Rules for adding one

The files of a scenario must stay consistent with each other. The agent can only
reconstruct a causal chain if the timestamps line up, and a broken timeline is the
fastest way to get an unstable report.

1. Put the trigger in `changes.txt` first, with a full `YYYY-MM-DD` timestamp.
2. Let `metrics.txt` degrade a few minutes later — the gap is what makes the deployment
   look causal rather than coincidental.
3. Have `logs.txt` show the failure mode after that, not before.
4. Include at least one signal that is deliberately *normal*, so there is something for
   the agent to rule out.
5. Write `profile.md` alongside — units and baselines, not diagnosis.

A scenario also wants a matching runbook in `resources/knowledge/`, otherwise retrieval
has nothing relevant to contribute and the report cites no `knowledge` source.
