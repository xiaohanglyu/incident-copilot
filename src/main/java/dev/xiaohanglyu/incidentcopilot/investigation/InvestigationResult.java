package dev.xiaohanglyu.incidentcopilot.investigation;

import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeSnippet;
import dev.xiaohanglyu.incidentcopilot.tools.ToolCall;
import java.util.List;

/**
 * The response: what the model concluded, plus what actually happened on the way there.
 * Keeping the trail separate from the report is the point — a reader can check the
 * conclusion against the data instead of taking it on faith.
 */
public record InvestigationResult(
        Report report,
        List<ToolCall> toolCalls,
        List<KnowledgeSnippet> retrievedKnowledge
) {
}
