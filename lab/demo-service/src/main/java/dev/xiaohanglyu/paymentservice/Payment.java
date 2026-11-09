package dev.xiaohanglyu.paymentservice;

import java.time.OffsetDateTime;

record Payment(
        long id,
        String customerRef,
        String merchantRef,
        long amountCents,
        long refundedCents,
        String status,
        OffsetDateTime createdAt) {
}
