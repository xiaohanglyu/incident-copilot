package dev.xiaohanglyu.incidentcopilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Lets the agent discover what it can look at. Without this, a question that names a
 * symptom but no service leaves the model with nothing to pass to the other tools, and
 * it gives up before investigating anything.
 */
@Component
public class ServiceCatalogTool {

    private final FixtureLoader fixtures;
    private final ToolCallLog log;

    ServiceCatalogTool(FixtureLoader fixtures, ToolCallLog log) {
        this.fixtures = fixtures;
        this.log = log;
    }

    @Tool(description = "List the services this copilot can pull metrics, logs and change "
            + "history for. Call this first when the question does not name a service.")
    public String listServices() {
        long started = System.currentTimeMillis();
        String result = "Services with data available: " + String.join(", ", fixtures.services());
        log.record("listServices", "", result, System.currentTimeMillis() - started);
        return result;
    }
}
