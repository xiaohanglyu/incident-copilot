package dev.xiaohanglyu.incidentcopilot.tools;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Ground truth for what the agent did during one investigation. The tools write here as
 * they run; the model's own account of its steps is not consulted.
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ToolCallLog {

    private final List<ToolCall> calls = new ArrayList<>();

    void record(String tool, String arguments, String result, long millis) {
        calls.add(new ToolCall(tool, arguments, result, millis));
    }

    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }
}
