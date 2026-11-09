#!/usr/bin/env bash
# Release v1.4.0 caches every payment response by Idempotency-Key and never evicts.
# Answer key: unbounded-cache.md. About 50 minutes at HOLD_SCALE=1.
source "$(dirname "$0")/lib.sh"

start_run unbounded-cache
deploy v1.3.0 "" "#212" "Return merchant reference in payment history" \
    "PaymentController.history: adds merchantRef to the response"
start_traffic
hold 15 "baseline"

deploy v1.4.0 unbounded-cache "#231" "Return the original response on Idempotency-Key retries" \
    "PaymentController.create: caches the created payment and its rendered receipt by Idempotency-Key"
hold 10 "heap filling"

# Decoy after the cause: plausible-sounding, lands while the heap is already growing.
# It is a version-string-only release, so it keeps the fault and the restart resets the
# heap; the growth starting over from zero is itself evidence.
deploy v1.4.1 unbounded-cache "#233" "Raise Tomcat max threads to 250" \
    "configuration only: server.tomcat.threads.max 200 -> 250"
hold 20 "incident"

finish
