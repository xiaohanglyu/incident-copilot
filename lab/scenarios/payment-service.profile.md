# payment-service

Synchronous HTTP payment API: create payments, list a customer's history, refund.
Java 25 · Spring Boot 4 · Logback · HikariCP · PostgreSQL.

## Reading its telemetry

Logs use the Spring Boot default pattern. Timestamps are ISO-8601 with offset
(`2026-11-04T14:20:00.123+08:00`); the service runs in Asia/Shanghai. Metrics use the same
zone and format. Each restart writes a `payment-service <version> ready` line.

| Field | Unit / meaning |
|---|---|
| `p50`, `p99` | HTTP handler latency over the minute, actuator endpoints excluded. Unit in the value. |
| `rps` | Requests per second over the minute. |
| `5xx%` | Share of responses with status 500–599. |
| `cpu%` | JVM process CPU as a share of the CPUs available to the container (2). |
| `heap%` | Heap used over max heap (256MB, fixed). |
| `gc ms/s` | Milliseconds per second spent in GC pauses. |
| `pool active/max` | HikariCP connections in use over pool size. Max is 20. |
| `pending` | Threads waiting for a connection. Waits time out after 30s. |

A row of `-` means no scrape that minute, usually a restart.

## Baselines

Measured over the first minutes of this run, before any change after the initial deploy.

{{baselines}}

Refund requests that ask for more than the refundable amount are rejected with 422 and a
WARN line. Clients send these routinely; a few WARN per minute is normal.

## Error signatures

- `HikariPool-1 - Connection is not available, request timed out after 30000ms` — a
  caller waited the full connection timeout.
- `Connection leak detection triggered` — HikariCP saw a connection held out of the pool
  for over 60s; the stack trace shows where it was borrowed.

## Dependencies

Downstream: PostgreSQL `payments` database, a single table with about 3M rows indexed on
`(customer_ref, created_at)`. No other downstream calls.
