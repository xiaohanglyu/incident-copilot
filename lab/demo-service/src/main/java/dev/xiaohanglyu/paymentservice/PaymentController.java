package dev.xiaohanglyu.paymentservice;

import java.sql.SQLException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
class PaymentController {

    private final PaymentRepository payments;
    private final RefundService refunds;
    private final IdempotencyCache cache;

    PaymentController(PaymentRepository payments, RefundService refunds, IdempotencyCache cache) {
        this.payments = payments;
        this.refunds = refunds;
        this.cache = cache;
    }

    @PostMapping
    ResponseEntity<Payment> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestBody CreatePayment request) {
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        long id = payments.create(request.customerRef(), request.merchantRef(),
                request.amountCents());
        Payment payment = payments.find(id).orElseThrow(() ->
                new IllegalStateException("Payment " + id + " missing right after insert"));
        cache.put(key, payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping
    List<Payment> history(@RequestParam String customerRef) {
        return payments.history(customerRef);
    }

    @PostMapping("/{id}/refunds")
    ResponseEntity<Void> refund(@PathVariable long id, @RequestBody Refund request)
            throws SQLException {
        return switch (refunds.refund(id, request.amountCents())) {
            case REFUNDED -> ResponseEntity.ok().build();
            case REJECTED -> ResponseEntity.unprocessableEntity().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    record CreatePayment(String customerRef, String merchantRef, long amountCents) {
    }

    record Refund(long amountCents) {
    }
}
