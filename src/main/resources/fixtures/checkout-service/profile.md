# checkout-service

Synchronous HTTP checkout API. Java 21 · Spring Boot · Logback · HikariCP · PostgreSQL.

## Reading its telemetry

**Timestamps carry no offset.** Logback is configured as
`yyyy-MM-dd HH:mm:ss.SSS` in **Asia/Shanghai**. Nothing in a log line says so — read any
timestamp from this service as local time, and convert before comparing it against a
source that emits UTC.

| Field | Unit / meaning |
|---|---|
| `p50`, `p99` | Wall-clock latency of the HTTP handler. Suffixed (`ms`, `s`) — the unit is in the value, not the column. |
| `rps` | Requests per second, averaged over the bucket. |
| `db_pool_active/max` | HikariCP connections in use over pool size. `max` is 50 and is not autoscaled. |
| `5xx rate` | Share of responses, not a count. |

## Baselines

- p99 sits at 115–125ms. Anything above 300ms is abnormal for this service.
- The pool idles at 10–15 active. Sustained values above 40 mean requests are queueing.
- CPU runs 33–36% at normal traffic. This service is IO-bound: it spends far more time
  waiting on the database than computing.
- Roughly 5 WARN lines per minute at baseline, almost all of them retryable HTTP client
  warnings. A WARN rate in the hundreds is not normal.

## Error signatures

- `HikariPool-1 - Connection is not available` — emitted by HikariCP when a caller waited
  the full `connectionTimeout` (30s here) without getting a connection.
- `SQLTimeoutException` from `OrderService` — the application-level surface of the same
  wait. It is not a database-side timeout.

## Dependencies

Upstream: `api-gateway`. Downstream: PostgreSQL `orders` cluster, `payment-service`,
`inventory-service`. Every checkout request touches the `orders` cluster; the other two
are called only on the payment path.
