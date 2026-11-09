package dev.xiaohanglyu.paymentservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.xiaohanglyu.paymentservice.LabProperties.Fault;

/**
 * The UNBOUNDED_CACHE release: "return the original response when a client retries with
 * the same Idempotency-Key". Every key is unique in practice and nothing is ever removed,
 * so the heap grows with traffic until GC can't keep up. Without the fault this is a no-op.
 */
@Component
class IdempotencyCache {

    /** Size of the receipt kept with each response; sets how fast the heap fills. */
    static final int RECEIPT_BYTES = 16 * 1024;

    private final Map<String, Cached> entries = new ConcurrentHashMap<>();
    private final LabProperties lab;

    IdempotencyCache(LabProperties lab) {
        this.lab = lab;
    }

    Optional<Payment> get(String key) {
        if (key == null || !lab.has(Fault.UNBOUNDED_CACHE)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(key)).map(Cached::payment);
    }

    void put(String key, Payment payment) {
        if (key != null && lab.has(Fault.UNBOUNDED_CACHE)) {
            entries.put(key, new Cached(payment, new byte[RECEIPT_BYTES]));
        }
    }

    private record Cached(Payment payment, byte[] renderedReceipt) {
    }
}
