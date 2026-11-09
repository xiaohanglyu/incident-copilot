# Answer key: unbounded-cache

**Cause.** Deploy v1.4.0 (PR #231) caches every created payment with a 16KB receipt in a
map keyed by `Idempotency-Key`, which is never evicted. Each key is unique, so the heap
grows with traffic (~20 payments/s, ~320KB/s) until G1 spends most of its time collecting
and allocations fail with `OutOfMemoryError: Java heap space`.

**Evidence a correct report cites.**
- `changes.txt`: v1.4.0 adds the cache.
- `metrics.txt`: heap used rises steadily after v1.4.0, GC pause time climbs with it,
  latency follows.
- `logs.txt`: `java.lang.OutOfMemoryError: Java heap space`.

**Ruled out by the data.**
- v1.4.1 (the latest deploy, thread count): the heap was already growing before it; after
  the restart it starts growing again from baseline at the same slope. It carries v1.4.0's
  code. (In the lab it changes only the version string.)
- More traffic: request rate is flat.
- Database: pool usage stays low until the JVM itself stalls.

**Wrong conclusions.** "v1.4.1 caused it" (the most recent deploy is not the cause);
"raise -Xmx" (delays it, doesn't fix it).
