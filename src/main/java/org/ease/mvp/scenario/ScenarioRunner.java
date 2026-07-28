package org.ease.mvp.scenario;

import org.ease.mvp.configuration.ConfigurationCodec;
import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.llm.runtime.LlmRuntime;
import org.ease.mvp.mapek.CycleResult;
import org.ease.mvp.mapek.knowledge.ContestationRequest;
import org.ease.mvp.mapek.knowledge.CorrectionType;
import org.ease.mvp.mapek.knowledge.DecisionTrace;
import org.ease.mvp.runtime.EaseEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ScenarioRunner {
    private final LlmRuntime llmRuntime;
    private final DeploymentConfiguration referenceConfiguration;

    public ScenarioRunner(LlmRuntime llmRuntime) {
        this.llmRuntime = llmRuntime;
        this.referenceConfiguration = ConfigurationFiles.loadDefault();
    }

    public ScenarioBatchResult run(
            ScenarioBatchDefinition batch,
            Consumer<ScenarioResult> progress
    ) {
        Instant started = Instant.now();
        List<ScenarioResult> results = new ArrayList<>();
        for (int index = 0; index < batch.scenarios().size(); index++) {
            ScenarioResult result = runOne(batch, batch.scenarios().get(index), index);
            results.add(result);
            if (progress != null) progress.accept(result);
        }
        int succeeded = (int) results.stream()
                .filter(result -> "SUCCESS".equals(result.status()))
                .count();
        return new ScenarioBatchResult(
                ScenarioBatchResult.SCHEMA_VERSION,
                batch.batchId(),
                batch.name(),
                started,
                Instant.now(),
                succeeded,
                results.size() - succeeded,
                results
        );
    }

    public ScenarioBatchResult run(ScenarioBatchDefinition batch) {
        return run(batch, null);
    }

    private ScenarioResult runOne(
            ScenarioBatchDefinition batch,
            Map<String, Object> raw,
            int index
    ) {
        Instant executedAt = Instant.now();
        String scenarioId = optionalString(raw, "id", "scenario-" + (index + 1));
        String scenarioName = optionalString(raw, "name", scenarioId);
        boolean reference = optionalBoolean(raw, "referenceScenario", false);
        String configurationId = optionalString(raw, "configurationId", "");
        DeploymentConfiguration configuration = null;
        Map<String, Object> configurationMap = Map.of();
        Map<String, Object> originalEvidenceMap = Map.of();
        try {
            if (configurationId.isBlank()) {
                throw new IllegalArgumentException("configurationId is required");
            }
            Object specification = batch.configurations().get(configurationId);
            if (specification == null) {
                throw new IllegalArgumentException(
                        "Unknown configurationId '" + configurationId + "'"
                );
            }
            configuration = configuration(specification, configurationId);
            configurationMap = configuration.toMap();
            Evidence evidence = evidence(raw.get("evidence"), configuration.defaultEvidence());
            originalEvidenceMap = evidence.toMap();

            EaseEngine engine = new EaseEngine(configuration, llmRuntime);
            CycleResult original = engine.runCycle(evidence);
            CycleResult finalResult = original;
            ContestationRequest contestation = contestation(raw.get("contestation"), configuration);
            List<String> notes = new ArrayList<>();
            Map<String, Object> correctedEvidence = null;
            if (contestation != null) {
                finalResult = engine.contestEvidence(original.trace().id(), contestation);
                correctedEvidence = finalResult.trace().triggeringEvidence().toMap();
                notes.add("Applied " + contestation.correctionType().name()
                        + " to " + contestation.correctedValues().keySet());
            }
            DecisionTrace finalTrace = finalResult.trace();
            Map<String, Object> differences = ParameterDiff.between(
                    referenceConfiguration.toMap(),
                    configuration.toMap(),
                    referenceConfiguration.defaultEvidence().toMap(),
                    comparableEvidence(evidence),
                    correctedEvidence == null
                            ? null
                            : comparableEvidence(finalTrace.triggeringEvidence())
            );
            return new ScenarioResult(
                    ScenarioResult.SCHEMA_VERSION,
                    batch.batchId(),
                    scenarioId,
                    scenarioName,
                    reference,
                    configuration.id(),
                    configuration.name(),
                    configurationMap,
                    originalEvidenceMap,
                    correctedEvidence,
                    differences,
                    metrics(original.trace()),
                    metrics(finalTrace),
                    original.trace().selectedIntentionId(),
                    finalTrace.selectedIntentionId(),
                    finalTrace.selectedMode() == null ? null : finalTrace.selectedMode().name(),
                    finalTrace.executionStatus(),
                    "SUCCESS",
                    notes,
                    null,
                    original.trace().id(),
                    finalTrace.id(),
                    executedAt
            );
        } catch (Exception exception) {
            String configurationName = configuration == null ? "" : configuration.name();
            return new ScenarioResult(
                    ScenarioResult.SCHEMA_VERSION,
                    batch.batchId(),
                    scenarioId,
                    scenarioName,
                    reference,
                    configuration == null ? configurationId : configuration.id(),
                    configurationName,
                    configurationMap,
                    originalEvidenceMap,
                    null,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    null,
                    null,
                    null,
                    "NOT_EXECUTED",
                    "ERROR",
                    List.of(),
                    safeMessage(exception),
                    null,
                    null,
                    executedAt
            );
        }
    }

    private DeploymentConfiguration configuration(Object value, String id) {
        if (value instanceof String reference) {
            return ConfigurationFiles.loadBundled(reference);
        }
        return ConfigurationCodec.fromMap(
                ConfigurationCodec.object(value, "$.configurations." + id)
        );
    }

    private Evidence evidence(
            Object value,
            DeploymentConfiguration.EvidenceDefaults defaults
    ) {
        if (value == null) return defaults.toEvidence();
        Map<String, Object> map = ConfigurationCodec.object(value, "$.scenarios[].evidence");
        return new Evidence(
                optionalInteger(map, "purchasedPerishables", defaults.purchasedPerishables()),
                optionalInteger(map, "discardedPerishables", defaults.discardedPerishables()),
                optionalInteger(map, "advisorySuggestions", defaults.advisorySuggestions()),
                optionalInteger(map, "ignoredSuggestions", defaults.ignoredSuggestions()),
                optionalDecimal(
                        map,
                        "sustainabilityConfidence",
                        defaults.sustainabilityConfidence()
                ),
                optionalBoolean(
                        map,
                        "externalDisclosureAttempt",
                        defaults.externalDisclosureAttempt()
                ),
                optionalBoolean(
                        map,
                        "externalDisclosureConsent",
                        defaults.externalDisclosureConsent()
                ),
                optionalBoolean(
                        map,
                        "automaticListChangeConsent",
                        defaults.automaticListChangeConsent()
                ),
                Instant.now(),
                optionalString(map, "provenance", defaults.provenance())
        );
    }

    private ContestationRequest contestation(
            Object value,
            DeploymentConfiguration configuration
    ) {
        if (value == null) return null;
        Map<String, Object> map =
                ConfigurationCodec.object(value, "$.scenarios[].contestation");
        return new ContestationRequest(
                optionalString(
                        map,
                        "stakeholderId",
                        configuration.contestationPolicy().authorisedStakeholder()
                ),
                CorrectionType.parse(optionalString(
                        map,
                        "correctionType",
                        CorrectionType.HISTORICAL_FACT_CORRECTION.name()
                )),
                nullableInteger(map, "correctedDiscardedPerishables"),
                nullableDecimal(map, "correctedConfidence"),
                nullableBoolean(map, "correctedExternalDisclosureConsent"),
                nullableBoolean(map, "correctedAutomaticListChangeConsent"),
                optionalString(map, "reason", "Scenario-defined correction")
        );
    }

    private Map<String, Object> metrics(DecisionTrace trace) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("aggregateMismatch", trace.governance().aggregateMismatch());
        map.put("aggregateConfidence", trace.governance().aggregateConfidence());
        map.put("governanceRegion", trace.governance().region());
        map.put("hardViolations", trace.governance().hardViolations());
        map.put("humanReviewRequired", trace.governance().humanReviewRequired());
        map.put("selectedPlanId", trace.selectedIntentionId());
        map.put("selectedMode", trace.selectedMode() == null ? null : trace.selectedMode().name());
        map.put("executionStatus", trace.executionStatus());
        map.put(
                "constraintMetrics",
                trace.mismatchIndicators().stream().map(item -> item.toMap()).toList()
        );
        return map;
    }

    private Map<String, Object> comparableEvidence(Evidence evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("purchasedPerishables", evidence.purchasedPerishables());
        map.put("discardedPerishables", evidence.discardedPerishables());
        map.put("advisorySuggestions", evidence.advisorySuggestions());
        map.put("ignoredSuggestions", evidence.ignoredSuggestions());
        map.put("sustainabilityConfidence", evidence.sustainabilityConfidence());
        map.put("externalDisclosureAttempt", evidence.externalDisclosureAttempt());
        map.put("externalDisclosureConsent", evidence.externalDisclosureConsent());
        map.put("automaticListChangeConsent", evidence.automaticListChangeConsent());
        map.put("provenance", evidence.provenance());
        return map;
    }

    private String optionalString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-empty string");
        }
        return text;
    }

    private int optionalInteger(Map<String, Object> map, String key, int fallback) {
        return map.containsKey(key) ? requiredInteger(map.get(key), key) : fallback;
    }

    private Integer nullableInteger(Map<String, Object> map, String key) {
        return !map.containsKey(key) || map.get(key) == null
                ? null
                : requiredInteger(map.get(key), key);
    }

    private int requiredInteger(Object value, String key) {
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return number.intValue();
    }

    private double optionalDecimal(Map<String, Object> map, String key, double fallback) {
        return map.containsKey(key) ? requiredDecimal(map.get(key), key) : fallback;
    }

    private Double nullableDecimal(Map<String, Object> map, String key) {
        return !map.containsKey(key) || map.get(key) == null
                ? null
                : requiredDecimal(map.get(key), key);
    }

    private double requiredDecimal(Object value, String key) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be a finite number");
        }
        return number.doubleValue();
    }

    private boolean optionalBoolean(Map<String, Object> map, String key, boolean fallback) {
        return map.containsKey(key) ? requiredBoolean(map.get(key), key) : fallback;
    }

    private Boolean nullableBoolean(Map<String, Object> map, String key) {
        return !map.containsKey(key) || map.get(key) == null
                ? null
                : requiredBoolean(map.get(key), key);
    }

    private boolean requiredBoolean(Object value, String key) {
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return result;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        String safe = message.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        return safe.length() <= 1_000 ? safe : safe.substring(0, 1_000);
    }
}
