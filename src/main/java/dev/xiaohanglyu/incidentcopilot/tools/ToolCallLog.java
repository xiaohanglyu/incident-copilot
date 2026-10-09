package dev.xiaohanglyu.incidentcopilot.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Ground truth for what the agent did during one investigation. The tools write here as
 * they run; the model's own account of its steps is not consulted.
 *
 * <p>Each investigation runs inside its own {@link Session}, bound with a
 * {@link ScopedValue} rather than the HTTP request. A streaming investigation runs off the
 * request thread, and Spring AI invokes tools on the thread that called the model, so the
 * binding follows the work wherever it runs.
 */
@Component
public class ToolCallLog {

    private static final ScopedValue<Session> CURRENT = ScopedValue.newInstance();

    /**
     * Runs {@code work} as one investigation. {@code onCall} sees each tool call the moment
     * it finishes, which is what lets the streaming endpoint push the trail live.
     */
    public <T> Recorded<T> record(Consumer<ToolCall> onCall, Supplier<T> work) {
        Session session = new Session(onCall);
        T value = ScopedValue.where(CURRENT, session).call(work::get);
        return new Recorded<>(value, session.calls());
    }

    void record(String tool, String arguments, String result, long millis) {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException(
                    "Tool " + tool + " ran outside an investigation; nothing would record it");
        }
        CURRENT.get().add(new ToolCall(tool, arguments, result, millis));
    }

    /** What the work returned, plus every tool call made while it ran, in order. */
    public record Recorded<T>(T value, List<ToolCall> calls) {
    }

    private static final class Session {

        private final List<ToolCall> calls = Collections.synchronizedList(new ArrayList<>());
        private final Consumer<ToolCall> onCall;

        private Session(Consumer<ToolCall> onCall) {
            this.onCall = onCall;
        }

        private void add(ToolCall call) {
            calls.add(call);
            onCall.accept(call);
        }

        private List<ToolCall> calls() {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }
    }
}
