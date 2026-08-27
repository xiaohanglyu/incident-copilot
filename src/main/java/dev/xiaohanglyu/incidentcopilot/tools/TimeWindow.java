package dev.xiaohanglyu.incidentcopilot.tools;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/**
 * Parses the optional ISO 8601 window arguments the model passes to the tools.
 *
 * <p>A value it cannot parse becomes {@code null} — an unbounded window — rather than an
 * error. A malformed timestamp should cost breadth, not the whole investigation.
 */
@Component
class TimeWindow {

    Instant parse(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso.strip()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(iso.strip());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /** Renders the tool arguments for the investigation trail. */
    String describe(String base, String fromTime, String toTime) {
        StringBuilder text = new StringBuilder(base);
        if (fromTime != null && !fromTime.isBlank()) {
            text.append(", from=").append(fromTime.strip());
        }
        if (toTime != null && !toTime.isBlank()) {
            text.append(", to=").append(toTime.strip());
        }
        return text.toString();
    }
}
