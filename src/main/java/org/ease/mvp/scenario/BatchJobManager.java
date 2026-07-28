package org.ease.mvp.scenario;

import org.ease.mvp.llm.runtime.LlmRuntime;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BatchJobManager {
    private static final int MAXIMUM_RETAINED_JOBS = 20;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final LlmRuntime llmRuntime;

    public BatchJobManager(LlmRuntime llmRuntime) {
        this.llmRuntime = llmRuntime;
    }

    public Map<String, Object> start(String json) {
        ScenarioBatchDefinition batch = ScenarioBatchCodec.parse(json);
        cleanup();
        Job job = new Job(
                "batch-job-" + UUID.randomUUID(),
                batch.batchId(),
                batch.name(),
                batch.scenarios().size()
        );
        jobs.put(job.id, job);
        Thread.startVirtualThread(() -> execute(job, batch));
        return job.toMap();
    }

    public Map<String, Object> status(String jobId) {
        return job(jobId).toMap();
    }

    public ScenarioBatchResult result(String jobId) {
        Job job = job(jobId);
        if (job.result == null) {
            throw new IllegalStateException("Batch job " + jobId + " has not completed");
        }
        return job.result;
    }

    private void execute(Job job, ScenarioBatchDefinition batch) {
        job.state = "RUNNING";
        job.startedAt = Instant.now();
        try {
            ScenarioBatchResult result = new ScenarioRunner(llmRuntime).run(batch, item -> {
                job.partialResults.add(item);
                job.completed = job.partialResults.size();
                if ("ERROR".equals(item.status())) job.errors++;
            });
            job.result = result;
            job.completed = job.total;
            job.state = result.failed() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        } catch (Exception exception) {
            job.state = "FAILED";
            job.fatalError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
        } finally {
            job.completedAt = Instant.now();
        }
    }

    private Job job(String jobId) {
        Job result = jobs.get(jobId);
        if (result == null) throw new IllegalArgumentException("Unknown batch job: " + jobId);
        return result;
    }

    private void cleanup() {
        if (jobs.size() < MAXIMUM_RETAINED_JOBS) return;
        jobs.values().stream()
                .filter(Job::terminal)
                .min(Comparator.comparing(job -> job.createdAt))
                .ifPresent(job -> jobs.remove(job.id));
    }

    private static final class Job {
        private final String id;
        private final String batchId;
        private final String batchName;
        private final int total;
        private final Instant createdAt = Instant.now();
        private final List<ScenarioResult> partialResults = new CopyOnWriteArrayList<>();
        private volatile String state = "QUEUED";
        private volatile int completed;
        private volatile int errors;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String fatalError;
        private volatile ScenarioBatchResult result;

        private Job(String id, String batchId, String batchName, int total) {
            this.id = id;
            this.batchId = batchId;
            this.batchName = batchName;
            this.total = total;
        }

        private boolean terminal() {
            return state.startsWith("COMPLETED") || "FAILED".equals(state);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("jobId", id);
            map.put("batchId", batchId);
            map.put("batchName", batchName);
            map.put("state", state);
            map.put("total", total);
            map.put("completed", completed);
            map.put("progress", total == 0 ? 0 : (double) completed / total);
            map.put("errors", errors);
            map.put("createdAt", createdAt.toString());
            map.put("startedAt", startedAt == null ? null : startedAt.toString());
            map.put("completedAt", completedAt == null ? null : completedAt.toString());
            map.put("fatalError", fatalError);
            map.put(
                    "partialResults",
                    partialResults.stream().map(ScenarioResult::toMap).toList()
            );
            map.put("result", result == null ? null : result.toMap());
            return map;
        }
    }
}
