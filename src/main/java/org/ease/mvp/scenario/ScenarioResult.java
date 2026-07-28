package org.ease.mvp.scenario;

import org.ease.mvp.support.Json;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ScenarioResult(
        String schemaVersion,
        String batchId,
        String scenarioId,
        String scenarioName,
        boolean referenceScenario,
        String configurationId,
        String configurationName,
        Map<String, Object> configuration,
        Map<String, Object> originalEvidence,
        Map<String, Object> correctedEvidence,
        Map<String, Object> changedParameters,
        Map<String, Object> originalMetrics,
        Map<String, Object> finalMetrics,
        String originalPlanId,
        String finalPlanId,
        String finalMode,
        String executionStatus,
        String status,
        List<String> notes,
        String error,
        String sourceTraceId,
        String finalTraceId,
        Instant executedAt
) {
    public static final String SCHEMA_VERSION = "ease-scenario-result/v1";

    public ScenarioResult {
        configuration = copy(configuration);
        originalEvidence = copy(originalEvidence);
        correctedEvidence = correctedEvidence == null ? null : copy(correctedEvidence);
        changedParameters = copy(changedParameters);
        originalMetrics = copy(originalMetrics);
        finalMetrics = copy(finalMetrics);
        notes = List.copyOf(notes);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", schemaVersion);
        map.put("batchId", batchId);
        map.put("scenarioId", scenarioId);
        map.put("scenarioName", scenarioName);
        map.put("referenceScenario", referenceScenario);
        map.put("configurationId", configurationId);
        map.put("configurationName", configurationName);
        map.put("parameters", Map.of(
                "configuration", configuration,
                "originalEvidence", originalEvidence
        ));
        map.put("originalEvidence", originalEvidence);
        map.put("correctedEvidence", correctedEvidence);
        map.put("changedParameters", changedParameters);
        map.put("originalMetrics", originalMetrics);
        map.put("finalMetrics", finalMetrics);
        map.put("originalPlanId", originalPlanId);
        map.put("finalPlanId", finalPlanId);
        map.put("finalMode", finalMode);
        map.put("executionStatus", executionStatus);
        map.put("status", status);
        map.put("notes", notes);
        map.put("error", error);
        map.put("sourceTraceId", sourceTraceId);
        map.put("finalTraceId", finalTraceId);
        map.put("executedAt", executedAt.toString());
        return map;
    }

    public String planValuesJson() {
        return Json.stringify(configuration.getOrDefault("plans", List.of()));
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
