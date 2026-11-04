# Adding runbooks and incident scenarios

**English** · [简体中文](../zh-CN/extending.md) · [Project overview](../../README.md)

The knowledge base and demo data can be extended through resource files without Java
changes. Connecting real systems such as Prometheus or a logging platform requires
adapter code and is a separate task.

## Add a runbook

Create a Markdown file under `src/main/resources/knowledge/`. Describe symptoms,
possible causes, verification, and mitigation for a class of failures. For example:

```markdown
# Runbook: database connection pool exhaustion

## Symptoms

Request latency rises while pool usage stays near its limit.

## Possible causes

Slow queries, long transactions, or unreleased connections can hold connections too long.

## Verification

Compare changes with query duration, transaction duration, and connection hold times.

## Mitigation

Based on verification, consider reverting the change or addressing long transactions.
Describe the risks of each action.
```

At startup, documents are split at `## ` headings. Each chunk retains the document title
and introduction, so write sections that can be understood independently. After editing,
rerun Maven and restart the application to rebuild the in-memory index.

Runbooks contain general investigation guidance. Service-specific units, pool capacities,
and time zones belong in the service's `profile.md`, avoiding duplicate maintenance.

## Add a demo scenario

Each service has a directory under `src/main/resources/fixtures/`. Its directory name is
the service name used in queries:

```text
src/main/resources/fixtures/example-service/
├── profile.md
├── metrics.txt
├── logs.txt
└── changes.txt
```

| File | Contents |
|---|---|
| `profile.md` | Stack, field meanings, units, time zone, baselines, and dependencies |
| `metrics.txt` | Timestamped metrics covering normal behavior and incident progression |
| `logs.txt` | Errors and other signals consistent with the metrics timeline |
| `changes.txt` | Deployments or configuration changes with full times and descriptions |

The existing `checkout-service` shows pool exhaustion after a query slows down.
`order-consumer` shows lag caused by an unbounded cache. Use their formats as references,
but make the new scenario's data support its own conclusion.

## Keep the timeline consistent

1. Decide the expected cause and the evidence needed to verify it before writing data.
2. Record the triggering change and a plausible progression in subsequent metrics and logs.
3. Include normal signals or counterevidence, such as stable production rate during consumer lag, to distinguish competing explanations.
4. Put units, normal values, and time zones in the service description, without giving away the diagnosis.
5. Add a relevant runbook and prepare several questions, including ones without a service name.

The time filter recognizes full dates and times such as `2026-08-24 14:26:03.481` and
`2026-08-24T00:06:44+08:00`, including timestamps within JSON log fields. Timestamps
without offsets use the application's `default-zone`, currently shared across all such
fixtures. Do not use only `HH:mm`, especially for scenarios crossing midnight.

Services are discovered at startup through `*/metrics.txt`, and file contents are cached.
Rerun Maven and restart after adding or editing a scenario. Metrics and logs are filtered
by time, with the start extended backward by one hour by default. Changes are not
filtered by time.

## Check the new content

Use the [API](api.md) or web UI to investigate the new scenario and check:

- The service appears in `listServices`, and its description is accurate.
- Metrics and logs return within the requested window, or report the available range.
- `retrievedKnowledge` includes a relevant runbook.
- Each report evidence item can be found in tool results or retrieved excerpts.
- Vague questions and incomplete evidence produce reasonable responses.

Model results can vary, so manual checks do not replace an evaluation set. There is no
diagnostic evaluation framework yet. Record the expected cause, required evidence, and
conclusions that the data cannot support alongside new scenarios for future evaluation.
