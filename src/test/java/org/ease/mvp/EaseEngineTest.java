package org.ease.mvp;

import com.sun.net.httpserver.HttpServer;
import org.ease.mvp.bdi.intention.CandidateIntention;
import org.ease.mvp.configuration.ConfigurationCodec;
import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.llm.client.LlmClient;
import org.ease.mvp.llm.client.OpenAiCompatibleLlmClient;
import org.ease.mvp.llm.client.StructuredLlmResponse;
import org.ease.mvp.llm.config.LlmApiProtocol;
import org.ease.mvp.llm.config.LlmSettings;
import org.ease.mvp.llm.runtime.LlmRuntime;
import org.ease.mvp.mapek.execute.ExecutionRecord;
import org.ease.mvp.mapek.knowledge.DecisionTrace;
import org.ease.mvp.mapek.analyse.MismatchIndicator;
import org.ease.mvp.mapek.knowledge.ContestationRequest;
import org.ease.mvp.mapek.knowledge.CorrectionType;
import org.ease.mvp.runtime.EaseEngine;
import org.ease.mvp.scenario.ScenarioBatchCodec;
import org.ease.mvp.scenario.ScenarioBatchResult;
import org.ease.mvp.scenario.ScenarioCsvExporter;
import org.ease.mvp.scenario.ScenarioResult;
import org.ease.mvp.scenario.ScenarioRunner;
import org.ease.mvp.support.Json;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class EaseEngineTest {
    private int passed;

    public static void main(String[] args) {
        EaseEngineTest suite = new EaseEngineTest();
        suite.paperScenarioSelectsAssistiveI2();
        suite.confirmationEnactsTheSelectedMode();
        suite.confirmationCannotExecuteTwice();
        suite.contestationRecomputesAndPreservesTheOriginalTrace();
        suite.contestationAfterExecutionRollsBackToAdvisory();
        suite.hardConstraintHasLexicographicPrecedence();
        suite.lowConfidenceRequestsHumanReview();
        suite.wasteWithoutSuggestionHistoryStillProducesMismatch();
        suite.invalidEvidenceIsRejected();
        suite.configurationRoundTripValidationAndFilePersistence();
        suite.configuredPlansAndThresholdsChangeOutcomes();
        suite.multiFieldHistoricalContestationIsTraceable();
        suite.prospectiveConsentDoesNotRewriteTheSourceEvent();
        suite.prospectiveCorrectionRejectsNonConsentFields();
        suite.batchExecutionIsolatesErrorsAndExportsConsistentData();
        suite.llmUpdateRevisesKnowledgeAndBdiInsideGovernanceBoundary();
        suite.llmFailureFallsBackWithoutBreakingTheCycle();
        suite.llmEvidenceRequiresExplicitDisclosureConsent();
        suite.hardPrivacyViolationPreventsLlmDisclosure();
        suite.responsesClientUsesConfiguredHandleAndSecret();
        System.out.println("PASS: " + suite.passed + " EASE MVP tests");
    }

    private void paperScenarioSelectsAssistiveI2() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace trace = engine.reproducePaperScenario().trace();

        close(0.62, indicator(trace, "Csustainability").mismatch(), 0.000_001, "sustainability mismatch");
        close(0.403, trace.governance().aggregateMismatch(), 0.000_001, "aggregate mismatch");
        close(0.935, trace.governance().aggregateConfidence(), 0.000_001, "aggregate confidence");
        equal(List.of(), trace.governance().hardViolations(), "hard-constraint state");
        equal("ASSISTIVE_MAY_BE_CONSIDERED", trace.governance().region(), "governance region");
        equal("I2", trace.selectedIntentionId(), "selected intention");
        equal(AutonomyMode.ASSISTIVE, trace.selectedMode(), "selected mode");
        equal("PENDING_CONFIRMATION", trace.executionStatus(), "confirmation gate");
        equal("REJECTED_INSUFFICIENT", candidate(trace, "I1").status(), "I1 status");
        equal("REJECTED_INADMISSIBLE", candidate(trace, "I3").status(), "I3 status");
        equal("REJECTED_OUTSIDE_GOVERNANCE_REGION", candidate(trace, "I4").status(), "I4 status");
        passed++;
    }

    private void confirmationEnactsTheSelectedMode() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace trace = engine.reproducePaperScenario().trace();
        ExecutionRecord record = engine.confirm(trace.id());

        equal("EXECUTED_AFTER_CONFIRMATION", record.status(), "execution status");
        equal(AutonomyMode.ASSISTIVE, record.resultingMode(), "execution mode");
        equal("ASSISTIVE", engine.state().get("currentMode"), "runtime mode");
        passed++;
    }

    private void contestationRecomputesAndPreservesTheOriginalTrace() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace original = engine.reproducePaperScenario().trace();
        DecisionTrace revised = engine.contestDiscardedEvidence(
                original.id(),
                1,
                "One item was not avoidable household waste"
        ).trace();

        equal(3, original.triggeringEvidence().discardedPerishables(), "immutable original evidence");
        equal(1, revised.triggeringEvidence().discardedPerishables(), "corrected evidence");
        equal(original.id(), revised.revisesTraceId(), "revision link");
        equal("REVISION_AFTER_CONTESTATION", revised.phase(), "revision phase");
        close(0, revised.governance().aggregateMismatch(), 0.000_001, "recomputed mismatch");
        equal("I0", revised.selectedIntentionId(), "revised intention");
        equal(AutonomyMode.ADVISORY, revised.selectedMode(), "restored mode");
        equal(2, ((List<?>) engine.state().get("traces")).size(), "append-only trace count");
        passed++;
    }

    private void confirmationCannotExecuteTwice() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace trace = engine.reproducePaperScenario().trace();
        engine.confirm(trace.id());
        boolean thrown = false;
        try {
            engine.confirm(trace.id());
        } catch (IllegalStateException expected) {
            thrown = true;
        }
        truth(thrown, "duplicate confirmation rejection");
        passed++;
    }

    private void hardConstraintHasLexicographicPrecedence() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace trace = engine.runPrivacyViolationScenario().trace();

        equal(List.of("Cprivacy"), trace.governance().hardViolations(), "hard violation");
        equal("HARD_CONSTRAINT_RESPONSE", trace.governance().region(), "hard governance region");
        equal("I5", trace.selectedIntentionId(), "privacy safeguard intention");
        equal(AutonomyMode.RESTRICTIVE, trace.selectedMode(), "mandatory restrictive safeguard");
        equal("EXECUTED", trace.executionStatus(), "hard response execution");
        equal("REJECTED_HARD_CONSTRAINT", candidate(trace, "I1").status(), "soft response rejection");
        equal("RESTRICTIVE", engine.state().get("currentMode"), "enacted hard response");
        passed++;
    }

    private void contestationAfterExecutionRollsBackToAdvisory() {
        EaseEngine engine = new EaseEngine();
        DecisionTrace original = engine.reproducePaperScenario().trace();
        engine.confirm(original.id());
        DecisionTrace revised = engine.contestDiscardedEvidence(
                original.id(),
                1,
                "One item was not avoidable household waste"
        ).trace();

        equal("EXECUTED", revised.executionStatus(), "rollback execution status");
        equal("I0", revised.selectedIntentionId(), "rollback intention");
        equal(AutonomyMode.ADVISORY, revised.selectedMode(), "rollback selected mode");
        equal("ADVISORY", engine.state().get("currentMode"), "runtime rollback mode");
        passed++;
    }

    private void lowConfidenceRequestsHumanReview() {
        EaseEngine engine = new EaseEngine();
        Evidence base = ConfigurationFiles.loadDefault().defaultEvidence().toEvidence();
        Evidence uncertain = new Evidence(
                base.purchasedPerishables(),
                base.discardedPerishables(),
                base.advisorySuggestions(),
                base.ignoredSuggestions(),
                0.40,
                false,
                false,
                false,
                Instant.now(),
                "Degraded sensor adapter"
        );
        DecisionTrace trace = engine.runCycle(uncertain).trace();

        truth(trace.governance().humanReviewRequired(), "human-review flag");
        equal("INSUFFICIENT_CONFIDENCE_HUMAN_REVIEW", trace.governance().region(), "confidence region");
        equal("I-review", trace.selectedIntentionId(), "safe-state intention");
        equal("HUMAN_REVIEW_REQUIRED", trace.executionStatus(), "safe-state status");
        equal(AutonomyMode.ADVISORY, trace.selectedMode(), "safe mode retained");
        passed++;
    }

    private void invalidEvidenceIsRejected() {
        boolean thrown = false;
        try {
            new Evidence(2, 3, 1, 0, 0.9, false, false, false, Instant.now(), "invalid");
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        truth(thrown, "invalid evidence rejection");
        passed++;
    }

    private void wasteWithoutSuggestionHistoryStillProducesMismatch() {
        EaseEngine engine = new EaseEngine();
        Evidence evidence = new Evidence(
                10,
                4,
                0,
                0,
                0.9,
                false,
                false,
                false,
                Instant.now(),
                "Inventory-only observation"
        );
        DecisionTrace trace = engine.runCycle(evidence).trace();
        truth(indicator(trace, "Csustainability").mismatch() > 0, "inventory-only sustainability mismatch");
        passed++;
    }

    private void configurationRoundTripValidationAndFilePersistence() {
        DeploymentConfiguration original = ConfigurationFiles.loadDefault();
        DeploymentConfiguration roundTrip =
                ConfigurationCodec.parse(ConfigurationCodec.write(original));
        equal(original.id(), roundTrip.id(), "configuration round-trip id");
        equal(original.thresholds().toMap(), roundTrip.thresholds().toMap(), "threshold round-trip");
        equal(original.plans().size(), roundTrip.plans().size(), "plan round-trip");

        boolean outOfRange = false;
        try {
            ConfigurationCodec.parse(
                    ConfigurationCodec.write(original)
                            .replace("\"qMin\":0.8", "\"qMin\":1.2")
            );
        } catch (IllegalArgumentException expected) {
            outOfRange = expected.getMessage().contains("qMin");
        }
        truth(outOfRange, "out-of-range configuration message");

        boolean missing = false;
        try {
            ConfigurationCodec.parse("{\"schemaVersion\":\"ease-deployment/v1\"}");
        } catch (IllegalArgumentException expected) {
            missing = expected.getMessage().contains("$.thresholds");
        }
        truth(missing, "missing configuration message");

        try {
            Path temporary = Files.createTempFile("ease-configuration-", ".json");
            ConfigurationFiles.save(temporary, original);
            equal(original.id(), ConfigurationFiles.load(temporary).id(), "saved configuration load");
            Files.deleteIfExists(temporary);
        } catch (Exception exception) {
            throw new AssertionError("configuration file persistence", exception);
        }
        passed++;
    }

    private void configuredPlansAndThresholdsChangeOutcomes() {
        DeploymentConfiguration advisory =
                ConfigurationFiles.loadBundled("bundled:advisory-first");
        DecisionTrace advisoryTrace =
                new EaseEngine(advisory, disabledLlmRuntime()).reproducePaperScenario().trace();
        equal("I1", advisoryTrace.selectedIntentionId(), "configured advisory-first plan");
        equal(AutonomyMode.ADVISORY, advisoryTrace.selectedMode(), "advisory-first mode");

        DeploymentConfiguration conservative =
                ConfigurationFiles.loadBundled("bundled:conservative-governance");
        DecisionTrace conservativeTrace =
                new EaseEngine(conservative, disabledLlmRuntime()).reproducePaperScenario().trace();
        equal("I0", conservativeTrace.selectedIntentionId(), "configured conservative outcome");
        equal("ADVISORY_RESPONSE", conservativeTrace.governance().region(), "configured threshold region");
        passed++;
    }

    private void multiFieldHistoricalContestationIsTraceable() {
        EaseEngine engine = new EaseEngine(ConfigurationFiles.loadDefault(), disabledLlmRuntime());
        DecisionTrace original = engine.reproducePaperScenario().trace();
        DecisionTrace corrected = engine.contestEvidence(
                original.id(),
                new ContestationRequest(
                        "charlie",
                        CorrectionType.HISTORICAL_FACT_CORRECTION,
                        1,
                        0.96,
                        null,
                        true,
                        "Correct disposal classification, confidence and standing consent"
                )
        ).trace();

        equal(3, original.triggeringEvidence().discardedPerishables(), "historical source retained");
        close(0.9, original.triggeringEvidence().sustainabilityConfidence(), 0, "original confidence retained");
        equal(1, corrected.triggeringEvidence().discardedPerishables(), "corrected discarded count");
        close(0.96, corrected.triggeringEvidence().sustainabilityConfidence(), 0, "corrected confidence");
        equal(true, corrected.triggeringEvidence().automaticListChangeConsent(), "corrected consent");
        equal(original.id(), corrected.revisesTraceId(), "historical revision link");
        equal(
                original.id(),
                corrected.contestation().sourceTraceId(),
                "contestation source trace"
        );
        equal(3, corrected.contestation().correctedValues().size(), "three corrected fields");
        passed++;
    }

    private void prospectiveConsentDoesNotRewriteTheSourceEvent() {
        EaseEngine engine = new EaseEngine(ConfigurationFiles.loadDefault(), disabledLlmRuntime());
        DecisionTrace source = engine.runPrivacyViolationScenario().trace();
        DecisionTrace prospective = engine.contestEvidence(
                source.id(),
                new ContestationRequest(
                        "charlie",
                        CorrectionType.PROSPECTIVE_CONSENT_CHANGE,
                        null,
                        null,
                        true,
                        null,
                        "Grant external-disclosure consent for future cycles"
                )
        ).trace();

        equal(List.of("Cprivacy"), source.governance().hardViolations(), "source violation retained");
        equal(List.of(), prospective.governance().hardViolations(), "future consent applied");
        equal("PROSPECTIVE_CONSENT_CHANGE", prospective.phase(), "prospective trace phase");
        equal(null, prospective.revisesTraceId(), "prospective change is not a revision");
        equal(source.id(), prospective.contestation().sourceTraceId(), "prospective source relation");
        equal(
                "APPLIES_TO_CURRENT_AND_FUTURE_CYCLES_ONLY",
                prospective.contestation().toMap().get("temporalEffect"),
                "prospective temporal effect"
        );
        passed++;
    }

    private void prospectiveCorrectionRejectsNonConsentFields() {
        boolean rejected = false;
        try {
            new ContestationRequest(
                    "charlie",
                    CorrectionType.PROSPECTIVE_CONSENT_CHANGE,
                    null,
                    0.95,
                    true,
                    null,
                    "Invalid prospective confidence correction"
            );
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("consent values only");
        }
        truth(rejected, "prospective non-consent rejection");
        passed++;
    }

    private void batchExecutionIsolatesErrorsAndExportsConsistentData() {
        ScenarioBatchResult batch = new ScenarioRunner(disabledLlmRuntime()).run(
                ScenarioBatchCodec.parse(
                        ConfigurationFiles.readBundledText("/config/example-batch.json")
                )
        );
        equal(4, batch.succeeded(), "successful batch scenarios");
        equal(1, batch.failed(), "isolated batch error");
        equal(5, batch.results().size(), "all batch results retained");
        ScenarioResult worked = batch.results().stream()
                .filter(result -> result.scenarioId().equals("worked-example"))
                .findFirst()
                .orElseThrow();
        equal("I2", worked.finalPlanId(), "batch worked-example regression");
        close(
                0.403,
                ((Number) worked.finalMetrics().get("aggregateMismatch")).doubleValue(),
                0.000_001,
                "batch worked mismatch"
        );
        ScenarioResult invalid = batch.results().stream()
                .filter(result -> result.scenarioId().equals("invalid-isolated"))
                .findFirst()
                .orElseThrow();
        equal("ERROR", invalid.status(), "per-scenario error status");
        truth(invalid.error().contains("discardedPerishables"), "per-scenario error message");

        String json = batch.toJson();
        String csv = new ScenarioCsvExporter().export(batch);
        truth(json.contains("\"scenarioId\":\"worked-example\""), "JSON scenario identity");
        truth(json.contains("\"finalPlanId\":\"I2\""), "JSON final plan");
        truth(csv.startsWith("schemaVersion,batchId,scenarioId"), "stable CSV header");
        truth(csv.contains(",worked-example,Worked example,"), "CSV scenario identity");
        truth(csv.contains(",I2,ASSISTIVE,PENDING_CONFIRMATION,"), "CSV final result");
        equal(ScenarioCsvExporter.COLUMNS.size(), csv.substring(0, csv.indexOf("\r\n")).split(",", -1).length, "CSV column count");
        passed++;
    }

    private void llmUpdateRevisesKnowledgeAndBdiInsideGovernanceBoundary() {
        String output = cognitiveOutput(0.10, 0.92);
        LlmClient client = (settings, request) -> new StructuredLlmResponse(
                output, "request-test", "test-model", Map.of("input_tokens", 120, "output_tokens", 80), 12
        );
        LlmSettings settings = new LlmSettings(
                true, "http://127.0.0.1:9999/v1/responses", LlmApiProtocol.RESPONSES,
                "test-model", "super-secret-key", "Authorization", "Bearer", true, 5
        );
        EaseEngine engine = new EaseEngine(new LlmRuntime(client, settings));
        DecisionTrace trace = engine.reproducePaperScenario().trace();

        equal("APPLIED_AS_GOVERNED_PROPOSAL", trace.cognitiveUpdate().status(), "LLM update status");
        truth(trace.beliefs().stream().anyMatch(item -> item.startsWith("LLM-B1:")), "LLM belief merged");
        truth(trace.desires().stream().anyMatch(item -> item.startsWith("LLM-D1:")), "LLM desire merged");
        equal("LLM_GOVERNED_PROPOSAL", candidate(trace, "I1").predictionSource(), "LLM prediction source");
        equal("I1", trace.selectedIntentionId(), "LLM-assessed least-intrusive intention");
        equal(AutonomyMode.ADVISORY, trace.selectedMode(), "governed LLM selection mode");
        String publicState = Json.stringify(engine.state());
        truth(!publicState.contains("super-secret-key"), "API key redaction");
        equal(true, ((Map<?, ?>) ((Map<?, ?>) engine.state().get("configuration")).get("llm")).get("apiKeyConfigured"), "public key status");
        boolean querySecretRejected = false;
        try {
            new LlmSettings(
                    true, "https://provider.example/v1/responses?api_key=leaked",
                    LlmApiProtocol.RESPONSES, "test-model", "", "Authorization", "Bearer", true, 5
            );
        } catch (IllegalArgumentException expected) {
            querySecretRejected = true;
        }
        truth(querySecretRejected, "credentials embedded in endpoint query are rejected");
        passed++;
    }

    private void llmFailureFallsBackWithoutBreakingTheCycle() {
        LlmClient client = (settings, request) -> {
            throw new IllegalStateException("simulated provider outage");
        };
        LlmSettings settings = new LlmSettings(
                true, "http://127.0.0.1:9999/v1/responses", LlmApiProtocol.RESPONSES,
                "test-model", "secret", "Authorization", "Bearer", true, 5
        );
        EaseEngine engine = new EaseEngine(new LlmRuntime(client, settings));
        DecisionTrace trace = engine.reproducePaperScenario().trace();

        equal("FAILED_LOCAL_FALLBACK", trace.cognitiveUpdate().status(), "LLM fallback status");
        equal("I2", trace.selectedIntentionId(), "deterministic fallback selection");
        equal("PENDING_CONFIRMATION", trace.executionStatus(), "fallback confirmation gate");
        passed++;
    }

    private void llmEvidenceRequiresExplicitDisclosureConsent() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = (settings, request) -> {
            calls.incrementAndGet();
            throw new AssertionError("LLM must not be called without evidence-disclosure consent");
        };
        LlmSettings settings = new LlmSettings(
                true, "http://127.0.0.1:9999/v1/responses", LlmApiProtocol.RESPONSES,
                "test-model", "secret", "Authorization", "Bearer", false, 5
        );
        DecisionTrace trace = new EaseEngine(new LlmRuntime(client, settings)).reproducePaperScenario().trace();

        equal(0, calls.get(), "no-consent provider calls");
        equal("SKIPPED_NO_LLM_DATA_CONSENT", trace.cognitiveUpdate().status(), "no-consent status");
        equal("I2", trace.selectedIntentionId(), "no-consent deterministic selection");
        passed++;
    }

    private void hardPrivacyViolationPreventsLlmDisclosure() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = (settings, request) -> {
            calls.incrementAndGet();
            throw new AssertionError("LLM must not be called while Cprivacy is violated");
        };
        LlmSettings settings = new LlmSettings(
                true, "http://127.0.0.1:9999/v1/responses", LlmApiProtocol.RESPONSES,
                "test-model", "secret", "Authorization", "Bearer", true, 5
        );
        DecisionTrace trace = new EaseEngine(new LlmRuntime(client, settings)).runPrivacyViolationScenario().trace();

        equal(0, calls.get(), "hard-privacy provider calls");
        equal("SKIPPED_HARD_PRIVACY_CONSTRAINT", trace.cognitiveUpdate().status(), "hard-privacy LLM status");
        equal("I5", trace.selectedIntentionId(), "hard-privacy deterministic intention");
        passed++;
    }

    private void responsesClientUsesConfiguredHandleAndSecret() {
        HttpServer server = null;
        try {
            AtomicReference<String> authorisation = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/responses", exchange -> {
                authorisation.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String body = Json.stringify(Map.of(
                        "id", "response-local-test",
                        "model", "mock-model",
                        "output", List.of(Map.of(
                                "type", "message",
                                "content", List.of(Map.of("type", "output_text", "text", cognitiveOutput(0.12, 0.91)))
                        )),
                        "usage", Map.of("input_tokens", 50, "output_tokens", 40)
                ));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();

            LlmSettings settings = new LlmSettings(
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses",
                    LlmApiProtocol.RESPONSES,
                    "mock-model",
                    "local-secret",
                    "Authorization",
                    "Bearer",
                    true,
                    5
            );
            EaseEngine engine = new EaseEngine(new LlmRuntime(new OpenAiCompatibleLlmClient(), settings));
            DecisionTrace trace = engine.reproducePaperScenario().trace();

            equal("Bearer local-secret", authorisation.get(), "configured authentication header");
            truth(requestBody.get().contains("\"type\":\"json_schema\""), "strict schema request");
            truth(requestBody.get().contains("\"store\":false"), "non-persistent provider request");
            equal("response-local-test", trace.cognitiveUpdate().requestId(), "provider request traceability");
            equal("APPLIED_AS_GOVERNED_PROPOSAL", trace.cognitiveUpdate().status(), "HTTP cognitive update");
            passed++;
        } catch (Exception exception) {
            throw new AssertionError("Responses client integration failed", exception);
        } finally {
            if (server != null) server.stop(0);
        }
    }

    private static String cognitiveOutput(double residual, double confidence) {
        return Json.stringify(Map.of(
                "summary", "Updated the system's inspectable BDI hypotheses from current evidence.",
                "beliefs", List.of(Map.of(
                        "statement", "advisory fatigue may be contributing to repeated ignored suggestions",
                        "confidence", 0.72,
                        "provenance", "LLM interpretation of advisory interaction history",
                        "contestable", true
                )),
                "desires", List.of(Map.of(
                        "statement", "reduce interaction fatigue while preserving Charlie's authority",
                        "confidence", 0.78,
                        "provenance", "active autonomy constraint and interaction history",
                        "contestable", true
                )),
                "planAssessments", List.of(Map.of(
                        "templateId", "I1",
                        "predictedResidualMismatch", residual,
                        "predictedConfidence", confidence,
                        "rationale", "A more targeted advisory recipe may be sufficient in this context"
                )),
                "knowledgeNotes", List.of("Reassess the advisory-fatigue hypothesis after the next interaction"),
                "uncertainties", List.of("No direct stakeholder confirmation of advisory fatigue")
        ));
    }

    private LlmRuntime disabledLlmRuntime() {
        LlmClient client = (settings, request) -> {
            throw new AssertionError("Disabled LLM runtime must not invoke a provider");
        };
        return new LlmRuntime(client, new LlmSettings(
                false,
                LlmSettings.DEFAULT_ENDPOINT,
                LlmApiProtocol.RESPONSES,
                LlmSettings.DEFAULT_MODEL,
                "",
                "Authorization",
                "Bearer",
                false,
                5
        ));
    }

    private MismatchIndicator indicator(DecisionTrace trace, String id) {
        return trace.mismatchIndicators().stream()
                .filter(indicator -> indicator.constraintId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private CandidateIntention candidate(DecisionTrace trace, String id) {
        return trace.candidateIntentions().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private void close(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private void truth(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
