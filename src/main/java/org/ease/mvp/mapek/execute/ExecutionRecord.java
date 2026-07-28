package org.ease.mvp.mapek.execute;

import org.ease.mvp.domain.AutonomyMode;

import java.time.Instant;
import java.util.Map;

public record ExecutionRecord(
        String traceId,
        String intentionId,
        String status,
        AutonomyMode resultingMode,
        String detail,
        Instant occurredAt
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "traceId", traceId,
                "intentionId", intentionId,
                "status", status,
                "resultingMode", resultingMode.name(),
                "detail", detail,
                "occurredAt", occurredAt.toString()
        );
    }
}
