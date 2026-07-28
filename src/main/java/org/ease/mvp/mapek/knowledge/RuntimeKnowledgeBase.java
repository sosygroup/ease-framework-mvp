package org.ease.mvp.mapek.knowledge;

import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.mapek.execute.ExecutionRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RuntimeKnowledgeBase {
    private final Evidence initialEvidence;
    private long version;
    private AutonomyMode currentMode = AutonomyMode.ADVISORY;
    private Evidence currentEvidence;
    private final List<DecisionTrace> traces = new ArrayList<>();
    private final List<ExecutionRecord> executionRecords = new ArrayList<>();
    private final List<LlmCognitiveUpdate> cognitiveUpdates = new ArrayList<>();

    public RuntimeKnowledgeBase(Evidence initialEvidence) {
        if (initialEvidence == null) throw new IllegalArgumentException("Initial evidence is required");
        this.initialEvidence = initialEvidence;
        this.currentEvidence = initialEvidence;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized AutonomyMode currentMode() {
        return currentMode;
    }

    public synchronized Evidence currentEvidence() {
        return currentEvidence;
    }

    public synchronized void updateEvidence(Evidence evidence) {
        currentEvidence = evidence;
        version++;
    }

    public synchronized long appendTrace(DecisionTrace trace) {
        traces.add(trace);
        if (trace.cognitiveUpdate() != null) cognitiveUpdates.add(trace.cognitiveUpdate());
        version = trace.knowledgeVersionAfter();
        return version;
    }

    public synchronized void applyExecution(ExecutionRecord record) {
        executionRecords.add(record);
        currentMode = record.resultingMode();
        version++;
    }

    public synchronized Optional<DecisionTrace> trace(String id) {
        return traces.stream().filter(trace -> trace.id().equals(id)).findFirst();
    }

    public synchronized boolean hasExecutionForTrace(String traceId) {
        return executionRecords.stream().anyMatch(record -> record.traceId().equals(traceId));
    }

    public synchronized List<DecisionTrace> traces() {
        return List.copyOf(traces);
    }

    public synchronized Optional<LlmCognitiveUpdate> latestCognitiveUpdate() {
        return cognitiveUpdates.isEmpty()
                ? Optional.empty()
                : Optional.of(cognitiveUpdates.get(cognitiveUpdates.size() - 1));
    }

    public synchronized void reset() {
        version = 0;
        currentMode = AutonomyMode.ADVISORY;
        currentEvidence = initialEvidence;
        traces.clear();
        executionRecords.clear();
        cognitiveUpdates.clear();
    }

    public synchronized Map<String, Object> toMap(Map<String, Object> configuration) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("knowledgeVersion", version);
        map.put("currentMode", currentMode.name());
        map.put("currentEvidence", currentEvidence.toMap());
        map.put("traces", traces.stream().map(DecisionTrace::toMap).toList());
        map.put("executionRecords", executionRecords.stream().map(ExecutionRecord::toMap).toList());
        map.put("cognitiveUpdates", cognitiveUpdates.stream().map(LlmCognitiveUpdate::toMap).toList());
        map.put("latestCognitiveUpdate", cognitiveUpdates.isEmpty()
                ? null
                : cognitiveUpdates.get(cognitiveUpdates.size() - 1).toMap());
        map.put("configuration", configuration);
        return map;
    }
}
