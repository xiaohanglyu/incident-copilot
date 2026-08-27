package dev.xiaohanglyu.incidentcopilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * v1 serves {@code fixtures/<service>/changes.txt}. A real implementation queries GitHub
 * or the deployment pipeline.
 *
 * <p>Deliberately <em>not</em> filtered by the requested window. The whole value of change
 * history is what happened before the symptom appeared; clipping it to the incident window
 * would remove the deployment that caused the incident.
 */
@Component
public class ChangeHistoryTool {

    private final FixtureLoader fixtures;
    private final ToolCallLog log;

    ChangeHistoryTool(FixtureLoader fixtures, ToolCallLog log) {
        this.fixtures = fixtures;
        this.log = log;
    }

    @Tool(description = "List recent deployments and code changes for a service, with "
            + "timestamps. Always returns the full history regardless of any incident "
            + "window, because the change that caused an incident precedes it.")
    public String getRecentChanges(
            @ToolParam(description = "Service name, e.g. checkout-service") String service) {
        long started = System.currentTimeMillis();
        String result = fixtures.load(service, "changes.txt");
        log.record("getRecentChanges", "service=" + service, result,
                System.currentTimeMillis() - started);
        return result;
    }
}
