package org.ease.mvp.scenario;

import java.util.List;
import java.util.Map;

public record ScenarioBatchDefinition(
        String schemaVersion,
        String batchId,
        String name,
        Map<String, Object> configurations,
        List<Map<String, Object>> scenarios
) {
    public static final String SCHEMA_VERSION = "ease-scenario-batch/v1";

    public ScenarioBatchDefinition {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion must be '" + SCHEMA_VERSION + "'"
            );
        }
        if (batchId == null || !batchId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("batchId must be a non-empty safe identifier");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Batch name is required");
        }
        if (configurations == null || configurations.isEmpty()) {
            throw new IllegalArgumentException("Batch configurations must not be empty");
        }
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("Batch scenarios must not be empty");
        }
        configurations = Map.copyOf(configurations);
        scenarios = scenarios.stream().map(Map::copyOf).toList();
    }
}
