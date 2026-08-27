# Runbook: database connection pool exhaustion

## Symptoms

- Request latency rises sharply while CPU and memory stay normal.
- Active connections sit at the configured maximum (for example `50/50`).
- Logs contain `HikariPool-1 - Connection is not available, request timed out`.

## Why it happens

Every request that needs the database waits for a free connection. When individual
queries slow down, connections are held longer, the pool saturates, and requests queue
behind it. Latency therefore climbs across *all* endpoints of the service, not only the
ones touching the slow query.

The most common trigger is a code change that alters a query — a dropped index hint, an
added join, a missing `LIMIT`, or lazy loading turned into an N+1 pattern.

## How to confirm

1. Compare the latency inflection point against the deployment timeline. A gap of a few
   minutes is typical: the pool drains gradually.
2. Look for slow queries in the database's own statistics rather than in the application.
3. Check whether CPU stayed flat. Saturated CPU points elsewhere; flat CPU with high
   latency points at waiting.

## Mitigation

Roll back the deployment that introduced the change. Raising the pool size is not a fix —
it delays saturation while adding load to the database.
