package org.ease.mvp.runtime;

import org.ease.mvp.bdi.belief.BeliefRevision;
import org.ease.mvp.bdi.deliberation.BdiDeliberator;
import org.ease.mvp.bdi.desire.DesireGenerator;
import org.ease.mvp.bdi.intention.HomeHubPlanLibrary;
import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.homehub.HomeHubConfiguration;
import org.ease.mvp.llm.config.LlmApiProtocol;
import org.ease.mvp.llm.runtime.LlmRuntime;
import org.ease.mvp.llm.update.LlmKnowledgeBdiUpdater;
import org.ease.mvp.mapek.CycleResult;
import org.ease.mvp.mapek.MapeKLoop;
import org.ease.mvp.mapek.analyse.EthicalAnalyser;
import org.ease.mvp.mapek.execute.ExecutionRecord;
import org.ease.mvp.mapek.execute.IntentionExecutor;
import org.ease.mvp.mapek.knowledge.Contestation;
import org.ease.mvp.mapek.knowledge.ContestationRequest;
import org.ease.mvp.mapek.knowledge.CorrectionType;
import org.ease.mvp.mapek.knowledge.DecisionTrace;
import org.ease.mvp.mapek.knowledge.RuntimeKnowledgeBase;
import org.ease.mvp.mapek.monitor.EvidenceMonitor;
import org.ease.mvp.mapek.plan.AutonomyPlanner;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class EaseEngine {
    private final HomeHubConfiguration configuration;
    private final RuntimeKnowledgeBase knowledge;
    private final IntentionExecutor executor;
    private final LlmRuntime llmRuntime;
    private final MapeKLoop loop;

    public EaseEngine() {
        this(ConfigurationFiles.loadDefault(), new LlmRuntime());
    }

    public EaseEngine(LlmRuntime llmRuntime) {
        this(ConfigurationFiles.loadDefault(), llmRuntime);
    }

    public EaseEngine(DeploymentConfiguration deployment) {
        this(deployment, new LlmRuntime());
    }

    public EaseEngine(DeploymentConfiguration deployment, LlmRuntime llmRuntime) {
        this.configuration = new HomeHubConfiguration(deployment);
        this.knowledge = new RuntimeKnowledgeBase(deployment.defaultEvidence().toEvidence());
        this.executor = new IntentionExecutor();
        this.llmRuntime = llmRuntime;
        EvidenceMonitor monitor = new EvidenceMonitor(
                new BeliefRevision(deployment.evaluator().targetWasteRatio())
        );
        EthicalAnalyser analyser = new EthicalAnalyser(
                deployment.evaluator().targetWasteRatio(),
                deployment.evaluator().fullDeviationWasteRatio(),
                deployment.weights().sustainability(),
                deployment.weights().autonomy(),
                configuration.thresholds(),
                deployment.evaluator().version()
        );
        AutonomyPlanner planner = new AutonomyPlanner(
                new DesireGenerator(deployment.baselineDesires(), deployment.hardViolationDesire()),
                new BdiDeliberator(
                        configuration.thresholds(),
                        new HomeHubPlanLibrary(deployment)
                )
        );
        loop = new MapeKLoop(
                monitor, analyser, planner, executor, knowledge, configuration,
                new LlmKnowledgeBdiUpdater(llmRuntime, configuration)
        );
    }

    public synchronized CycleResult runCurrentCycle() {
        return loop.run(knowledge.currentEvidence(), null, null);
    }

    public synchronized CycleResult runCycle(Evidence evidence) {
        knowledge.updateEvidence(evidence);
        return loop.run(evidence, null, null);
    }

    public synchronized CycleResult reproducePaperScenario() {
        knowledge.reset();
        Evidence evidence = configuration.deployment().defaultEvidence().toEvidence();
        knowledge.updateEvidence(evidence);
        return loop.run(evidence, null, null);
    }

    public synchronized CycleResult runPrivacyViolationScenario() {
        knowledge.reset();
        Evidence base = configuration.deployment().defaultEvidence().toEvidence();
        Evidence privacyAttempt = new Evidence(
                base.purchasedPerishables(), base.discardedPerishables(),
                base.advisorySuggestions(), base.ignoredSuggestions(),
                base.sustainabilityConfidence(), true, false, false,
                Instant.now(), "Home Hub data-egress gateway + paper-scenario sensors"
        );
        knowledge.updateEvidence(privacyAttempt);
        return loop.run(privacyAttempt, null, null);
    }

    public synchronized CycleResult contestDiscardedEvidence(
            String traceId,
            int correctedDiscarded,
            String reason
    ) {
        return contestEvidence(traceId, new ContestationRequest(
                configuration.deployment().contestationPolicy().authorisedStakeholder(),
                CorrectionType.HISTORICAL_FACT_CORRECTION,
                correctedDiscarded,
                null,
                null,
                null,
                reason == null || reason.isBlank() ? "User correction" : reason
        ));
    }

    public synchronized CycleResult contestEvidence(
            String traceId,
            ContestationRequest request
    ) {
        if (request == null) throw new IllegalArgumentException("Contestation request is required");
        DecisionTrace original = knowledge.trace(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown trace: " + traceId));
        validateContestationPolicy(request);
        Evidence previous = request.correctionType() == CorrectionType.HISTORICAL_FACT_CORRECTION
                ? original.triggeringEvidence()
                : knowledge.currentEvidence();
        Map<String, Object> originalValues = originalValues(previous, request);
        Map<String, Object> correctedValues = request.correctedValues();
        if (originalValues.equals(correctedValues)) {
            throw new IllegalArgumentException(
                    "At least one corrected value must differ from the recorded value"
            );
        }
        String temporalProvenance = request.correctionType()
                == CorrectionType.HISTORICAL_FACT_CORRECTION
                ? "Accepted historical fact correction; source evidence retained in trace " + traceId
                : "Prospective consent change; prior event remains unchanged in trace " + traceId;
        Evidence corrected = previous.withCorrections(
                request.correctedDiscardedPerishables(),
                request.correctedConfidence(),
                request.correctedExternalDisclosureConsent(),
                request.correctedAutomaticListChangeConsent(),
                temporalProvenance
        );
        Contestation contestation = new Contestation(
                "contestation-" + UUID.randomUUID(),
                traceId,
                request.stakeholderId(),
                request.correctionType(),
                request.reason(),
                originalValues,
                correctedValues,
                "ACCEPTED_AND_RECOMPUTED",
                Instant.now()
        );
        knowledge.updateEvidence(corrected);
        String revisesTraceId = request.correctionType()
                == CorrectionType.HISTORICAL_FACT_CORRECTION
                ? traceId
                : null;
        return loop.run(corrected, revisesTraceId, contestation);
    }

    public synchronized ExecutionRecord confirm(String traceId) {
        return executor.confirm(traceId, knowledge);
    }

    public synchronized void reset() {
        knowledge.reset();
    }

    public synchronized Map<String, Object> configureLlm(
            boolean enabled,
            String endpoint,
            LlmApiProtocol protocol,
            String model,
            String apiKey,
            String authHeader,
            String authScheme,
            boolean evidenceDisclosureConsent,
            int timeoutSeconds
    ) {
        return llmRuntime.configure(
                enabled, endpoint, protocol, model, apiKey, authHeader, authScheme,
                evidenceDisclosureConsent, timeoutSeconds
        );
    }

    public synchronized Map<String, Object> clearLlmApiKey() {
        return llmRuntime.clearApiKey();
    }

    public synchronized Map<String, Object> testLlmConnection() throws Exception {
        return llmRuntime.testConnection();
    }

    public synchronized Map<String, Object> state() {
        Map<String, Object> runtimeConfiguration = new LinkedHashMap<>(configuration.toMap());
        runtimeConfiguration.put("llm", llmRuntime.publicConfiguration());
        return knowledge.toMap(runtimeConfiguration);
    }

    public DeploymentConfiguration deploymentConfiguration() {
        return configuration.deployment();
    }

    public LlmRuntime llmRuntime() {
        return llmRuntime;
    }

    private void validateContestationPolicy(ContestationRequest request) {
        DeploymentConfiguration.ContestationPolicy policy =
                configuration.deployment().contestationPolicy();
        if (!policy.authorisedStakeholder().equals(request.stakeholderId())) {
            throw new IllegalArgumentException(
                    "Stakeholder '" + request.stakeholderId()
                            + "' is not authorised by contestation policy " + policy.version()
            );
        }
        if (request.correctedDiscardedPerishables() != null
                && !policy.allowDiscardedCorrection()) {
            throw new IllegalArgumentException(
                    "The active contestation policy does not allow discarded-product correction"
            );
        }
        if (request.correctedConfidence() != null && !policy.allowConfidenceCorrection()) {
            throw new IllegalArgumentException(
                    "The active contestation policy does not allow confidence correction"
            );
        }
        if (request.correctedExternalDisclosureConsent() != null
                && !policy.allowExternalDisclosureConsentCorrection()) {
            throw new IllegalArgumentException(
                    "The active contestation policy does not allow external-consent correction"
            );
        }
        if (request.correctedAutomaticListChangeConsent() != null
                && !policy.allowAutomaticListConsentCorrection()) {
            throw new IllegalArgumentException(
                    "The active contestation policy does not allow automatic-list consent correction"
            );
        }
    }

    private Map<String, Object> originalValues(
            Evidence evidence,
            ContestationRequest request
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (request.correctedDiscardedPerishables() != null) {
            values.put("discardedPerishables", evidence.discardedPerishables());
        }
        if (request.correctedConfidence() != null) {
            values.put("sustainabilityConfidence", evidence.sustainabilityConfidence());
        }
        if (request.correctedExternalDisclosureConsent() != null) {
            values.put("externalDisclosureConsent", evidence.externalDisclosureConsent());
        }
        if (request.correctedAutomaticListChangeConsent() != null) {
            values.put("automaticListChangeConsent", evidence.automaticListChangeConsent());
        }
        return values;
    }
}
