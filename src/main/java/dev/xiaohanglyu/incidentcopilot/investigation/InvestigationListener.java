package dev.xiaohanglyu.incidentcopilot.investigation;

import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeSnippet;
import dev.xiaohanglyu.incidentcopilot.tools.ToolCall;
import java.util.List;

/**
 * Progress of one investigation, step by step. The final report is the method's return
 * value, not an event, so a caller cannot mistake a partial trail for a finished one.
 */
public interface InvestigationListener {

    InvestigationListener NONE = new InvestigationListener() {
    };

    /** Runbook excerpts retrieved before the model is called. */
    default void onKnowledge(List<KnowledgeSnippet> snippets) {
    }

    /** One diagnostic tool call, delivered as soon as the tool returns. */
    default void onToolCall(ToolCall call) {
    }
}
