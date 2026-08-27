package dev.xiaohanglyu.incidentcopilot.investigation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigate")
public class InvestigationController {

    private final InvestigationService investigationService;

    public InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @PostMapping
    public InvestigationResult investigate(@Valid @RequestBody InvestigationRequest request) {
        return investigationService.investigate(request);
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
