# order-consumer

Kafka consumer that projects `orders.v1` into the read model.
Go 1.23 · zerolog · segmentio/kafka-go · 3 pods, 6 partitions.

## Reading its telemetry

**Timestamps are RFC3339 and carry an explicit `+08:00` offset**, unlike
`checkout-service`, whose Logback output has no offset at all. When correlating the two
services, convert before comparing — a naive string comparison will be an hour or eight
out depending on where you read it.

Logs are **JSON lines**, one object per line. The human-readable text is in `message`;
`error` carries the raw error string; `caller` is `package/file.go:line`.

| Field | Unit / meaning |
|---|---|
| `consumer_lag` | Messages behind the head, summed across all 6 partitions — not per-partition. |
| `msgs/sec` | Messages *consumed*. Compare against the broker's produce rate, which is reported separately. |
| `heap_used%` | Share of the pod's memory limit, not of Go's `GOMEMLIMIT`. |
| `pod_restarts` | Cumulative within the window, not a rate. |
| `rebalances` | Cumulative consumer-group rebalances within the window. |
| `generation` | Consumer group generation. It increments on every rebalance; a fast-climbing generation is itself the symptom. |

## Baselines

- Lag sits between 100 and 400 during normal operation. Anything above 5000 is abnormal.
- CPU runs 40–45% while consuming normally.
- Zero restarts and zero rebalances is the steady state. This group is not autoscaled.
- Baseline WARN rate is under 1 per minute.

## Error signatures

- `fatal: heap exhausted` with `caller: runtime/panic.go` — the Go runtime could not
  allocate. Kubernetes restarts the pod.
- `attempt to heartbeat failed since group is rebalancing` — emitted by kafka-go while
  the group is reassigning partitions.
- `consumer poll timeout expired` — processing exceeded `max.poll.interval.ms`, which is
  5 minutes for this group.

## Dependencies

Upstream: `checkout-service` and `payment-service` produce to `orders.v1`.
Downstream: the read-model PostgreSQL cluster.

The produce path is asynchronous, so lag here does not slow `checkout-service` down.
