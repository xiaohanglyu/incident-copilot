package dev.xiaohanglyu.incidentcopilot.knowledge;

/**
 * A retrieved chunk of a runbook or postmortem. The knowledge package owns this type
 * so it never has to depend on the investigation package.
 */
public record KnowledgeSnippet(
        String source,
        String content
) {
}
