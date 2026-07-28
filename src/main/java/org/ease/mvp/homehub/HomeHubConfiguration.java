package org.ease.mvp.homehub;

import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.EthicalConstraint;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.domain.Stakeholder;
import org.ease.mvp.domain.ThresholdPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain adapter for the Home Hub evaluator. All deployment-specific values come
 * from {@link DeploymentConfiguration}; this class only exposes the semantics
 * implemented by the current MVP evaluator.
 */
public final class HomeHubConfiguration {
    private final DeploymentConfiguration deployment;

    public HomeHubConfiguration() {
        this(ConfigurationFiles.loadDefault());
    }

    public HomeHubConfiguration(DeploymentConfiguration deployment) {
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment configuration is required");
        }
        this.deployment = deployment;
    }

    public DeploymentConfiguration deployment() {
        return deployment;
    }

    public ThresholdPolicy thresholds() {
        return deployment.thresholds();
    }

    public List<Stakeholder> stakeholders() {
        return deployment.stakeholders();
    }

    public List<EthicalConstraint> constraints() {
        return deployment.constraints();
    }

    public String context() {
        return deployment.context();
    }

    public List<String> contestationRoutes() {
        List<String> routes = new ArrayList<>();
        DeploymentConfiguration.ContestationPolicy policy = deployment.contestationPolicy();
        if (policy.allowDiscardedCorrection()) {
            routes.add("Correct the historical number of discarded perishables");
        }
        if (policy.allowConfidenceCorrection()) {
            routes.add("Correct the historical evidence-confidence value");
        }
        if (policy.allowExternalDisclosureConsentCorrection()) {
            routes.add("Correct historical external-disclosure consent or record a prospective change");
        }
        if (policy.allowAutomaticListConsentCorrection()) {
            routes.add("Correct historical automatic-list consent or record a prospective change");
        }
        routes.add("Request review of constraint precedence or threshold version "
                + thresholds().version());
        routes.add("Challenge a predicted effect, selected autonomy mode, or request rollback");
        return List.copyOf(routes);
    }

    public List<String> uncertainty(Evidence evidence) {
        List<String> uncertainty = new ArrayList<>();
        if (evidence.sustainabilityConfidence() < 1) {
            uncertainty.add("At least one disposal event may be misclassified; sustainability confidence is "
                    + String.format("%.3f", evidence.sustainabilityConfidence()));
        }
        uncertainty.add("Candidate residual mismatches are configured predictions, not empirically validated estimates");
        uncertainty.add("Evaluator parameters and threshold calibration belong to deployment "
                + deployment.id() + " (" + thresholds().version() + ")");
        return List.copyOf(uncertainty);
    }

    public List<Map<String, Object>> planLibraryDescription() {
        return deployment.plans().stream().map(DeploymentConfiguration.PlanDefinition::toMap).toList();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> configuration = new LinkedHashMap<>(deployment.toMap());
        configuration.put(
                "autonomyLadder",
                Arrays.stream(AutonomyMode.values()).map(AutonomyMode::toMap).toList()
        );
        configuration.put("runtimeArchitecture", Map.of(
                "outerLoop", List.of("Monitor", "Analyse", "Plan", "Execute", "Knowledge"),
                "innerDeliberation", List.of(
                        "BeliefRevision",
                        "DesireGenerator",
                        "HomeHubPlanLibrary",
                        "BdiDeliberator"
                ),
                "configurationSource", DeploymentConfiguration.SCHEMA_VERSION
        ));
        return configuration;
    }
}
