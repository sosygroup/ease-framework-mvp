package org.ease.mvp.bdi.intention;

import org.ease.mvp.bdi.desire.DesireSet;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.configuration.DeploymentConfiguration.PlanDefinition;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.llm.model.LlmPlanAssessment;
import org.ease.mvp.mapek.analyse.GovernanceState;

import java.util.ArrayList;
import java.util.List;

public final class HomeHubPlanLibrary {
    private final DeploymentConfiguration configuration;

    public HomeHubPlanLibrary(DeploymentConfiguration configuration) {
        this.configuration = configuration;
    }

    public List<CandidateIntention> softConstraintPlans(
            GovernanceState governance,
            DesireSet desires
    ) {
        return configuration.softPlans().stream()
                .map(definition -> candidate(
                        definition,
                        definition.predictedResidual(governance.aggregateMismatch()),
                        desires
                ))
                .toList();
    }

    public List<CandidateIntention> softConstraintPlans(
            GovernanceState governance,
            DesireSet desires,
            LlmCognitiveUpdate cognitiveUpdate
    ) {
        List<CandidateIntention> baseline = softConstraintPlans(governance, desires);
        if (cognitiveUpdate == null || !cognitiveUpdate.applied()) return baseline;
        return baseline.stream().map(candidate -> cognitiveUpdate.planAssessments().stream()
                .filter(assessment -> assessment.templateId().equals(candidate.id()))
                .findFirst()
                .map(assessment -> governedPrediction(
                        candidate,
                        assessment,
                        governance,
                        cognitiveUpdate.id()
                ))
                .orElse(candidate)).toList();
    }

    public List<CandidateIntention> hardConstraintPlans(
            GovernanceState governance,
            DesireSet desires
    ) {
        List<CandidateIntention> candidates = new ArrayList<>();
        for (PlanDefinition definition : configuration.softPlans()) {
            candidates.add(new CandidateIntention(
                    definition.id(),
                    definition.description(),
                    definition.mode(),
                    governance.aggregateMismatch(),
                    governance.aggregateConfidence(),
                    definition.requiresConfirmation(),
                    definition.requiresAutomaticListConsent(),
                    definition.reversible(),
                    definition.operationalCost(),
                    definition.stakeholderBurden(),
                    desires.statements(),
                    "REJECTED_HARD_CONSTRAINT",
                    "Does not remediate Cprivacy; soft-constraint improvement cannot compensate",
                    "DETERMINISTIC_PLAN_LIBRARY",
                    "Hard-constraint precedence",
                    null
            ));
        }
        PlanDefinition block = configuration.plan("I5");
        candidates.add(new CandidateIntention(
                block.id(),
                block.description(),
                block.mode(),
                governance.aggregateMismatch(),
                block.predictedConfidence(),
                block.requiresConfirmation(),
                block.requiresAutomaticListConsent(),
                block.reversible(),
                block.operationalCost(),
                block.stakeholderBurden(),
                desires.statements(),
                "SELECTED",
                "Mandatory response to Cprivacy under lexicographic hard-constraint precedence",
                "DETERMINISTIC_PLAN_LIBRARY",
                "Configured privacy-block plan",
                null
        ));
        return List.copyOf(candidates);
    }

    public CandidateIntention safeStatePlan(
            String planId,
            double residualMismatch,
            double confidence,
            DesireSet desires,
            String status,
            String rationale
    ) {
        PlanDefinition definition = configuration.plan(planId);
        return new CandidateIntention(
                definition.id(),
                definition.description(),
                definition.mode(),
                residualMismatch,
                Math.min(confidence, definition.predictedConfidence()),
                definition.requiresConfirmation(),
                definition.requiresAutomaticListConsent(),
                definition.reversible(),
                definition.operationalCost(),
                definition.stakeholderBurden(),
                desires.statements(),
                status,
                rationale,
                "DETERMINISTIC_PLAN_LIBRARY",
                "Configured safe-state plan",
                null
        );
    }

    private CandidateIntention candidate(
            PlanDefinition definition,
            double residualMismatch,
            DesireSet desires
    ) {
        return new CandidateIntention(
                definition.id(),
                definition.description(),
                definition.mode(),
                residualMismatch,
                definition.predictedConfidence(),
                definition.requiresConfirmation(),
                definition.requiresAutomaticListConsent(),
                definition.reversible(),
                definition.operationalCost(),
                definition.stakeholderBurden(),
                desires.statements(),
                "GENERATED",
                "Pending admissibility and sufficiency evaluation",
                "DETERMINISTIC_PLAN_LIBRARY",
                "Configured reference-mismatch projection",
                null
        );
    }

    private CandidateIntention governedPrediction(
            CandidateIntention candidate,
            LlmPlanAssessment assessment,
            GovernanceState governance,
            String updateId
    ) {
        double confidence = Math.min(
                assessment.predictedConfidence(),
                governance.aggregateConfidence()
        );
        return candidate.withPrediction(
                assessment.predictedResidualMismatch(),
                confidence,
                assessment.rationale(),
                updateId
        );
    }
}
