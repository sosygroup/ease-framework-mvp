package org.ease.mvp.mapek.knowledge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record Contestation(
        String id,
        String sourceTraceId,
        String stakeholderId,
        CorrectionType correctionType,
        String reason,
        Map<String, Object> originalValues,
        Map<String, Object> correctedValues,
        String outcome,
        Instant submittedAt
) {
    public Contestation {
        originalValues = Map.copyOf(originalValues);
        correctedValues = Map.copyOf(correctedValues);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("sourceTraceId", sourceTraceId);
        map.put("stakeholderId", stakeholderId);
        map.put("correctionType", correctionType.name());
        map.put("reason", reason);
        map.put("originalValues", originalValues);
        map.put("correctedValues", correctedValues);
        map.put("changedFields", correctedValues.keySet());
        map.put("outcome", outcome);
        map.put("submittedAt", submittedAt.toString());
        map.put(
                "temporalEffect",
                correctionType == CorrectionType.HISTORICAL_FACT_CORRECTION
                        ? "RECOMPUTES_SOURCE_EVENT_WITHOUT_OVERWRITING_IT"
                        : "APPLIES_TO_CURRENT_AND_FUTURE_CYCLES_ONLY"
        );
        return map;
    }
}
