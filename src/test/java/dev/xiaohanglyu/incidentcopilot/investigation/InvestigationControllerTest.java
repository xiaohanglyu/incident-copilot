package dev.xiaohanglyu.incidentcopilot.investigation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeSnippet;
import dev.xiaohanglyu.incidentcopilot.tools.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InvestigationControllerTest {

    private static final String BODY = """
            {"query": "checkout-service response time suddenly spiked"}""";

    private final InvestigationService service = mock(InvestigationService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new InvestigationController(service))
            .build();

    @Test
    void streamsKnowledgeThenEachToolCallThenTheResult() throws Exception {
        KnowledgeSnippet snippet = new KnowledgeSnippet("connection-pool-exhaustion.md", "…");
        ToolCall metrics = new ToolCall("getMetrics", "service=checkout-service", "p99 3.4s", 3);
        ToolCall logs = new ToolCall("searchLogs", "service=checkout-service", "HikariPool", 1);
        Report report = new Report("Pool exhaustion", 0.9, List.of(), List.of(), List.of());

        when(service.investigate(any(), any())).thenAnswer(invocation -> {
            InvestigationListener listener = invocation.getArgument(1);
            listener.onKnowledge(List.of(snippet));
            listener.onToolCall(metrics);
            listener.onToolCall(logs);
            return new InvestigationResult(report, List.of(metrics, logs), List.of(snippet));
        });

        String body = streamBody();

        assertThat(body).containsSubsequence(
                "event:knowledge", "connection-pool-exhaustion.md",
                "event:tool-call", "getMetrics",
                "event:tool-call", "searchLogs",
                "event:result", "Pool exhaustion");
    }

    @Test
    void reportsAFailureAsAnErrorEvent() throws Exception {
        when(service.investigate(any(), any()))
                .thenThrow(new IllegalStateException("model unavailable"));

        assertThat(streamBody())
                .contains("event:error")
                .contains("model unavailable")
                .doesNotContain("event:result");
    }

    @Test
    void rejectsABlankQueryBeforeStreaming() throws Exception {
        mvc.perform(post("/api/investigate/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    private String streamBody() throws Exception {
        MvcResult started = mvc.perform(post("/api/investigate/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
