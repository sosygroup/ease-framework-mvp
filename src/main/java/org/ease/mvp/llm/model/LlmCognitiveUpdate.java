package org.ease.mvp.llm.model;

import org.ease.mvp.llm.config.LlmSettings;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LlmCognitiveUpdate(
        String id,
        Instant createdAt,
        String status,
        String endpoint,
        String protocol,
        String configuredModel,
        String responseModel,
        String requestId,
        long latencyMillis,
        String summary,
        List<CognitiveAssertion> beliefProposals,
        List<CognitiveAssertion> desireProposals,
        List<LlmPlanAssessment> planAssessments,
        List<String> knowledgeNotes,
        List<String> uncertainties,
        Map<String, Object> usage,
        String error
) {
    public LlmCognitiveUpdate {
        beliefProposals = List.copyOf(beliefProposals);
        desireProposals = List.copyOf(desireProposals);
        planAssessments = List.copyOf(planAssessments);
        knowledgeNotes = List.copyOf(knowledgeNotes);
        uncertainties = List.copyOf(uncertainties);
        usage = Collections.unmodifiableMap(new LinkedHashMap<>(usage));
    }

    public static LlmCognitiveUpdate disabled(LlmSettings settings) {
        return new LlmCognitiveUpdate(
                "llm-disabled-" + Instant.now().toEpochMilli(), Instant.now(),
                "DISABLED_LOCAL_BASELINE", settings.publicEndpoint(), settings.protocol().name(),
                settings.model(), "", "", 0,
                "The deterministic EASE belief revision and plan library remain active; no external LLM was called.",
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), null
        );
    }

    public static LlmCognitiveUpdate failed(LlmSettings settings, long latencyMillis, String error) {
        return new LlmCognitiveUpdate(
                "llm-failed-" + Instant.now().toEpochMilli(), Instant.now(),
                "FAILED_LOCAL_FALLBACK", settings.publicEndpoint(), settings.protocol().name(),
                settings.model(), "", "", latencyMillis,
                "The LLM update failed; the cycle continued through the deterministic, policy-governed fallback.",
                List.of(), List.of(), List.of(), List.of(),
                List.of("LLM-derived belief and plan updates were unavailable for this cycle"),
                Map.of(), error
        );
    }

    public static LlmCognitiveUpdate skipped(LlmSettings settings, String status, String summary) {
        Instant now = Instant.now();
        return new LlmCognitiveUpdate(
                "llm-skipped-" + now.toEpochMilli(), now,
                status, settings.publicEndpoint(), settings.protocol().name(),
                settings.model(), "", "", 0, summary,
                List.of(), List.of(), List.of(), List.of(),
                List.of("No runtime evidence was transmitted to the configured LLM endpoint"),
                Map.of(), null
        );
    }

    public boolean applied() {
        return "APPLIED_AS_GOVERNED_PROPOSAL".equals(status);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("createdAt", createdAt.toString());
        map.put("status", status);
        map.put("endpoint", endpoint);
        map.put("protocol", protocol);
        map.put("configuredModel", configuredModel);
        map.put("responseModel", responseModel);
        map.put("requestId", requestId);
        map.put("latencyMillis", latencyMillis);
        map.put("summary", summary);
        map.put("beliefProposals", beliefProposals.stream().map(CognitiveAssertion::toMap).toList());
        map.put("desireProposals", desireProposals.stream().map(CognitiveAssertion::toMap).toList());
        map.put("planAssessments", planAssessments.stream().map(LlmPlanAssessment::toMap).toList());
        map.put("knowledgeNotes", knowledgeNotes);
        map.put("uncertainties", uncertainties);
        map.put("usage", usage);
        map.put("error", error);
        map.put("governanceBoundary", "PROPOSAL_ONLY_VALIDATED_BY_DETERMINISTIC_EASE_POLICY");
        return map;
    }
}
