#!/usr/bin/env bash
# Release v1.4.0 locks the payment row during refunds on a raw connection that rejected
# refunds never return. Answer key: connection-leak.md. About 45 minutes at HOLD_SCALE=1.
source "$(dirname "$0")/lib.sh"

start_run connection-leak
deploy v1.3.0 "" "#212" "Return merchant reference in payment history" \
    "PaymentController.history: adds merchantRef to the response"
start_traffic
hold 15 "baseline"

deploy v1.3.1 "" "#219" "Bump logback and actuator config" \
    "configuration only: log rolling size, actuator exposure list"
hold 8 "after the harmless deploy"

deploy v1.4.0 connection-leak "#227" "Lock payment row during refund to prevent double refunds" \
    "RefundService: SELECT ... FOR UPDATE on its own connection before updating refunded_cents"
hold 20 "incident"

finish
