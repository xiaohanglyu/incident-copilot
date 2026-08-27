package dev.xiaohanglyu.incidentcopilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * v1 serves {@code fixtures/<service>/metrics.txt}. A real implementation queries
 * Prometheus.
 */
@Component
public class MetricsTool {

    private final FixtureLoader fixtures;
    private final TimeWindow window;
    private final ToolCallLog log;

    MetricsTool(FixtureLoader fixtures, TimeWindow window, ToolCallLog log) {
        this.fixtures = fixtures;
        this.window = window;
        this.log = log;
    }

    @Tool(description = "Read latency, error rate, CPU, memory and database connection pool "
            + "metrics for a service. Optionally bounded by an ISO 8601 time window; the "
            + "window is widened backwards automatically so a preceding cause stays visible.")
    public String getMetrics(
            @ToolParam(description = "Service name, e.g. checkout-service") String service,
            @ToolParam(description = "Window start, ISO 8601, e.g. 2026-08-24T14:25:00+08:00. "
                    + "Omit for all available data.", required = false) String fromTime,
            @ToolParam(description = "Window end, ISO 8601. Omit for open-ended.",
                    required = false) String toTime) {
        long started = System.currentTimeMillis();
        String result = fixtures.load(service, "metrics.txt",
                window.parse(fromTime), window.parse(toTime));
        log.record("getMetrics", window.describe("service=" + service, fromTime, toTime),
                result, System.currentTimeMillis() - started);
        return result;
    }
}
