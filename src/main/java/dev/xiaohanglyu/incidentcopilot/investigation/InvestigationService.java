package dev.xiaohanglyu.incidentcopilot.investigation;

import dev.xiaohanglyu.incidentcopilot.investigation.InvestigationController.InvestigationRequest;
import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeService;
import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeSnippet;
import dev.xiaohanglyu.incidentcopilot.shared.config.CopilotProperties;
import dev.xiaohanglyu.incidentcopilot.tools.ChangeHistoryTool;
import dev.xiaohanglyu.incidentcopilot.tools.LogSearchTool;
import dev.xiaohanglyu.incidentcopilot.tools.MetricsTool;
import dev.xiaohanglyu.incidentcopilot.tools.ServiceCatalogTool;
import dev.xiaohanglyu.incidentcopilot.tools.ServiceProfileTool;
import dev.xiaohanglyu.incidentcopilot.tools.ToolCallLog;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The whole demo in one place: retrieve knowledge explicitly, hand the model the
 * diagnostic tools, bind the answer to {@link Report}.
 */
@Service
public class InvestigationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ChatClient chatClient;
    private final KnowledgeService knowledgeService;
    private final CopilotProperties properties;
    private final ToolCallLog toolCallLog;
    private final ServiceCatalogTool serviceCatalogTool;
    private final ServiceProfileTool serviceProfileTool;
    private final MetricsTool metricsTool;
    private final LogSearchTool logSearchTool;
    private final ChangeHistoryTool changeHistoryTool;

    public InvestigationService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeService knowledgeService,
            CopilotProperties properties,
            ToolCallLog toolCallLog,
            ServiceCatalogTool serviceCatalogTool,
            ServiceProfileTool serviceProfileTool,
            MetricsTool metricsTool,
            LogSearchTool logSearchTool,
            ChangeHistoryTool changeHistoryTool) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeService = knowledgeService;
        this.properties = properties;
        this.toolCallLog = toolCallLog;
        this.serviceCatalogTool = serviceCatalogTool;
        this.serviceProfileTool = serviceProfileTool;
        this.metricsTool = metricsTool;
        this.logSearchTool = logSearchTool;
        this.changeHistoryTool = changeHistoryTool;
    }

    public InvestigationResult investigate(InvestigationRequest request) {
        List<KnowledgeSnippet> snippets = retrieve(request.query());

        Report report = chatClient.prompt()
                .system(properties.systemPrompt())
                .user(buildUserPrompt(request, snippets))
                .tools(serviceCatalogTool, serviceProfileTool,
                        metricsTool, logSearchTool, changeHistoryTool)
                .call()
                .entity(Report.class);

        return new InvestigationResult(report, toolCallLog.calls(), snippets);
    }

    /** Retrieval happens here, not in an advisor, so the hits can be cited in the report. */
    private List<KnowledgeSnippet> retrieve(String query) {
        return knowledgeService.search(query, properties.topK());
    }

    private String buildUserPrompt(InvestigationRequest request, List<KnowledgeSnippet> snippets) {
        String knowledge = snippets.isEmpty()
                ? "(nothing relevant found)"
                : snippets.stream()
                        .map(snippet -> "--- %s ---%n%s".formatted(snippet.source(), snippet.content()))
                        .collect(Collectors.joining("\n\n"));

        return """
                Reported by the on-call engineer:
                %s
                %s
                Runbook excerpts retrieved from the knowledge base. Cite these as "knowledge"
                when you use them:

                %s
                """.formatted(request.query(), reportedContext(request), knowledge);
    }

    /**
     * The optional ticket fields, worded so the model treats them as leads. Stating what
     * they are <em>not</em> matters: a window read as a hard boundary hides the cause, and
     * a service list read as a filter stops the agent looking upstream.
     */
    private String reportedContext(InvestigationRequest request) {
        StringBuilder context = new StringBuilder();

        if (request.since() != null || request.until() != null) {
            context.append("\nWhen the symptom was noticed");
            if (request.since() != null) {
                context.append(": from ").append(ISO.format(
                        request.since().atZone(properties.defaultZone())));
            }
            if (request.until() != null) {
                context.append(" until ").append(ISO.format(
                        request.until().atZone(properties.defaultZone())));
            }
            context.append("""
                    .
                    This is when a human noticed, not when the cause occurred. The cause is
                    earlier. Do not narrow your search to after this time.
                    """);
        }

        if (request.services() != null && !request.services().isEmpty()) {
            context.append("""

                    Services the reporter mentions: %s.
                    Treat these as leads, not as a boundary. On a multi-service ticket the
                    listed services are often all downstream victims of something nobody
                    named. If the evidence points elsewhere, follow it.
                    """.formatted(String.join(", ", request.services())));
        }

        return context.toString();
    }
}
