package dev.xiaohanglyu.incidentcopilot.investigation;

/**
 * One fact the report rests on, tagged with where it came from — a tool name or
 * {@code knowledge}. Citing the origin is what makes the reasoning auditable.
 */
public record Evidence(
        String source,
        String detail
) {
}
