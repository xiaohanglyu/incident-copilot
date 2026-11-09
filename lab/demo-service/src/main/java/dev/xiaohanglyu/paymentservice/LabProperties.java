package dev.xiaohanglyu.paymentservice;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What release is running and which bugs it contains.
 *
 * <p>Each {@link Fault} stands in for the code of one pull request. A scenario "deploys" a
 * version by restarting the service with a different set, so the restart, the version
 * change and the bug all reach the telemetry together, as they do in a real rollout.
 *
 * @param version shown in logs and recorded in the change history, e.g. {@code v1.4.0}
 * @param faults  bugs present in this version; empty means the healthy baseline
 */
@ConfigurationProperties("lab")
public record LabProperties(String version, Set<Fault> faults) {

    public LabProperties {
        version = version == null || version.isBlank() ? "dev" : version;
        faults = faults == null ? Set.of() : Set.copyOf(faults);
    }

    public boolean has(Fault fault) {
        return faults.contains(fault);
    }

    /** Bound from kebab-case, e.g. {@code LAB_FAULTS=slow-query,connection-leak}. */
    public enum Fault {
        /** Customer lookup made case-insensitive with lower(), which the index cannot serve. */
        SLOW_QUERY,
        /** Refund path takes a raw connection and skips close() when it rejects a refund. */
        CONNECTION_LEAK,
        /** Responses cached by idempotency key in a map that is never evicted. */
        UNBOUNDED_CACHE
    }
}
