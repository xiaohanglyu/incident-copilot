package dev.xiaohanglyu.incidentcopilot.investigation;

import dev.xiaohanglyu.incidentcopilot.knowledge.KnowledgeSnippet;
import dev.xiaohanglyu.incidentcopilot.tools.ToolCall;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/investigate")
public class InvestigationController {

    private final InvestigationService investigationService;

    public InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    /** Model calls take tens of seconds; the default async timeout would cut them off. */
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    /** Each investigation mostly waits on the model, which is what virtual threads are for. */
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping
    public InvestigationResult investigate(@Valid @RequestBody InvestigationRequest request) {
        return investigationService.investigate(request);
    }

    /**
     * The same investigation as Server-Sent Events, so the page can show the trail while
     * the agent is still working. Events, in order: {@code knowledge} once, {@code tool-call}
     * per tool, then {@code result} (the full {@link InvestigationResult}) or {@code error}.
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody InvestigationRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        executor.execute(() -> {
            try {
                InvestigationResult result = investigationService.investigate(request,
                        new InvestigationListener() {
                            @Override
                            public void onKnowledge(List<KnowledgeSnippet> snippets) {
                                send(emitter, "knowledge", snippets);
                            }

                            @Override
                            public void onToolCall(ToolCall call) {
                                send(emitter, "tool-call", call);
                            }
                        });
                send(emitter, "result", result);
                emitter.complete();
            } catch (UncheckedIOException clientGone) {
                // The page closed or navigated away; a send failing is how we find out.
                emitter.completeWithError(clientGone);
            } catch (RuntimeException failure) {
                // Headers are already sent, so the failure has to travel as an event.
                sendQuietly(emitter, "error", Map.of("message", String.valueOf(failure.getMessage())));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * A failed send means nobody is listening. Throwing stops the investigation there
     * instead of paying for model calls whose answer will be discarded.
     */
    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendQuietly(SseEmitter emitter, String name, Object data) {
        try {
            send(emitter, name, data);
        } catch (UncheckedIOException ignored) {
            // Nobody left to tell.
        }
    }

    /**
     * Only {@code query} is required. The rest is what a ticket usually carries anyway —
     * supplied as context, never as a filter, so the agent can still look somewhere the
     * reporter did not think to mention.
     */
    public record InvestigationRequest(
            @NotBlank String query,
            /** When the symptom was noticed. Not when the cause happened. */
            Instant since,
            Instant until,
            /** Services the reporter mentions. They may all be victims. */
            List<String> services
    ) {
    }
}
