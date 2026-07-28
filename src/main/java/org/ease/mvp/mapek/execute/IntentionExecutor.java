package org.ease.mvp.mapek.execute;

import org.ease.mvp.bdi.deliberation.DeliberationResult;
import org.ease.mvp.bdi.intention.CandidateIntention;
import org.ease.mvp.mapek.knowledge.DecisionTrace;
import org.ease.mvp.mapek.knowledge.RuntimeKnowledgeBase;

import java.time.Instant;

public final class IntentionExecutor {
    public void executeIfImmediate(
            DecisionTrace trace,
            DeliberationResult deliberation,
            RuntimeKnowledgeBase knowledge
    ) {
        if (!"EXECUTED".equals(deliberation.executionStatus()) || deliberation.selected() == null) {
            return;
        }
        knowledge.applyExecution(new ExecutionRecord(
                trace.id(),
                deliberation.selected().id(),
                "EXECUTED",
                deliberation.selected().mode(),
                deliberation.rationale(),
                Instant.now()
        ));
    }

    public ExecutionRecord confirm(String traceId, RuntimeKnowledgeBase knowledge) {
        DecisionTrace trace = knowledge.trace(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown trace: " + traceId));
        if (!"PENDING_CONFIRMATION".equals(trace.executionStatus())) {
            throw new IllegalStateException("Trace is not awaiting confirmation");
        }
        if (knowledge.hasExecutionForTrace(traceId)) {
            throw new IllegalStateException("Trace has already been executed");
        }
        CandidateIntention selected = trace.candidateIntentions().stream()
                .filter(candidate -> candidate.id().equals(trace.selectedIntentionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected intention is missing"));
        ExecutionRecord record = new ExecutionRecord(
                trace.id(),
                selected.id(),
                "EXECUTED_AFTER_CONFIRMATION",
                selected.mode(),
                "Charlie confirmed the proposed shopping-list quantity reductions",
                Instant.now()
        );
        knowledge.applyExecution(record);
        return record;
    }
}
