#!/usr/bin/env bash
# Release v1.4.0 makes customer lookup case-insensitive and loses the index.
# Answer key: slow-query.md. About 50 minutes at HOLD_SCALE=1.
source "$(dirname "$0")/lib.sh"

start_run slow-query
deploy v1.3.0 "" "#212" "Return merchant reference in payment history" \
    "PaymentController.history: adds merchantRef to the response"
start_traffic
hold 15 "baseline"

# Decoy: a real restart close to the incident that changes nothing that matters.
deploy v1.3.1 "" "#219" "Bump logback and actuator config" \
    "configuration only: log rolling size, actuator exposure list"
hold 10 "after the harmless deploy"

deploy v1.4.0 slow-query "#224" "Make customer reference lookup case-insensitive" \
    "PaymentRepository.history: compares lower(customer_ref) = lower(?) so mixed-case references from the mobile app match"
hold 20 "incident"

finish
