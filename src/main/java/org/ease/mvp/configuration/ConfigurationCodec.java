package org.ease.mvp.configuration;

import org.ease.mvp.configuration.DeploymentConfiguration.ContestationPolicy;
import org.ease.mvp.configuration.DeploymentConfiguration.EvidenceDefaults;
import org.ease.mvp.configuration.DeploymentConfiguration.PlanDefinition;
import org.ease.mvp.configuration.DeploymentConfiguration.PlanKind;
import org.ease.mvp.configuration.DeploymentConfiguration.SustainabilityEvaluator;
import org.ease.mvp.configuration.DeploymentConfiguration.Weights;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.ConstraintKind;
import org.ease.mvp.domain.EthicalConstraint;
import org.ease.mvp.domain.Stakeholder;
import org.ease.mvp.domain.ThresholdPolicy;
import org.ease.mvp.support.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigurationCodec {
    private ConfigurationCodec() {
    }

    public static DeploymentConfiguration parse(String json) {
        try {
            return fromMap(object(Json.parse(json), "$"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid EASE deployment configuration: " + exception.getMessage());
        }
    }

    public static String write(DeploymentConfiguration configuration) {
        return Json.stringify(configuration.toMap());
    }

    public static DeploymentConfiguration fromMap(Map<String, Object> root) {
        Map<String, Object> thresholdMap = object(required(root, "thresholds", "$"), "$.thresholds");
        ThresholdPolicy thresholds = new ThresholdPolicy(
                decimal(thresholdMap, "tauA", "$.thresholds"),
                decimal(thresholdMap, "tauAS", "$.thresholds"),
                decimal(thresholdMap, "tauD", "$.thresholds"),
                decimal(thresholdMap, "tauR", "$.thresholds"),
                decimal(thresholdMap, "qMin", "$.thresholds"),
                string(thresholdMap, "version", "$.thresholds"),
                string(thresholdMap, "owner", "$.thresholds"),
                string(thresholdMap, "calibrationStatus", "$.thresholds")
        );

        Map<String, Object> weightMap = object(required(root, "weights", "$"), "$.weights");
        Weights weights = new Weights(
                decimal(weightMap, "sustainability", "$.weights"),
                decimal(weightMap, "autonomy", "$.weights")
        );

        Map<String, Object> evaluatorMap = object(required(root, "evaluator", "$"), "$.evaluator");
        SustainabilityEvaluator evaluator = new SustainabilityEvaluator(
                decimal(evaluatorMap, "targetWasteRatio", "$.evaluator"),
                decimal(evaluatorMap, "fullDeviationWasteRatio", "$.evaluator"),
                string(evaluatorMap, "version", "$.evaluator")
        );

        Map<String, Object> evidenceMap =
                object(required(root, "defaultEvidence", "$"), "$.defaultEvidence");
        EvidenceDefaults defaultEvidence = new EvidenceDefaults(
                integer(evidenceMap, "purchasedPerishables", "$.defaultEvidence"),
                integer(evidenceMap, "discardedPerishables", "$.defaultEvidence"),
                integer(evidenceMap, "advisorySuggestions", "$.defaultEvidence"),
                integer(evidenceMap, "ignoredSuggestions", "$.defaultEvidence"),
                decimal(evidenceMap, "sustainabilityConfidence", "$.defaultEvidence"),
                bool(evidenceMap, "externalDisclosureAttempt", "$.defaultEvidence"),
                bool(evidenceMap, "externalDisclosureConsent", "$.defaultEvidence"),
                bool(evidenceMap, "automaticListChangeConsent", "$.defaultEvidence"),
                string(evidenceMap, "provenance", "$.defaultEvidence")
        );

        List<Stakeholder> stakeholders = new ArrayList<>();
        List<?> stakeholderValues = array(required(root, "stakeholders", "$"), "$.stakeholders");
        for (int index = 0; index < stakeholderValues.size(); index++) {
            String path = "$.stakeholders[" + index + "]";
            Map<String, Object> item = object(stakeholderValues.get(index), path);
            stakeholders.add(new Stakeholder(
                    string(item, "id", path),
                    string(item, "role", path),
                    string(item, "authority", path),
                    strings(item, "ethicalConcerns", path)
            ));
        }

        List<EthicalConstraint> constraints = new ArrayList<>();
        List<?> constraintValues = array(required(root, "constraints", "$"), "$.constraints");
        for (int index = 0; index < constraintValues.size(); index++) {
            String path = "$.constraints[" + index + "]";
            Map<String, Object> item = object(constraintValues.get(index), path);
            String constraintId = string(item, "id", path);
            double configuredWeight = switch (constraintId) {
                case "Cprivacy" -> 0;
                case "Csustainability" -> weights.sustainability();
                case "Cautonomy" -> weights.autonomy();
                default -> decimal(item, "weight", path);
            };
            constraints.add(new EthicalConstraint(
                    constraintId,
                    string(item, "principle", path),
                    enumeration(ConstraintKind.class, item, "kind", path),
                    string(item, "stakeholderId", path),
                    string(item, "authority", path),
                    string(item, "target", path),
                    string(item, "applicability", path),
                    integer(item, "precedence", path),
                    configuredWeight,
                    bool(item, "contestable", path),
                    string(item, "violationHandling", path),
                    string(item, "revisionPolicy", path),
                    string(item, "version", path)
            ));
        }

        List<PlanDefinition> plans = new ArrayList<>();
        List<?> planValues = array(required(root, "plans", "$"), "$.plans");
        for (int index = 0; index < planValues.size(); index++) {
            String path = "$.plans[" + index + "]";
            Map<String, Object> item = object(planValues.get(index), path);
            plans.add(new PlanDefinition(
                    string(item, "id", path),
                    string(item, "name", path),
                    string(item, "description", path),
                    enumeration(PlanKind.class, item, "kind", path),
                    enumeration(AutonomyMode.class, item, "mode", path),
                    decimal(item, "referenceMismatch", path),
                    decimal(item, "residualMismatchAtReference", path),
                    decimal(item, "predictedConfidence", path),
                    bool(item, "requiresConfirmation", path),
                    bool(item, "reversible", path),
                    decimal(item, "operationalCost", path),
                    decimal(item, "stakeholderBurden", path),
                    bool(item, "requiresAutomaticListConsent", path)
            ));
        }

        Map<String, Object> contestationMap =
                object(required(root, "contestationPolicy", "$"), "$.contestationPolicy");
        ContestationPolicy contestationPolicy = new ContestationPolicy(
                string(contestationMap, "version", "$.contestationPolicy"),
                string(contestationMap, "authorisedStakeholder", "$.contestationPolicy"),
                bool(contestationMap, "allowDiscardedCorrection", "$.contestationPolicy"),
                bool(contestationMap, "allowConfidenceCorrection", "$.contestationPolicy"),
                bool(
                        contestationMap,
                        "allowExternalDisclosureConsentCorrection",
                        "$.contestationPolicy"
                ),
                bool(
                        contestationMap,
                        "allowAutomaticListConsentCorrection",
                        "$.contestationPolicy"
                )
        );

        Map<String, Object> bdiMap = object(required(root, "bdi", "$"), "$.bdi");
        return new DeploymentConfiguration(
                string(root, "schemaVersion", "$"),
                string(root, "id", "$"),
                string(root, "name", "$"),
                string(root, "description", "$"),
                string(root, "context", "$"),
                thresholds,
                weights,
                evaluator,
                defaultEvidence,
                stakeholders,
                constraints,
                plans,
                contestationPolicy,
                strings(bdiMap, "baselineDesires", "$.bdi"),
                string(bdiMap, "hardViolationDesire", "$.bdi")
        );
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(path + " must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public static List<?> array(Object value, String path) {
        if (!(value instanceof List<?> result)) {
            throw new IllegalArgumentException(path + " must be a JSON array");
        }
        return result;
    }

    public static String string(Map<String, Object> map, String key, String path) {
        Object value = required(map, key, path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(path + "." + key + " must be a non-empty string");
        }
        return text;
    }

    public static double decimal(Map<String, Object> map, String key, String path) {
        Object value = required(map, key, path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + "." + key + " must be a finite number");
        }
        return number.doubleValue();
    }

    public static int integer(Map<String, Object> map, String key, String path) {
        Object value = required(map, key, path);
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException(path + "." + key + " must be an integer");
        }
        return number.intValue();
    }

    public static boolean bool(Map<String, Object> map, String key, String path) {
        Object value = required(map, key, path);
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(path + "." + key + " must be true or false");
        }
        return result;
    }

    public static List<String> strings(Map<String, Object> map, String key, String path) {
        List<?> values = array(required(map, key, path), path + "." + key);
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(
                        path + "." + key + "[" + index + "] must be a non-empty string"
                );
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    public static Object required(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException(path + "." + key + " is required");
        }
        return map.get(key);
    }

    private static <T extends Enum<T>> T enumeration(
            Class<T> type,
            Map<String, Object> map,
            String key,
            String path
    ) {
        String value = string(map, key, path);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    path + "." + key + " has unsupported value '" + value + "'"
            );
        }
    }
}
