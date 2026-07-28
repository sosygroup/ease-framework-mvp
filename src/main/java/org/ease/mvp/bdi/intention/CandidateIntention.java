package org.ease.mvp.bdi.intention;

import org.ease.mvp.domain.AutonomyMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CandidateIntention(
        String id,
        String plan,
        AutonomyMode mode,
        double predictedResidualMismatch,
        double predictedConfidence,
        boolean requiresConfirmation,
        boolean requiresAutomaticListConsent,
        boolean reversible,
        double operationalCost,
        double stakeholderBurden,
        List<String> supportsDesires,
        String status,
        String rationale,
        String predictionSource,
        String predictionRationale,
        String sourceUpdateId
) {
    public CandidateIntention(
            String id,
            String plan,
            AutonomyMode mode,
            double predictedResidualMismatch,
            double predictedConfidence,
            boolean requiresConfirmation,
            boolean reversible,
            double operationalCost,
            double stakeholderBurden,
            List<String> supportsDesires,
            String status,
            String rationale
    ) {
        this(id, plan, mode, predictedResidualMismatch, predictedConfidence, requiresConfirmation,
                false,
                reversible, operationalCost, stakeholderBurden, supportsDesires, status, rationale,
                "DETERMINISTIC_PLAN_LIBRARY", "Transparent scenario function", null);
    }

    public CandidateIntention withStatus(String newStatus, String newRationale) {
        return new CandidateIntention(
                id, plan, mode, predictedResidualMismatch, predictedConfidence,
                requiresConfirmation, requiresAutomaticListConsent, reversible,
                operationalCost, stakeholderBurden,
                supportsDesires, newStatus, newRationale,
                predictionSource, predictionRationale, sourceUpdateId
        );
    }

    public CandidateIntention withPrediction(
            double newResidualMismatch,
            double newConfidence,
            String newPredictionRationale,
            String updateId
    ) {
        return new CandidateIntention(
                id, plan, mode, newResidualMismatch, newConfidence,
                requiresConfirmation, requiresAutomaticListConsent, reversible,
                operationalCost, stakeholderBurden,
                supportsDesires, status, rationale,
                "LLM_GOVERNED_PROPOSAL", newPredictionRationale, updateId
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("plan", plan);
        map.put("mode", mode.name());
        map.put("intrusionRank", mode.intrusionRank());
        map.put("predictedResidualMismatch", predictedResidualMismatch);
        map.put("predictedConfidence", predictedConfidence);
        map.put("requiresConfirmation", requiresConfirmation);
        map.put("requiresAutomaticListConsent", requiresAutomaticListConsent);
        map.put("reversible", reversible);
        map.put("operationalCost", operationalCost);
        map.put("stakeholderBurden", stakeholderBurden);
        map.put("supportsDesires", supportsDesires);
        map.put("status", status);
        map.put("rationale", rationale);
        map.put("predictionSource", predictionSource);
        map.put("predictionRationale", predictionRationale);
        map.put("sourceUpdateId", sourceUpdateId);
        return map;
    }
}
