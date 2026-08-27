package dev.xiaohanglyu.incidentcopilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * v1 serves {@code fixtures/<service>/logs.txt}. A real implementation queries Loki or
 * Elasticsearch.
 */
@Component
public class LogSearchTool {

    private final FixtureLoader fixtures;
    private final TimeWindow window;
    private final ToolCallLog log;

    LogSearchTool(FixtureLoader fixtures, TimeWindow window, ToolCallLog log) {
        this.fixtures = fixtures;
        this.window = window;
        this.log = log;
    }

    @Tool(description = "Search recent application logs of a service for errors, warnings "
            + "or a keyword. Optionally bounded by an ISO 8601 time window. Log format and "
            + "timezone differ per service — call describeService first.")
    public String searchLogs(
            @ToolParam(description = "Service name, e.g. checkout-service") String service,
            @ToolParam(description = "Keyword to match, e.g. timeout") String keyword,
            @ToolParam(description = "Window start, ISO 8601, e.g. 2026-08-24T00:00:00+08:00. "
                    + "Omit for all available data.", required = false) String fromTime,
            @ToolParam(description = "Window end, ISO 8601. Omit for open-ended.",
                    required = false) String toTime) {
        long started = System.currentTimeMillis();
        String result = fixtures.load(service, "logs.txt",
                window.parse(fromTime), window.parse(toTime));
        log.record("searchLogs",
                window.describe("service=%s, keyword=%s".formatted(service, keyword),
                        fromTime, toTime),
                result, System.currentTimeMillis() - started);
        return result;
    }
}
