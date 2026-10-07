package dev.xiaohanglyu.incidentcopilot.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.xiaohanglyu.incidentcopilot.shared.config.CopilotProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ToolCallLogTest {

    private final ToolCallLog log = new ToolCallLog();

    @Test
    void recordsCallsMadeWhileTheWorkRuns() {
        ToolCallLog.Recorded<String> recorded = log.record(call -> { }, () -> {
            log.record("getMetrics", "service=a", "ok", 3);
            log.record("searchLogs", "service=a", "ok", 1);
            return "report";
        });

        assertThat(recorded.value()).isEqualTo("report");
        assertThat(recorded.calls()).extracting(ToolCall::tool)
                .containsExactly("getMetrics", "searchLogs");
    }

    @Test
    void notifiesTheListenerAsEachCallFinishes() {
        List<String> seen = new ArrayList<>();

        log.record(call -> seen.add(call.tool()), () -> {
            log.record("listServices", "", "ok", 0);
            // The listener has already heard about the first call before the second starts.
            assertThat(seen).containsExactly("listServices");
            log.record("getRecentChanges", "service=a", "ok", 0);
            return null;
        });

        assertThat(seen).containsExactly("listServices", "getRecentChanges");
    }

    @Test
    void keepsConcurrentInvestigationsApart() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<ToolCallLog.Recorded<Void>> first = CompletableFuture.supplyAsync(
                    () -> log.record(call -> { }, () -> {
                        log.record("first", "", "", 0);
                        return null;
                    }), executor);
            CompletableFuture<ToolCallLog.Recorded<Void>> second = CompletableFuture.supplyAsync(
                    () -> log.record(call -> { }, () -> {
                        log.record("second", "", "", 0);
                        return null;
                    }), executor);

            assertThat(first.get().calls()).extracting(ToolCall::tool).containsExactly("first");
            assertThat(second.get().calls()).extracting(ToolCall::tool).containsExactly("second");
        }
    }

    @Test
    void refusesToolCallsOutsideAnInvestigation() {
        assertThatThrownBy(() -> log.record("getMetrics", "", "", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getMetrics");
    }

    @Test
    void realToolsRecordThemselves() {
        CopilotProperties properties = new CopilotProperties(
                null, 5, null, Duration.ofHours(1), ZoneId.of("Asia/Shanghai"));
        ServiceCatalogTool catalog = new ServiceCatalogTool(new FixtureLoader(properties), log);

        ToolCallLog.Recorded<String> recorded = log.record(call -> { }, catalog::listServices);

        assertThat(recorded.value()).contains("checkout-service", "order-consumer");
        assertThat(recorded.calls()).singleElement()
                .extracting(ToolCall::tool).isEqualTo("listServices");
    }
}
