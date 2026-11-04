# API reference

**English** · [简体中文](../zh-CN/api.md) · [Project overview](../../README.md)

The local service defaults to `http://localhost:8080`, with the web UI at `/`.
The JSON and SSE investigation endpoints accept the same request body.

| Method and path | Response | Purpose |
|---|---|---|
| `POST /api/investigate` | JSON | Wait for the completed investigation |
| `POST /api/investigate/stream` | Server-Sent Events | Receive progress and the final result; used by the web UI |

## Request fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| `query` | string | Yes | Symptom or question; must not be empty or whitespace-only |
| `since` | string | No | Start of the observed symptom period, ISO 8601 with an offset or `Z` |
| `until` | string | No | End of the observed symptom period, in the same format |
| `services` | string[] | No | Services the reporter considers relevant |

Times and service names are passed to the model as context, not as direct restrictions
on available tools. The model can pass a time window to metrics and log tools, which
then filter data and extend the start backward according to their own rules.
See [architecture](architecture.md). All optional fields can be omitted.

```bash
curl http://localhost:8080/api/investigate \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "checkout-service response time suddenly increased",
    "since": "2026-08-24T14:25:00+08:00",
    "services": ["checkout-service"]
  }'
```

The example time matches the bundled data, which does not advance with the current date.

## JSON response

The response contains three parts:

| Field | Source and purpose |
|---|---|
| `report` | Model-generated cause, evidence, verification steps, and mitigations |
| `toolCalls` | Backend-recorded tool arguments, results, and duration |
| `retrievedKnowledge` | Runbook excerpts retrieved for this investigation |

This is a format example with simplified text and call records, not a fixed output:

```json
{
  "report": {
    "mostLikelyCause": "A query slowed after deployment, exhausting the database connection pool.",
    "confidence": 0.8,
    "evidence": [
      {"source": "getMetrics", "detail": "Pool usage reached 50/50 and latency increased."},
      {"source": "getRecentChanges", "detail": "A deployment before the incident changed a database query."}
    ],
    "suggestedVerification": ["Check the modified query's duration and execution plan."],
    "suggestedMitigation": ["Consider rolling back the version after confirming its impact."]
  },
  "toolCalls": [
    {"tool": "getMetrics", "arguments": "service=checkout-service", "result": "pool 50/50; p99 3.4s", "millis": 3},
    {"tool": "getRecentChanges", "arguments": "service=checkout-service", "result": "14:20 deployment changed a database query", "millis": 1}
  ],
  "retrievedKnowledge": [
    {"source": "connection-pool-exhaustion.md", "content": "Connection pool investigation guidance…"}
  ]
}
```

Each `report.evidence` item has a `source` and `detail`. The prompt asks for the tool name
or `knowledge` for a retrieved excerpt. `confidence` is a model-assigned score requested
on a 0–1 scale, not a calibrated probability of correctness. The server does not yet
enforce that range or verify individual evidence claims.

`toolCalls[].arguments` is a readable string, not a JSON argument object. `millis` measures
tool execution, not total model inference time. Actual responses include all tool calls
and retrieved excerpts from that investigation.

## SSE response

```bash
curl -N http://localhost:8080/api/investigate/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"query": "Why are Kafka messages backing up?"}'
```

A successful investigation sends events in this order. Each event's `data` is JSON:

| Event | Data | When sent |
|---|---|---|
| `knowledge` | Array of excerpts matching `retrievedKnowledge` | Once after retrieval; may be empty |
| `tool-call` | One `ToolCall` object | After each completed tool call; zero or more |
| `result` | The same complete response as the JSON endpoint | Once on completion, then the stream closes |
| `error` | `{"message":"error details"}` | Replaces the final result on failure; can occur at any stage |

Use `tool-call` events for progress and the complete records in `result` for the final
view. This is not a token-by-token model text stream. No suggested actions are executed
after `result`.

## Errors and disconnections

A blank `query`, malformed JSON, or an invalid timestamp returns HTTP 400 during request
processing. Investigation failures on the regular endpoint produce HTTP error responses.
Once SSE starts, failures are sent as `error` events where possible. Clients must wait
for `result` to establish success rather than relying only on HTTP status.

The stream timeout is five minutes. After a client disconnects, a failed event send
stops processing. There is no separate cancellation endpoint, and an in-flight model
request is not guaranteed to stop immediately. Retrying starts a new investigation;
historical results are not stored.
