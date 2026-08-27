package dev.xiaohanglyu.incidentcopilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Where the agent learns what a service's telemetry <em>means</em>. Log format it can
 * work out on its own; field units, enum values, baselines and dependencies it cannot.
 * Reading a timestamp correctly but the unit of a field wrongly is a failure with no
 * outward sign, which is why this is a tool rather than something left to inference.
 */
@Component
public class ServiceProfileTool {

    private final FixtureLoader fixtures;
    private final ToolCallLog log;

    ServiceProfileTool(FixtureLoader fixtures, ToolCallLog log) {
        this.fixtures = fixtures;
        this.log = log;
    }

    @Tool(description = "Describe a service: its stack, log format and timezone, the units "
            + "and meaning of its metric fields, normal baselines, known error signatures "
            + "and its dependencies. Call this before interpreting that service's logs or "
            + "metrics.")
    public String describeService(
            @ToolParam(description = "Service name, e.g. checkout-service") String service) {
        long started = System.currentTimeMillis();
        String result = fixtures.load(service, "profile.md");
        log.record("describeService", "service=" + service, result,
                System.currentTimeMillis() - started);
        return result;
    }
}
