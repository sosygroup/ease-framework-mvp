package org.ease.mvp.mapek.analyse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MismatchIndicator(
        String constraintId,
        double deviation,
        double severity,
        double mismatch,
        double confidence,
        boolean hardViolation,
        List<String> evidenceLinks,
        String evaluatorVersion
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("constraintId", constraintId);
        map.put("deviation", deviation);
        map.put("severity", severity);
        map.put("mismatch", mismatch);
        map.put("confidence", confidence);
        map.put("hardViolation", hardViolation);
        map.put("evidenceLinks", evidenceLinks);
        map.put("evaluatorVersion", evaluatorVersion);
        return map;
    }
}
