package org.ease.mvp.llm.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record LlmPlanAssessment(
        String templateId,
        double predictedResidualMismatch,
        double predictedConfidence,
        String rationale
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateId", templateId);
        map.put("predictedResidualMismatch", predictedResidualMismatch);
        map.put("predictedConfidence", predictedConfidence);
        map.put("rationale", rationale);
        return map;
    }
}
