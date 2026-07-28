package org.ease.mvp.mapek;

import org.ease.mvp.bdi.deliberation.DeliberationResult;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.homehub.HomeHubConfiguration;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.llm.update.LlmKnowledgeBdiUpdater;
import org.ease.mvp.mapek.analyse.AnalysisResult;
import org.ease.mvp.mapek.analyse.EthicalAnalyser;
import org.ease.mvp.mapek.execute.IntentionExecutor;
import org.ease.mvp.mapek.knowledge.Contestation;
import org.ease.mvp.mapek.knowledge.DecisionTrace;
import org.ease.mvp.mapek.knowledge.RuntimeKnowledgeBase;
import org.ease.mvp.mapek.monitor.EvidenceMonitor;
import org.ease.mvp.mapek.monitor.MonitoringSnapshot;
import org.ease.mvp.mapek.plan.AutonomyPlanner;
import org.ease.mvp.mapek.plan.PlanningResult;

import java.time.Instant;
import java.util.UUID;

public final class MapeKLoop {
    private final EvidenceMonitor monitor;
    private final EthicalAnalyser analyser;
    private final AutonomyPlanner planner;
    private final IntentionExecutor executor;
    private final RuntimeKnowledgeBase knowledge;
    private final HomeHubConfiguration configuration;
    private final LlmKnowledgeBdiUpdater cognitiveUpdater;

    public MapeKLoop(
            EvidenceMonitor monitor,
            EthicalAnalyser analyser,
            AutonomyPlanner planner,
            IntentionExecutor executor,
            RuntimeKnowledgeBase knowledge,
            HomeHubConfiguration configuration,
            LlmKnowledgeBdiUpdater cognitiveUpdater
    ) {
        this.monitor = monitor;
        this.analyser = analyser;
        this.planner = planner;
        this.executor = executor;
        this.knowledge = knowledge;
        this.configuration = configuration;
        this.cognitiveUpdater = cognitiveUpdater;
    }

    public CycleResult run(Evidence evidence, String revisesTraceId, Contestation contestation) {
        long versionBefore = knowledge.version();
        AutonomyMode currentMode = knowledge.currentMode();

        MonitoringSnapshot monitoring = monitor.monitor(evidence, currentMode);
        AnalysisResult analysis = analyser.analyse(monitoring, currentMode);
        LlmCognitiveUpdate cognitiveUpdate = cognitiveUpdater.update(
                evidence, currentMode, monitoring, analysis, knowledge.latestCognitiveUpdate()
        );
        MonitoringSnapshot governedMonitoring = new MonitoringSnapshot(
                monitoring.evidence(), monitoring.beliefs().withCognitiveUpdate(cognitiveUpdate)
        );
        PlanningResult planning = planner.plan(evidence, analysis, currentMode, cognitiveUpdate);
        DeliberationResult deliberation = planning.deliberation();

        long versionAfter = versionBefore + 1;
        DecisionTrace trace = new DecisionTrace(
                "trace-" + UUID.randomUUID(),
                Instant.now(),
                versionBefore,
                versionAfter,
                phase(contestation),
                evidence,
                configuration.stakeholders(),
                configuration.context(),
                configuration.constraints(),
                governedMonitoring.beliefs().statements(),
                planning.desires().statements(),
                cognitiveUpdate,
                analysis.indicators(),
                analysis.governance(),
                configuration.thresholds(),
                deliberation.candidates(),
                deliberation.selected() == null ? null : deliberation.selected().id(),
                deliberation.selected() == null ? currentMode : deliberation.selected().mode(),
                deliberation.rationale(),
                configuration.uncertainty(evidence),
                deliberation.executionStatus(),
                configuration.contestationRoutes(),
                revisesTraceId,
                contestation
        );

        knowledge.appendTrace(trace);
        executor.executeIfImmediate(trace, deliberation, knowledge);
        return new CycleResult(trace, knowledge.version(), knowledge.currentMode());
    }

    private String phase(Contestation contestation) {
        if (contestation == null) return "DECISION";
        return contestation.correctionType()
                == org.ease.mvp.mapek.knowledge.CorrectionType.HISTORICAL_FACT_CORRECTION
                ? "REVISION_AFTER_CONTESTATION"
                : "PROSPECTIVE_CONSENT_CHANGE";
    }
}
