package org.ease.mvp.bdi.desire;

import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.mapek.analyse.GovernanceState;

import java.util.ArrayList;
import java.util.List;

public final class DesireGenerator {
    private final List<String> baselineDesires;
    private final String hardViolationDesire;

    public DesireGenerator(List<String> baselineDesires, String hardViolationDesire) {
        this.baselineDesires = List.copyOf(baselineDesires);
        this.hardViolationDesire = hardViolationDesire;
    }

    public DesireSet generate(GovernanceState governance) {
        List<String> desires = new ArrayList<>(baselineDesires);
        if (!governance.hardViolations().isEmpty()) {
            desires.add(0, hardViolationDesire);
        }
        return new DesireSet(desires);
    }

    public DesireSet generate(GovernanceState governance, LlmCognitiveUpdate cognitiveUpdate) {
        DesireSet baseline = generate(governance);
        if (cognitiveUpdate == null || !cognitiveUpdate.applied()) return baseline;
        List<String> desires = new ArrayList<>(baseline.statements());
        cognitiveUpdate.desireProposals().stream()
                .filter(assertion -> assertion.confidence() >= 0.50)
                .map(assertion -> assertion.traceStatement())
                .forEach(desires::add);
        return new DesireSet(desires);
    }
}
