package org.ease.mvp.scenario;

import org.ease.mvp.support.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ScenarioBatchResult(
        String schemaVersion,
        String batchId,
        String batchName,
        Instant startedAt,
        Instant completedAt,
        int succeeded,
        int failed,
        List<ScenarioResult> results
) {
    public static final String SCHEMA_VERSION = "ease-scenario-batch-result/v1";

    public ScenarioBatchResult {
        results = List.copyOf(results);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", schemaVersion);
        map.put("batchId", batchId);
        map.put("batchName", batchName);
        map.put("startedAt", startedAt.toString());
        map.put("completedAt", completedAt.toString());
        map.put("succeeded", succeeded);
        map.put("failed", failed);
        map.put("results", results.stream().map(ScenarioResult::toMap).toList());
        return map;
    }

    public String toJson() {
        return Json.stringify(toMap());
    }
}
