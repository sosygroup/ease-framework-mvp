package org.ease.mvp.scenario;

import org.ease.mvp.support.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScenarioCsvExporter {
    public static final String SCHEMA_VERSION = "ease-scenario-csv/v1";
    public static final List<String> COLUMNS = List.of(
            "schemaVersion",
            "batchId",
            "scenarioId",
            "scenarioName",
            "referenceScenario",
            "configurationId",
            "configurationName",
            "status",
            "executedAt",
            "originalConfidence",
            "correctedConfidence",
            "originalExternalDisclosureConsent",
            "correctedExternalDisclosureConsent",
            "originalAutomaticListChangeConsent",
            "correctedAutomaticListChangeConsent",
            "originalDiscardedPerishables",
            "correctedDiscardedPerishables",
            "aggregateMismatch",
            "aggregateConfidence",
            "governanceRegion",
            "finalPlanId",
            "finalMode",
            "executionStatus",
            "tauA",
            "tauAS",
            "tauD",
            "tauR",
            "qMin",
            "sustainabilityWeight",
            "autonomyWeight",
            "targetWasteRatio",
            "fullDeviationWasteRatio",
            "planValuesJson",
            "changedParametersJson",
            "notes",
            "error"
    );

    public String export(ScenarioBatchResult batch) {
        StringBuilder csv = new StringBuilder();
        row(csv, COLUMNS);
        for (ScenarioResult result : batch.results()) row(csv, values(result));
        return csv.toString();
    }

    private List<String> values(ScenarioResult result) {
        Map<String, Object> corrected = result.correctedEvidence();
        Map<String, Object> metrics = result.finalMetrics();
        Map<String, Object> thresholds = object(result.configuration().get("thresholds"));
        Map<String, Object> weights = object(result.configuration().get("weights"));
        Map<String, Object> evaluator = object(result.configuration().get("evaluator"));
        List<String> values = new ArrayList<>();
        values.add(SCHEMA_VERSION);
        values.add(result.batchId());
        values.add(result.scenarioId());
        values.add(result.scenarioName());
        values.add(String.valueOf(result.referenceScenario()));
        values.add(result.configurationId());
        values.add(result.configurationName());
        values.add(result.status());
        values.add(result.executedAt().toString());
        values.add(value(result.originalEvidence(), "sustainabilityConfidence"));
        values.add(value(corrected, "sustainabilityConfidence"));
        values.add(value(result.originalEvidence(), "externalDisclosureConsent"));
        values.add(value(corrected, "externalDisclosureConsent"));
        values.add(value(result.originalEvidence(), "automaticListChangeConsent"));
        values.add(value(corrected, "automaticListChangeConsent"));
        values.add(value(result.originalEvidence(), "discardedPerishables"));
        values.add(value(corrected, "discardedPerishables"));
        values.add(value(metrics, "aggregateMismatch"));
        values.add(value(metrics, "aggregateConfidence"));
        values.add(value(metrics, "governanceRegion"));
        values.add(nullSafe(result.finalPlanId()));
        values.add(nullSafe(result.finalMode()));
        values.add(nullSafe(result.executionStatus()));
        values.add(value(thresholds, "tauA"));
        values.add(value(thresholds, "tauAS"));
        values.add(value(thresholds, "tauD"));
        values.add(value(thresholds, "tauR"));
        values.add(value(thresholds, "qMin"));
        values.add(value(weights, "sustainability"));
        values.add(value(weights, "autonomy"));
        values.add(value(evaluator, "targetWasteRatio"));
        values.add(value(evaluator, "fullDeviationWasteRatio"));
        values.add(result.planValuesJson());
        values.add(Json.stringify(result.changedParameters()));
        values.add(String.join(" | ", result.notes()));
        values.add(nullSafe(result.error()));
        return values;
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String value(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return "";
        return String.valueOf(map.get(key));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void row(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) csv.append(',');
            csv.append(escape(values.get(index)));
        }
        csv.append("\r\n");
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
