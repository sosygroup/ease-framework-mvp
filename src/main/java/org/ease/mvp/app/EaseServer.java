package org.ease.mvp.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.ease.mvp.configuration.ConfigurationFiles;
import org.ease.mvp.configuration.DeploymentConfiguration;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.llm.config.LlmApiProtocol;
import org.ease.mvp.llm.runtime.LlmRuntime;
import org.ease.mvp.mapek.knowledge.ContestationRequest;
import org.ease.mvp.mapek.knowledge.CorrectionType;
import org.ease.mvp.runtime.EaseEngine;
import org.ease.mvp.scenario.BatchJobManager;
import org.ease.mvp.scenario.ScenarioBatchResult;
import org.ease.mvp.scenario.ScenarioCsvExporter;
import org.ease.mvp.support.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class EaseServer {
    private volatile EaseEngine engine;
    private volatile DeploymentConfiguration deployment;
    private final LlmRuntime llmRuntime;
    private final BatchJobManager batchJobs;
    private final HttpServer server;

    private EaseServer(int port) throws IOException {
        llmRuntime = new LlmRuntime();
        deployment = ConfigurationFiles.loadDefault();
        engine = new EaseEngine(deployment, llmRuntime);
        batchJobs = new BatchJobManager(llmRuntime);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        registerRoutes();
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        for (String argument : args) {
            if (argument.startsWith("--port=")) {
                port = Integer.parseInt(argument.substring("--port=".length()));
            }
        }
        EaseServer app = new EaseServer(port);
        app.server.start();
        System.out.println("EASE MVP is running at http://127.0.0.1:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    private void registerRoutes() {
        server.createContext("/api/health", exchange -> json(exchange, 200, Map.of("status", "UP", "service", "EASE MVP")));
        server.createContext("/api/state", guarded(exchange -> {
            requireMethod(exchange, "GET");
            json(exchange, 200, engine.state());
        }));
        server.createContext("/api/cycle", guarded(exchange -> {
            requireMethod(exchange, "POST");
            Map<String, String> form = form(exchange);
            Evidence current = currentEvidence();
            Evidence evidence = new Evidence(
                    integer(form, "purchasedPerishables", current.purchasedPerishables()),
                    integer(form, "discardedPerishables", current.discardedPerishables()),
                    integer(form, "advisorySuggestions", current.advisorySuggestions()),
                    integer(form, "ignoredSuggestions", current.ignoredSuggestions()),
                    decimal(form, "sustainabilityConfidence", current.sustainabilityConfidence()),
                    bool(form, "externalDisclosureAttempt", false),
                    bool(form, "externalDisclosureConsent", false),
                    bool(form, "automaticListChangeConsent", false),
                    Instant.now(),
                    "Interactive Home Hub sensor adapter"
            );
            json(exchange, 200, engine.runCycle(evidence).toMap());
        }));
        server.createContext("/api/scenarios/paper", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 200, engine.reproducePaperScenario().toMap());
        }));
        server.createContext("/api/scenarios/worked", guarded(exchange -> {
            requireMethod(exchange, "POST");
            applyConfiguration(ConfigurationFiles.loadDefault());
            json(exchange, 200, engine.reproducePaperScenario().toMap());
        }));
        server.createContext("/api/scenarios/privacy", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 200, engine.runPrivacyViolationScenario().toMap());
        }));
        server.createContext("/api/confirm", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 200, engine.confirm(required(form(exchange), "traceId")).toMap());
        }));
        server.createContext("/api/contest", guarded(exchange -> {
            requireMethod(exchange, "POST");
            Map<String, String> form = form(exchange);
            json(exchange, 200, engine.contestEvidence(
                    required(form, "traceId"),
                    new ContestationRequest(
                            form.getOrDefault(
                                    "stakeholderId",
                                    deployment.contestationPolicy().authorisedStakeholder()
                            ),
                            CorrectionType.parse(form.get("correctionType")),
                            nullableInteger(form, "correctedDiscardedPerishables"),
                            nullableDecimal(form, "correctedConfidence"),
                            nullableBoolean(form, "correctedExternalDisclosureConsent"),
                            nullableBoolean(form, "correctedAutomaticListChangeConsent"),
                            required(form, "reason")
                    )
            ).toMap());
        }));
        server.createContext("/api/configuration", guarded(exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                json(exchange, 200, deployment.toMap());
                return;
            }
            requireMethod(exchange, "POST");
            DeploymentConfiguration replacement = ConfigurationFiles.parse(body(exchange));
            applyConfiguration(replacement);
            json(exchange, 200, replacement.toMap());
        }));
        server.createContext("/api/configuration/download", guarded(exchange -> {
            requireMethod(exchange, "GET");
            download(
                    exchange,
                    200,
                    "application/json; charset=utf-8",
                    deployment.id() + ".json",
                    Json.stringify(deployment.toMap())
            );
        }));
        server.createContext("/api/configurations/examples", guarded(exchange -> {
            requireMethod(exchange, "GET");
            Map<String, Object> examples = new LinkedHashMap<>();
            examples.put("worked", ConfigurationFiles.loadDefault().toMap());
            examples.put(
                    "advisory-first",
                    ConfigurationFiles.loadBundled("bundled:advisory-first").toMap()
            );
            examples.put(
                    "conservative",
                    ConfigurationFiles.loadBundled("bundled:conservative-governance").toMap()
            );
            json(exchange, 200, examples);
        }));
        server.createContext("/api/batch/example", guarded(exchange -> {
            requireMethod(exchange, "GET");
            json(
                    exchange,
                    200,
                    Json.parse(ConfigurationFiles.readBundledText("/config/example-batch.json"))
            );
        }));
        server.createContext("/api/batch/start", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 202, batchJobs.start(body(exchange)));
        }));
        server.createContext("/api/batch/status", guarded(exchange -> {
            requireMethod(exchange, "GET");
            json(exchange, 200, batchJobs.status(required(query(exchange), "jobId")));
        }));
        server.createContext("/api/batch/export", guarded(exchange -> {
            requireMethod(exchange, "GET");
            Map<String, String> query = query(exchange);
            String jobId = required(query, "jobId");
            String format = query.getOrDefault("format", "json").toLowerCase();
            ScenarioBatchResult result = batchJobs.result(jobId);
            if ("json".equals(format)) {
                download(
                        exchange,
                        200,
                        "application/json; charset=utf-8",
                        result.batchId() + ".json",
                        result.toJson()
                );
            } else if ("csv".equals(format)) {
                download(
                        exchange,
                        200,
                        "text/csv; charset=utf-8",
                        result.batchId() + ".csv",
                        new ScenarioCsvExporter().export(result)
                );
            } else {
                throw new IllegalArgumentException("format must be json or csv");
            }
        }));
        server.createContext("/api/reset", guarded(exchange -> {
            requireMethod(exchange, "POST");
            engine.reset();
            json(exchange, 200, engine.state());
        }));
        server.createContext("/api/llm/configure", guarded(exchange -> {
            requireMethod(exchange, "POST");
            Map<String, String> form = form(exchange);
            json(exchange, 200, engine.configureLlm(
                    bool(form, "enabled", false),
                    required(form, "endpoint"),
                    LlmApiProtocol.parse(required(form, "protocol")),
                    required(form, "model"),
                    form.get("apiKey"),
                    form.getOrDefault("authHeader", "Authorization"),
                    form.getOrDefault("authScheme", "Bearer"),
                    bool(form, "evidenceDisclosureConsent", false),
                    integer(form, "timeoutSeconds", 30)
            ));
        }));
        server.createContext("/api/llm/test", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 200, engine.testLlmConnection());
        }));
        server.createContext("/api/llm/clear-key", guarded(exchange -> {
            requireMethod(exchange, "POST");
            json(exchange, 200, engine.clearLlmApiKey());
        }));
        server.createContext("/app.js", staticResource("/web/app.js", "text/javascript; charset=utf-8"));
        server.createContext("/styles.css", staticResource("/web/styles.css", "text/css; charset=utf-8"));
        server.createContext("/", staticResource("/web/index.html", "text/html; charset=utf-8"));
    }

    @SuppressWarnings("unchecked")
    private Evidence currentEvidence() {
        Map<String, Object> evidence = (Map<String, Object>) engine.state().get("currentEvidence");
        return new Evidence(
                ((Number) evidence.get("purchasedPerishables")).intValue(),
                ((Number) evidence.get("discardedPerishables")).intValue(),
                ((Number) evidence.get("advisorySuggestions")).intValue(),
                ((Number) evidence.get("ignoredSuggestions")).intValue(),
                ((Number) evidence.get("sustainabilityConfidence")).doubleValue(),
                (Boolean) evidence.get("externalDisclosureAttempt"),
                (Boolean) evidence.get("externalDisclosureConsent"),
                (Boolean) evidence.get("automaticListChangeConsent"),
                Instant.now(),
                String.valueOf(evidence.get("provenance"))
        );
    }

    private HttpHandler guarded(ThrowingHandler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (IllegalArgumentException exception) {
                json(exchange, 400, Map.of("error", exception.getMessage()));
            } catch (IllegalStateException exception) {
                json(exchange, 409, Map.of("error", exception.getMessage()));
            } catch (Exception exception) {
                exception.printStackTrace();
                json(exchange, 500, Map.of("error", "Internal error", "detail", String.valueOf(exception.getMessage())));
            }
        };
    }

    private HttpHandler staticResource(String resource, String contentType) {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                json(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try (InputStream input = EaseServer.class.getResourceAsStream(resource)) {
                if (input == null) {
                    json(exchange, 404, Map.of("error", "Resource not found"));
                    return;
                }
                byte[] body = input.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        };
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equals(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("Expected " + method + " request");
        }
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = body(exchange);
        Map<String, String> form = new LinkedHashMap<>();
        if (body.isBlank()) return form;
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            form.put(key, value);
        }
        return form;
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing form field: " + key);
        return value;
    }

    private static int integer(Map<String, String> form, String key, int defaultValue) {
        String value = form.get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static double decimal(Map<String, String> form, String key, double defaultValue) {
        String value = form.get(key);
        return value == null || value.isBlank() ? defaultValue : Double.parseDouble(value);
    }

    private static Integer nullableInteger(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static Double nullableDecimal(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }

    private static Boolean nullableBoolean(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.isBlank()) return null;
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.valueOf(value);
    }

    private static boolean bool(Map<String, String> form, String key, boolean defaultValue) {
        String value = form.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void download(
            HttpExchange exchange,
            int status,
            String contentType,
            String filename,
            String value
    ) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set(
                "Content-Disposition",
                "attachment; filename=\"" + filename.replace("\"", "") + "\""
        );
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private synchronized void applyConfiguration(DeploymentConfiguration replacement) {
        deployment = replacement;
        engine = new EaseEngine(replacement, llmRuntime);
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
