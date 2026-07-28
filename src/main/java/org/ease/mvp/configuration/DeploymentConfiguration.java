package org.ease.mvp.configuration;

import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.EthicalConstraint;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.domain.Stakeholder;
import org.ease.mvp.domain.ThresholdPolicy;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DeploymentConfiguration(
        String schemaVersion,
        String id,
        String name,
        String description,
        String context,
        ThresholdPolicy thresholds,
        Weights weights,
        SustainabilityEvaluator evaluator,
        EvidenceDefaults defaultEvidence,
        List<Stakeholder> stakeholders,
        List<EthicalConstraint> constraints,
        List<PlanDefinition> plans,
        ContestationPolicy contestationPolicy,
        List<String> baselineDesires,
        String hardViolationDesire
) {
    public static final String SCHEMA_VERSION = "ease-deployment/v1";
    private static final Set<String> REQUIRED_CONSTRAINTS =
            Set.of("Cprivacy", "Csustainability", "Cautonomy");
    private static final Set<String> REQUIRED_PLANS =
            Set.of("I0", "I1", "I2", "I3", "I4", "I5", "I-review");

    public DeploymentConfiguration {
        schemaVersion = required(schemaVersion, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion must be '" + SCHEMA_VERSION + "', found '" + schemaVersion + "'"
            );
        }
        id = identifier(id, "id");
        name = required(name, "name");
        description = required(description, "description");
        context = required(context, "context");
        if (thresholds == null) throw new IllegalArgumentException("thresholds is required");
        if (weights == null) throw new IllegalArgumentException("weights is required");
        if (evaluator == null) throw new IllegalArgumentException("evaluator is required");
        if (defaultEvidence == null) throw new IllegalArgumentException("defaultEvidence is required");
        if (contestationPolicy == null) throw new IllegalArgumentException("contestationPolicy is required");
        stakeholders = copyRequired(stakeholders, "stakeholders");
        constraints = copyRequired(constraints, "constraints");
        plans = copyRequired(plans, "plans");
        baselineDesires = copyRequired(baselineDesires, "baselineDesires");
        hardViolationDesire = required(hardViolationDesire, "hardViolationDesire");

        unique(stakeholders.stream().map(Stakeholder::id).toList(), "stakeholder id");
        unique(constraints.stream().map(EthicalConstraint::id).toList(), "constraint id");
        unique(plans.stream().map(PlanDefinition::id).toList(), "plan id");

        Set<String> constraintIds = new HashSet<>(
                constraints.stream().map(EthicalConstraint::id).toList()
        );
        if (!constraintIds.containsAll(REQUIRED_CONSTRAINTS)) {
            throw new IllegalArgumentException(
                    "constraints must define " + REQUIRED_CONSTRAINTS + "; found " + constraintIds
            );
        }
        Set<String> planIds = new HashSet<>(plans.stream().map(PlanDefinition::id).toList());
        if (!planIds.containsAll(REQUIRED_PLANS)) {
            throw new IllegalArgumentException(
                    "plans must define " + REQUIRED_PLANS + "; found " + planIds
            );
        }
        EthicalConstraint sustainabilityConstraint = constraints.stream()
                .filter(item -> item.id().equals("Csustainability"))
                .findFirst()
                .orElseThrow();
        EthicalConstraint autonomyConstraint = constraints.stream()
                .filter(item -> item.id().equals("Cautonomy"))
                .findFirst()
                .orElseThrow();
        EthicalConstraint privacyConstraint = constraints.stream()
                .filter(item -> item.id().equals("Cprivacy"))
                .findFirst()
                .orElseThrow();
        if (sustainabilityConstraint.kind().name().equals("HARD")) {
            throw new IllegalArgumentException("Csustainability must remain a soft constraint");
        }
        if (autonomyConstraint.kind().name().equals("HARD")) {
            throw new IllegalArgumentException("Cautonomy must remain a soft constraint");
        }
        if (!privacyConstraint.kind().name().equals("HARD")) {
            throw new IllegalArgumentException("Cprivacy must remain a hard constraint");
        }
    }

    public EthicalConstraint constraint(String constraintId) {
        return constraints.stream()
                .filter(item -> item.id().equals(constraintId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing configured constraint: " + constraintId
                ));
    }

    public PlanDefinition plan(String planId) {
        return plans.stream()
                .filter(item -> item.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing configured plan: " + planId));
    }

    public List<PlanDefinition> softPlans() {
        return plans.stream().filter(item -> item.kind() == PlanKind.SOFT_RESPONSE).toList();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", schemaVersion);
        map.put("id", id);
        map.put("name", name);
        map.put("description", description);
        map.put("context", context);
        map.put("thresholds", thresholds.toMap());
        map.put("weights", weights.toMap());
        map.put("evaluator", evaluator.toMap());
        map.put("defaultEvidence", defaultEvidence.toMap());
        map.put("stakeholders", stakeholders.stream().map(Stakeholder::toMap).toList());
        map.put("constraints", constraints.stream().map(EthicalConstraint::toMap).toList());
        map.put("plans", plans.stream().map(PlanDefinition::toMap).toList());
        map.put("contestationPolicy", contestationPolicy.toMap());
        map.put("bdi", Map.of(
                "baselineDesires", baselineDesires,
                "hardViolationDesire", hardViolationDesire
        ));
        return map;
    }

    public record Weights(double sustainability, double autonomy) {
        public Weights {
            unit(sustainability, "weights.sustainability");
            unit(autonomy, "weights.autonomy");
            if (sustainability + autonomy <= 0) {
                throw new IllegalArgumentException(
                        "weights.sustainability and weights.autonomy cannot both be zero"
                );
            }
        }

        public Map<String, Object> toMap() {
            return Map.of("sustainability", sustainability, "autonomy", autonomy);
        }
    }

    public record SustainabilityEvaluator(
            double targetWasteRatio,
            double fullDeviationWasteRatio,
            String version
    ) {
        public SustainabilityEvaluator {
            unit(targetWasteRatio, "evaluator.targetWasteRatio");
            unit(fullDeviationWasteRatio, "evaluator.fullDeviationWasteRatio");
            if (fullDeviationWasteRatio <= targetWasteRatio) {
                throw new IllegalArgumentException(
                        "evaluator.fullDeviationWasteRatio must be greater than evaluator.targetWasteRatio"
                );
            }
            version = required(version, "evaluator.version");
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "targetWasteRatio", targetWasteRatio,
                    "fullDeviationWasteRatio", fullDeviationWasteRatio,
                    "version", version
            );
        }
    }

    public record EvidenceDefaults(
            int purchasedPerishables,
            int discardedPerishables,
            int advisorySuggestions,
            int ignoredSuggestions,
            double sustainabilityConfidence,
            boolean externalDisclosureAttempt,
            boolean externalDisclosureConsent,
            boolean automaticListChangeConsent,
            String provenance
    ) {
        public EvidenceDefaults {
            provenance = required(provenance, "defaultEvidence.provenance");
            // Reuse the executable evidence invariants during configuration validation.
            new Evidence(
                    purchasedPerishables,
                    discardedPerishables,
                    advisorySuggestions,
                    ignoredSuggestions,
                    sustainabilityConfidence,
                    externalDisclosureAttempt,
                    externalDisclosureConsent,
                    automaticListChangeConsent,
                    Instant.now(),
                    provenance
            );
        }

        public Evidence toEvidence() {
            return new Evidence(
                    purchasedPerishables,
                    discardedPerishables,
                    advisorySuggestions,
                    ignoredSuggestions,
                    sustainabilityConfidence,
                    externalDisclosureAttempt,
                    externalDisclosureConsent,
                    automaticListChangeConsent,
                    Instant.now(),
                    provenance
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("purchasedPerishables", purchasedPerishables);
            map.put("discardedPerishables", discardedPerishables);
            map.put("advisorySuggestions", advisorySuggestions);
            map.put("ignoredSuggestions", ignoredSuggestions);
            map.put("sustainabilityConfidence", sustainabilityConfidence);
            map.put("externalDisclosureAttempt", externalDisclosureAttempt);
            map.put("externalDisclosureConsent", externalDisclosureConsent);
            map.put("automaticListChangeConsent", automaticListChangeConsent);
            map.put("provenance", provenance);
            return map;
        }
    }

    public enum PlanKind {
        RETAIN_SAFE_STATE,
        SOFT_RESPONSE,
        PRIVACY_BLOCK,
        HUMAN_REVIEW
    }

    public record PlanDefinition(
            String id,
            String name,
            String description,
            PlanKind kind,
            AutonomyMode mode,
            double referenceMismatch,
            double residualMismatchAtReference,
            double predictedConfidence,
            boolean requiresConfirmation,
            boolean reversible,
            double operationalCost,
            double stakeholderBurden,
            boolean requiresAutomaticListConsent
    ) {
        public PlanDefinition {
            id = identifier(id, "plans[].id");
            name = required(name, "plans[" + id + "].name");
            description = required(description, "plans[" + id + "].description");
            if (kind == null) throw new IllegalArgumentException("plans[" + id + "].kind is required");
            if (mode == null) throw new IllegalArgumentException("plans[" + id + "].mode is required");
            if (!Double.isFinite(referenceMismatch) || referenceMismatch <= 0 || referenceMismatch > 1) {
                throw new IllegalArgumentException(
                        "plans[" + id + "].referenceMismatch must be in (0,1]"
                );
            }
            unit(residualMismatchAtReference, "plans[" + id + "].residualMismatchAtReference");
            unit(predictedConfidence, "plans[" + id + "].predictedConfidence");
            unit(operationalCost, "plans[" + id + "].operationalCost");
            unit(stakeholderBurden, "plans[" + id + "].stakeholderBurden");
            if (kind == PlanKind.HUMAN_REVIEW && mode != AutonomyMode.ADVISORY) {
                throw new IllegalArgumentException("The human-review plan must use ADVISORY mode");
            }
            if (kind == PlanKind.PRIVACY_BLOCK && mode != AutonomyMode.RESTRICTIVE) {
                throw new IllegalArgumentException("The privacy-block plan must use RESTRICTIVE mode");
            }
        }

        public double predictedResidual(double aggregateMismatch) {
            return Math.max(
                    0,
                    Math.min(1, aggregateMismatch * residualMismatchAtReference / referenceMismatch)
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("description", description);
            map.put("kind", kind.name());
            map.put("mode", mode.name());
            map.put("referenceMismatch", referenceMismatch);
            map.put("residualMismatchAtReference", residualMismatchAtReference);
            map.put("predictedConfidence", predictedConfidence);
            map.put("requiresConfirmation", requiresConfirmation);
            map.put("reversible", reversible);
            map.put("operationalCost", operationalCost);
            map.put("stakeholderBurden", stakeholderBurden);
            map.put("requiresAutomaticListConsent", requiresAutomaticListConsent);
            return map;
        }
    }

    public record ContestationPolicy(
            String version,
            String authorisedStakeholder,
            boolean allowDiscardedCorrection,
            boolean allowConfidenceCorrection,
            boolean allowExternalDisclosureConsentCorrection,
            boolean allowAutomaticListConsentCorrection
    ) {
        public ContestationPolicy {
            version = required(version, "contestationPolicy.version");
            authorisedStakeholder = identifier(
                    authorisedStakeholder,
                    "contestationPolicy.authorisedStakeholder"
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("version", version);
            map.put("authorisedStakeholder", authorisedStakeholder);
            map.put("allowDiscardedCorrection", allowDiscardedCorrection);
            map.put("allowConfidenceCorrection", allowConfidenceCorrection);
            map.put(
                    "allowExternalDisclosureConsentCorrection",
                    allowExternalDisclosureConsentCorrection
            );
            map.put("allowAutomaticListConsentCorrection", allowAutomaticListConsentCorrection);
            return map;
        }
    }

    private static void unit(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static String identifier(String value, String field) {
        String result = required(value, field);
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException(
                    field + " must contain only letters, digits, '.', '_' or '-'"
            );
        }
        return result;
    }

    private static <T> List<T> copyRequired(List<T> value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must contain at least one item");
        }
        if (value.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException(field + " cannot contain null items");
        }
        return List.copyOf(value);
    }

    private static void unique(List<String> values, String field) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                throw new IllegalArgumentException("Duplicate " + field + ": " + value);
            }
        }
    }
}
