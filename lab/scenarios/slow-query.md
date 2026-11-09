# Answer key: slow-query

**Cause.** Deploy v1.4.0 (PR #224) changed the payment history query to
`lower(customer_ref) = lower(?)`. The index on `customer_ref` no longer applies, so every
history request scans about 3M rows. Connections are held for the length of the scan, the
pool (20) fills, and other requests wait up to 30s for a connection and fail.

**Evidence a correct report cites.**
- `changes.txt`: v1.4.0 shortly before latency rises; its description touches the query.
- `metrics.txt`: p99 and pool usage climb after v1.4.0; pending connections appear.
- `logs.txt`: `Connection is not available, request timed out after 30000ms` starts after
  v1.4.0, not before.

**Ruled out by the data.**
- v1.3.1 (config only): metrics stay at baseline for the ten minutes after it.
- Memory or GC: heap and GC pause stay at baseline.
- More traffic: request rate is set by the load generator and does not rise.

**Wrong conclusions.** "The database is down" (other queries still complete);
"connection leak" (no leak-detection warnings; the pool drains when load stops).
