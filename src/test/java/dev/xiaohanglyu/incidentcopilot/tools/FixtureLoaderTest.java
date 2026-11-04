package dev.xiaohanglyu.incidentcopilot.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.xiaohanglyu.incidentcopilot.shared.config.CopilotProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FixtureLoaderTest {

    private final FixtureLoader fixtures = new FixtureLoader(new CopilotProperties(
            null, 5, null, Duration.ofHours(1), ZoneId.of("Asia/Shanghai")));

    @Test
    void keywordKeepsOnlyMatchingEntries() {
        String result = fixtures.load("checkout-service", "logs.txt", null, null, "sqltimeout");

        assertThat(result).contains("SQLTimeoutException")
                .doesNotContain("HikariPool-1 - Connection");
    }

    @Test
    void keywordMatchesAcrossAWrappedLine() {
        // In the fixture this message is wrapped after "Connection".
        String result = fixtures.load("checkout-service", "logs.txt", null, null,
                "Connection is not available");

        assertThat(result).contains("HikariPool-1 - Connection", "is not available")
                .doesNotContain("SQLTimeoutException");
    }

    @Test
    void keywordKeepsTheHeader() {
        String result = fixtures.load("checkout-service", "logs.txt", null, null, "sqltimeout");

        assertThat(result).startsWith("Log matches for checkout-service");
    }

    @Test
    void keywordAppliesWithinTheWindow() {
        String result = fixtures.load("order-consumer", "logs.txt",
                Instant.parse("2026-08-23T16:10:00Z"), Instant.parse("2026-08-23T16:15:00Z"),
                "out of memory");

        assertThat(result).startsWith("Window:")
                .contains("2026-08-24T00:14:30+08:00")
                .doesNotContain("2026-08-24T00:22:58+08:00");
    }

    @Test
    void noMatchSaysThereIsDataAndHowToRetry() {
        String result = fixtures.load("checkout-service", "logs.txt", null, null,
                "OutOfMemoryError");

        assertThat(result).contains("No entries for checkout-service", "omit it");
    }

    @Test
    void blankKeywordFiltersNothing() {
        assertThat(fixtures.load("checkout-service", "logs.txt", null, null, " "))
                .isEqualTo(fixtures.load("checkout-service", "logs.txt"));
    }
}
