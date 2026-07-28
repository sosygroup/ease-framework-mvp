package org.ease.mvp.mapek;

import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.mapek.knowledge.DecisionTrace;

import java.util.Map;

public record CycleResult(DecisionTrace trace, long knowledgeVersion, AutonomyMode currentMode) {
    public Map<String, Object> toMap() {
        return Map.of(
                "trace", trace.toMap(),
                "knowledgeVersion", knowledgeVersion,
                "currentMode", currentMode.name()
        );
    }
}
