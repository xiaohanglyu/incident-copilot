package dev.xiaohanglyu.incidentcopilot.investigation;

import java.util.List;

/**
 * What the model produces. There is no field here for the steps it took — those are
 * recorded server-side while the tools run, so the account cannot be embellished.
 */
public record Report(
        String mostLikelyCause,
        double confidence,
        List<Evidence> evidence,
        List<String> suggestedVerification,
        List<String> suggestedMitigation
) {
}
