package dev.xiaohanglyu.incidentcopilot.tools;

import dev.xiaohanglyu.incidentcopilot.shared.config.CopilotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Serves the canned tool responses from {@code resources/fixtures/<service>}.
 * Scaffolding for v1 — it disappears once the tools talk to real systems.
 *
 * <p>Two behaviours here are deliberate. An unknown service returns a plain refusal
 * rather than another service's data: a tool that answers every question with the same
 * fixture teaches the model that whatever comes back is relevant, and it will then bend
 * that data to fit the question. And a requested window is always widened backwards,
 * because the cause of an incident precedes the symptom that got reported — clipping to
 * the reported onset hides the deployment that caused it.
 */
@Component
class FixtureLoader {

    /**
     * Matches both fixture dialects: {@code 2026-08-24 14:26:03.481} (Logback, no offset)
     * and {@code 2026-08-24T00:06:44+08:00} (RFC3339). Searched anywhere in the line, not
     * anchored — in JSON logs the timestamp sits inside a field.
     */
    private static final Pattern TIMESTAMP = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final List<String> knownServices;
    private final CopilotProperties properties;

    FixtureLoader(CopilotProperties properties) {
        this.properties = properties;
        this.knownServices = discoverServices();
    }

    List<String> services() {
        return knownServices;
    }

    String load(String service, String filename) {
        return load(service, filename, null, null);
    }

    /**
     * @param from start of the window, or null for everything available. Widened backwards
     *             by {@code incident-copilot.window-lookback} before filtering.
     * @param to   end of the window, or null for open-ended.
     */
    String load(String service, String filename, Instant from, Instant to) {
        Optional<String> resolved = resolve(service);
        if (resolved.isEmpty()) {
            return "No data available for service '%s'. Known services: %s."
                    .formatted(service, String.join(", ", knownServices));
        }

        String body = read(resolved.get(), filename);
        if (from == null && to == null) {
            return body;
        }

        Instant widened = from == null ? null : from.minus(properties.windowLookback());
        String filtered = filter(body, widened, to);

        if (filtered == null) {
            return """
                    No data for %s between %s and %s.
                    Available window: %s.
                    """.formatted(resolved.get(), display(widened), display(to),
                    availableWindow(body));
        }
        return """
                Window: %s to %s (widened back by %s from the reported onset, because the
                cause of an incident precedes the symptom).

                %s""".formatted(display(widened), display(to),
                properties.windowLookback(), filtered);
    }

    /** Returns null when the window excludes every timestamped line. */
    private String filter(String body, Instant from, Instant to) {
        List<String> kept = new ArrayList<>();
        boolean seenTimestamp = false;
        boolean keepingCurrent = true;
        boolean keptAny = false;

        for (String line : body.split("\n", -1)) {
            Optional<Instant> at = timestampOf(line);
            if (at.isPresent()) {
                seenTimestamp = true;
                keepingCurrent = within(at.get(), from, to);
                if (keepingCurrent) {
                    keptAny = true;
                }
            }
            // Lines before the first timestamp are the header. Lines after one without a
            // timestamp of their own are continuations — they follow their parent line.
            if (!seenTimestamp || keepingCurrent) {
                kept.add(line);
            }
        }
        return keptAny ? String.join("\n", kept) : null;
    }

    private boolean within(Instant at, Instant from, Instant to) {
        return (from == null || !at.isBefore(from)) && (to == null || !at.isAfter(to));
    }

    private Optional<Instant> timestampOf(String line) {
        Matcher matcher = TIMESTAMP.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String date = matcher.group(1);
        String time = matcher.group(2);
        String fraction = matcher.group(3) == null ? "" : matcher.group(3);
        String offset = matcher.group(4);
        try {
            if (offset == null) {
                // No offset in the text: the service profile is what tells a reader which
                // zone this is. Here it comes from configuration.
                return Optional.of(LocalDateTime.parse(date + "T" + time + fraction)
                        .atZone(properties.defaultZone())
                        .toInstant());
            }
            return Optional.of(OffsetDateTime.parse(date + "T" + time + fraction + offset)
                    .toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private String availableWindow(String body) {
        List<Instant> stamps = new ArrayList<>();
        for (String line : body.split("\n")) {
            timestampOf(line).ifPresent(stamps::add);
        }
        if (stamps.isEmpty()) {
            return "unknown";
        }
        return display(stamps.stream().min(Instant::compareTo).orElseThrow())
                + " to " + display(stamps.stream().max(Instant::compareTo).orElseThrow());
    }

    private String display(Instant instant) {
        return instant == null
                ? "now"
                : DISPLAY.format(instant.atZone(properties.defaultZone())) + " "
                        + properties.defaultZone();
    }

    /** Lenient on purpose: the model rarely guesses the exact service name from a symptom. */
    private Optional<String> resolve(String service) {
        if (service == null || service.isBlank()) {
            return Optional.empty();
        }
        String needle = service.toLowerCase(Locale.ROOT).strip();
        return knownServices.stream()
                .filter(known -> known.equals(needle))
                .findFirst()
                .or(() -> knownServices.stream()
                        .filter(known -> known.contains(needle) || needle.contains(known))
                        .findFirst());
    }

    private String read(String service, String filename) {
        return cache.computeIfAbsent(service + "/" + filename, path -> {
            try {
                return new PathMatchingResourcePatternResolver()
                        .getResource("classpath:/fixtures/" + path)
                        .getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Missing fixture: fixtures/" + path, e);
            }
        });
    }

    private List<String> discoverServices() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:/fixtures/*/metrics.txt");

            List<String> services = new ArrayList<>();
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                services.add(path.replaceAll(".*/fixtures/([^/]+)/metrics\\.txt$", "$1"));
            }
            return services.stream().distinct().sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan fixtures", e);
        }
    }
}
