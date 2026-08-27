package dev.xiaohanglyu.incidentcopilot.tools;

/**
 * One tool invocation as it actually happened. Recorded by the tools themselves rather
 * than reported by the model, so it cannot be embellished.
 */
public record ToolCall(
        String tool,
        String arguments,
        String result,
        long millis
) {
}
