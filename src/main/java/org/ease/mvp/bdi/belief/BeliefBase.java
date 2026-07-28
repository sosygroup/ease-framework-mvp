package org.ease.mvp.bdi.belief;

import org.ease.mvp.llm.model.LlmCognitiveUpdate;

import java.util.ArrayList;
import java.util.List;

public record BeliefBase(List<String> statements) {
    public BeliefBase {
        statements = List.copyOf(statements);
    }

    public BeliefBase withCognitiveUpdate(LlmCognitiveUpdate update) {
        if (update == null || !update.applied()) return this;
        List<String> merged = new ArrayList<>(statements);
        update.beliefProposals().stream()
                .filter(assertion -> assertion.confidence() >= 0.50)
                .map(assertion -> assertion.traceStatement())
                .forEach(merged::add);
        return new BeliefBase(merged);
    }
}
