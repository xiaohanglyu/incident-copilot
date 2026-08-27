# Runbook: Kafka consumer lag growing

## Symptoms

- Consumer lag rises continuously while the produce rate stays flat.
- Processing rate falls even though CPU is low or idle.
- Consumer group generation increments repeatedly; partitions are revoked and
  reassigned over and over.

## Why it happens

Lag with a flat produce rate means consumption slowed down, not that traffic grew. Low
CPU alongside falling throughput rules out the consumer being busy — it is being
interrupted.

The usual cause is a consumer that dies and rejoins in a loop. Every death triggers a
rebalance, every rebalance stops consumption across the whole group, and the group
spends more time rebalancing than working. A pod restarting on `OutOfMemoryError` is
the common trigger; so is a `poll()` that takes longer than `max.poll.interval.ms`,
which makes the broker evict a consumer that is still alive.

Unbounded in-memory state is the classic source of the OOM: a cache or dedup map that
grows with every message and is never evicted.

## How to confirm

1. Check whether heap usage climbs monotonically and fails to drop after a restart.
2. Correlate the first `OutOfMemoryError` with the first rebalance, and both with the
   most recent deployment.
3. Confirm broker health separately — flat produce rate and no under-replicated
   partitions mean the problem is on the consumer side.

## Mitigation

Roll back the deployment that introduced the unbounded state. Raising pod memory limits
buys time proportional to the leak rate and does not stop it. Increasing partition count
makes rebalances more expensive, not less.
