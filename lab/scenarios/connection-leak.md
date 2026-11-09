# Answer key: connection-leak

**Cause.** Deploy v1.4.0 (PR #227) takes a raw connection for refunds and returns early
without `close()` when a refund is rejected. About 3% of refunds are rejected (normal
client behaviour), so connections leak steadily. Once all 20 are gone, every request that
needs the database waits 30s and fails.

**Evidence a correct report cites.**
- `changes.txt`: v1.4.0 touches `RefundService` connection handling.
- `logs.txt`: `Connection leak detection triggered` with a stack trace through
  `RefundService.refundWithRowLock`, then `Connection is not available`.
- `metrics.txt`: active connections rise step by step to the maximum and stay there even
  though request rate does not change.

**Ruled out by the data.**
- v1.3.1 (config only): nothing changes for eight minutes after it.
- The refund-rejected WARNs: present at the same rate before v1.4.0; they are the trigger
  of the leak, not new behaviour.
- Slow queries: latency is fine until the pool is nearly empty, then jumps straight to ~30s.

**Wrong conclusions.** "Pool too small, raise maximum-pool-size" (it would only delay the
same failure); "slow query".
