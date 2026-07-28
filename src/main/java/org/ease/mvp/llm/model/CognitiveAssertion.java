package org.ease.mvp.llm.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record CognitiveAssertion(
        String id,
        String statement,
        double confidence,
        String provenance,
        boolean contestable
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("statement", statement);
        map.put("confidence", confidence);
        map.put("provenance", provenance);
        map.put("contestable", contestable);
        return map;
    }

    public String traceStatement() {
        return id + ": " + statement + " [q=" + String.format("%.3f", confidence)
                + "; source=" + provenance + "; contestable=" + contestable + "]";
    }
}
