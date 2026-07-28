package org.ease.mvp.app;

import org.ease.mvp.llm.runtime.LlmRuntime;
import org.ease.mvp.scenario.ScenarioBatchCodec;
import org.ease.mvp.scenario.ScenarioBatchDefinition;
import org.ease.mvp.scenario.ScenarioBatchResult;
import org.ease.mvp.scenario.ScenarioCsvExporter;
import org.ease.mvp.scenario.ScenarioRunner;
import org.ease.mvp.support.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BatchCli {
    private BatchCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: BatchCli <batch.json> [output-directory]"
            );
        }
        Path input = Path.of(args[0]);
        Path outputDirectory = args.length == 2 ? Path.of(args[1]) : Path.of("output", "batch");
        ScenarioBatchDefinition batch = ScenarioBatchCodec.parse(
                Files.readString(input, StandardCharsets.UTF_8)
        );
        ScenarioBatchResult result = new ScenarioRunner(new LlmRuntime()).run(batch);
        Files.createDirectories(outputDirectory);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(result.completedAt());
        String stem = batch.batchId() + "-" + timestamp;
        Path jsonPath = outputDirectory.resolve(stem + ".json");
        Path csvPath = outputDirectory.resolve(stem + ".csv");
        Files.writeString(jsonPath, result.toJson(), StandardCharsets.UTF_8);
        Files.writeString(csvPath, new ScenarioCsvExporter().export(result), StandardCharsets.UTF_8);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchId", result.batchId());
        summary.put("succeeded", result.succeeded());
        summary.put("failed", result.failed());
        summary.put("json", jsonPath.toAbsolutePath().toString());
        summary.put("csv", csvPath.toAbsolutePath().toString());
        System.out.println(Json.stringify(summary));
    }
}
