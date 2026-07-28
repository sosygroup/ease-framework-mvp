package org.ease.mvp.bdi.deliberation;

import org.ease.mvp.bdi.intention.CandidateIntention;

import java.util.List;

public record DeliberationResult(
        List<CandidateIntention> candidates,
        CandidateIntention selected,
        String rationale,
        String executionStatus
) {
    public DeliberationResult {
        candidates = List.copyOf(candidates);
    }
}
