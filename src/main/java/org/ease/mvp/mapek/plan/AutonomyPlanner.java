package org.ease.mvp.mapek.plan;

import org.ease.mvp.bdi.deliberation.BdiDeliberator;
import org.ease.mvp.bdi.desire.DesireGenerator;
import org.ease.mvp.bdi.desire.DesireSet;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.mapek.analyse.AnalysisResult;

public final class AutonomyPlanner {
    private final DesireGenerator desireGenerator;
    private final BdiDeliberator deliberator;

    public AutonomyPlanner(DesireGenerator desireGenerator, BdiDeliberator deliberator) {
        this.desireGenerator = desireGenerator;
        this.deliberator = deliberator;
    }

    public PlanningResult plan(
            Evidence evidence,
            AnalysisResult analysis,
            AutonomyMode currentMode,
            LlmCognitiveUpdate cognitiveUpdate
    ) {
        DesireSet desires = desireGenerator.generate(analysis.governance(), cognitiveUpdate);
        return new PlanningResult(
                desires,
                deliberator.deliberate(evidence, analysis.governance(), desires, currentMode, cognitiveUpdate)
        );
    }
}
