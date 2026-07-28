package org.ease.mvp.scenario;

import org.ease.mvp.configuration.ConfigurationCodec;
import org.ease.mvp.support.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScenarioBatchCodec {
    private ScenarioBatchCodec() {
    }

    public static ScenarioBatchDefinition parse(String json) {
        try {
            Map<String, Object> root = ConfigurationCodec.object(Json.parse(json), "$");
            Map<String, Object> configurations = ConfigurationCodec.object(
                    ConfigurationCodec.required(root, "configurations", "$"),
                    "$.configurations"
            );
            List<?> scenarioValues = ConfigurationCodec.array(
                    ConfigurationCodec.required(root, "scenarios", "$"),
                    "$.scenarios"
            );
            List<Map<String, Object>> scenarios = new ArrayList<>();
            for (int index = 0; index < scenarioValues.size(); index++) {
                scenarios.add(ConfigurationCodec.object(
                        scenarioValues.get(index),
                        "$.scenarios[" + index + "]"
                ));
            }
            return new ScenarioBatchDefinition(
                    ConfigurationCodec.string(root, "schemaVersion", "$"),
                    ConfigurationCodec.string(root, "batchId", "$"),
                    ConfigurationCodec.string(root, "name", "$"),
                    new LinkedHashMap<>(configurations),
                    scenarios
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid EASE scenario batch: " + exception.getMessage()
            );
        }
    }

    public static String write(ScenarioBatchDefinition batch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", batch.schemaVersion());
        map.put("batchId", batch.batchId());
        map.put("name", batch.name());
        map.put("configurations", batch.configurations());
        map.put("scenarios", batch.scenarios());
        return Json.stringify(map);
    }
}
