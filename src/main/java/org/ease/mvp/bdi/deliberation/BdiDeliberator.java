package org.ease.mvp.bdi.deliberation;

import org.ease.mvp.bdi.desire.DesireSet;
import org.ease.mvp.bdi.intention.CandidateIntention;
import org.ease.mvp.bdi.intention.HomeHubPlanLibrary;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.domain.ThresholdPolicy;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.mapek.analyse.GovernanceState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BdiDeliberator {
    private final ThresholdPolicy thresholds;
    private final HomeHubPlanLibrary planLibrary;

    public BdiDeliberator(ThresholdPolicy thresholds, HomeHubPlanLibrary planLibrary) {
        this.thresholds = thresholds;
        this.planLibrary = planLibrary;
    }

    public DeliberationResult deliberate(
            Evidence evidence,
            GovernanceState governance,
            DesireSet desires,
            AutonomyMode currentMode,
            LlmCognitiveUpdate cognitiveUpdate
    ) {
        if (governance.humanReviewRequired()) {
            return humanReview(governance, desires);
        }
        if (!governance.hardViolations().isEmpty()) {
            return hardConstraint(governance, desires);
        }
        if (governance.aggregateMismatch() < thresholds.assistive()) {
            return retainOrRollback(governance, desires, currentMode);
        }
        return selectSoftConstraintPlan(evidence, governance, desires, cognitiveUpdate);
    }

    private DeliberationResult humanReview(
            GovernanceState governance,
            DesireSet desires
    ) {
        CandidateIntention hold = planLibrary.safeStatePlan(
                "I-review",
                governance.aggregateMismatch(),
                governance.aggregateConfidence(),
                desires,
                "SELECTED_SAFE_STATE",
                "Aggregate evidence confidence is below qmin=" + thresholds.minimumConfidence()
        );
        return new DeliberationResult(
                List.of(hold), hold,
                "Automatic redistribution is suspended because confidence is insufficient; control remains with an authorised human.",
                "HUMAN_REVIEW_REQUIRED"
        );
    }

    private DeliberationResult hardConstraint(GovernanceState governance, DesireSet desires) {
        List<CandidateIntention> candidates = planLibrary.hardConstraintPlans(governance, desires);
        CandidateIntention selected = candidates.stream()
                .filter(candidate -> "I5".equals(candidate.id()))
                .findFirst()
                .orElseThrow();
        return new DeliberationResult(
                candidates, selected,
                "Cprivacy requires blocking the unauthorised disclosure; the hard constraint displaces the default intrusion ordering.",
                "EXECUTED"
        );
    }

    private DeliberationResult retainOrRollback(
            GovernanceState governance,
            DesireSet desires,
            AutonomyMode currentMode
    ) {
        boolean rollbackRequired = currentMode != AutonomyMode.ADVISORY;
        CandidateIntention retain = planLibrary.safeStatePlan(
                "I0",
                governance.aggregateMismatch(),
                governance.aggregateConfidence(),
                desires,
                "SELECTED",
                rollbackRequired
                        ? "Corrected evidence places the mismatch below tauAS; the stronger mode is no longer warranted"
                        : "Mismatch is below tauAS; no autonomy redistribution is warranted"
        );
        return new DeliberationResult(
                List.of(retain), retain,
                rollbackRequired
                        ? "Advisory is again sufficient after revision; the previous autonomy shift is rolled back."
                        : "Advisory is the least intrusive sufficient mode because M(t) is below tauAS.",
                rollbackRequired ? "EXECUTED" : "MONITORING"
        );
    }

    private DeliberationResult selectSoftConstraintPlan(
            Evidence evidence,
            GovernanceState governance,
            DesireSet desires,
            LlmCognitiveUpdate cognitiveUpdate
    ) {
        List<CandidateIntention> evaluated = new ArrayList<>();
        for (CandidateIntention candidate : planLibrary.softConstraintPlans(governance, desires, cognitiveUpdate)) {
            if (candidate.requiresAutomaticListConsent()
                    && !evidence.automaticListChangeConsent()) {
                evaluated.add(candidate.withStatus(
                        "REJECTED_INADMISSIBLE",
                        "Conflicts with Cautonomy: standing consent for automatic list changes is absent"
                ));
            } else if (candidate.mode().intrusionRank()
                    > governance.maximumAutomaticallyAdmissibleMode().intrusionRank()) {
                evaluated.add(candidate.withStatus(
                        "REJECTED_OUTSIDE_GOVERNANCE_REGION",
                        candidate.mode().name() + " exceeds the mode authorised by threshold policy " + thresholds.version()
                ));
            } else if (candidate.predictedConfidence() < thresholds.minimumConfidence()) {
                evaluated.add(candidate.withStatus(
                        "REJECTED_INSUFFICIENT_PREDICTION_CONFIDENCE",
                        "Predicted outcome confidence is below qmin=" + thresholds.minimumConfidence()
                ));
            } else if (candidate.predictedResidualMismatch() >= thresholds.assistive()) {
                evaluated.add(candidate.withStatus(
                        "REJECTED_INSUFFICIENT",
                        "Predicted residual mismatch does not fall below tauAS=" + thresholds.assistive()
                ));
            } else {
                evaluated.add(candidate.withStatus(
                        "ELIGIBLE_SUFFICIENT",
                        "Admissible and predicted to meet the governance objective"
                ));
            }
        }

        CandidateIntention selected = evaluated.stream()
                .filter(candidate -> "ELIGIBLE_SUFFICIENT".equals(candidate.status()))
                .min(Comparator
                        .comparingInt((CandidateIntention candidate) -> candidate.mode().intrusionRank())
                        .thenComparingDouble(CandidateIntention::predictedResidualMismatch)
                        .thenComparingDouble(CandidateIntention::stakeholderBurden))
                .orElse(null);

        if (selected == null) {
            return new DeliberationResult(
                    evaluated, null,
                    "No candidate is both admissible and sufficient; retain the safe mode and request governance-owner review.",
                    "HUMAN_REVIEW_REQUIRED"
            );
        }

        CandidateIntention selectedCandidate = selected;
        List<CandidateIntention> finalCandidates = evaluated.stream()
                .map(candidate -> candidate.id().equals(selectedCandidate.id())
                        ? candidate.withStatus("SELECTED", "Lowest-intrusion admissible and sufficient candidate")
                        : candidate)
                .toList();
        CandidateIntention finalSelected = finalCandidates.stream()
                .filter(candidate -> candidate.id().equals(selectedCandidate.id()))
                .findFirst()
                .orElseThrow();
        String rationale = finalSelected.id() + " is the lowest admissible autonomy mode predicted to reduce "
                + "the mismatch below tauAS while respecting hard constraints and confirmation authority.";
        return new DeliberationResult(
                finalCandidates,
                finalSelected,
                rationale,
                finalSelected.requiresConfirmation() ? "PENDING_CONFIRMATION" : "EXECUTED"
        );
    }
}
