package dev.xiaohanglyu.incidentcopilot.shared.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Binds the {@code incident-copilot.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "incident-copilot")
public record CopilotProperties(
        String knowledgeLocation,
        int topK,
        Resource systemPrompt,
        /** How far back to widen a requested window; causes precede the reported symptom. */
        Duration windowLookback,
        /** Zone for fixture timestamps that carry no offset, such as Logback's default. */
        ZoneId defaultZone
) {
}
