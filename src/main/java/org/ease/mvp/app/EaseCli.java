package org.ease.mvp.app;

import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.mapek.CycleResult;
import org.ease.mvp.runtime.EaseEngine;
import org.ease.mvp.support.Json;

import java.time.Instant;
import java.nio.file.Path;
import java.util.Map;

public final class EaseCli {
    private EaseCli() {
    }

    public static void main(String[] args) {
        String command = "demo";
        Path configurationPath = null;
        for (String argument : args) {
            if (argument.startsWith("--config=")) {
                configurationPath = Path.of(argument.substring("--config=".length()));
            } else {
                command = argument;
            }
        }
        DeploymentConfiguration configuration = configurationPath == null
                ? ConfigurationFiles.loadDefault()
                : ConfigurationFiles.load(configurationPath);
        EaseEngine engine = new EaseEngine(configuration);
        Object result;
        switch (command) {
            case "demo" -> result = engine.reproducePaperScenario().toMap();
            case "privacy" -> result = engine.runPrivacyViolationScenario().toMap();
            case "low-confidence" -> {
                Evidence base = configuration.defaultEvidence().toEvidence();
                Evidence uncertain = new Evidence(
                        base.purchasedPerishables(), base.discardedPerishables(),
                        base.advisorySuggestions(), base.ignoredSuggestions(),
                        0.40, false, false, false,
                        Instant.now(), "Degraded sensor adapter"
                );
                result = engine.runCycle(uncertain).toMap();
            }
            case "contest" -> {
                CycleResult original = engine.reproducePaperScenario();
                CycleResult revised = engine.contestDiscardedEvidence(
                        original.trace().id(),
                        1,
                        "One item spoiled before delivery and was not avoidable household waste"
                );
                result = Map.of("original", original.toMap(), "revised", revised.toMap());
            }
            default -> throw new IllegalArgumentException(
                    "Unknown command. Use: demo | privacy | low-confidence | contest [--config=path]"
            );
        }
        System.out.println(Json.stringify(result));
    }
}
