package org.ease.mvp.llm.client;

import org.ease.mvp.llm.config.LlmApiProtocol;
import org.ease.mvp.llm.config.LlmSettings;
import org.ease.mvp.support.Json;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleLlmClient implements LlmClient {
    private static final int MAXIMUM_RESPONSE_BYTES = 2_000_000;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    OpenAiCompatibleLlmClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public StructuredLlmResponse complete(LlmSettings settings, StructuredLlmRequest request) throws Exception {
        Map<String, Object> payload = settings.protocol() == LlmApiProtocol.RESPONSES
                ? responsesPayload(settings, request)
                : chatCompletionsPayload(settings, request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(settings.endpoint()))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload)));
        if (!settings.apiKey().isBlank()) {
            String value = settings.authScheme().isBlank()
                    ? settings.apiKey()
                    : settings.authScheme() + " " + settings.apiKey();
            builder.header(settings.authHeader(), value);
        }

        long started = System.nanoTime();
        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        long latency = (System.nanoTime() - started) / 1_000_000;
        byte[] responseBytes;
        try (InputStream input = response.body()) {
            responseBytes = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
        }
        if (responseBytes.length > MAXIMUM_RESPONSE_BYTES) {
            throw new IllegalStateException("LLM response exceeds the configured safety limit");
        }
        Object parsed = Json.parse(new String(responseBytes, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalStateException("LLM endpoint returned a non-object JSON response");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM endpoint returned HTTP " + response.statusCode()
                    + ": " + errorMessage(root));
        }
        String output = settings.protocol() == LlmApiProtocol.RESPONSES
                ? responsesOutput(root)
                : chatCompletionsOutput(root);
        return new StructuredLlmResponse(
                output,
                response.headers().firstValue("x-request-id").orElse(string(root.get("id"))),
                string(root.get("model")),
                map(root.get("usage")),
                latency
        );
    }

    private Map<String, Object> responsesPayload(LlmSettings settings, StructuredLlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("input", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
        ));
        payload.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", request.schemaName(),
                "strict", true,
                "schema", request.jsonSchema()
        )));
        payload.put("max_output_tokens", request.maximumOutputTokens());
        payload.put("store", false);
        return payload;
    }

    private Map<String, Object> chatCompletionsPayload(LlmSettings settings, StructuredLlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
        ));
        payload.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", request.schemaName(),
                        "strict", true,
                        "schema", request.jsonSchema()
                )
        ));
        payload.put("max_completion_tokens", request.maximumOutputTokens());
        return payload;
    }

    private String responsesOutput(Map<?, ?> root) {
        if (root.get("output_text") instanceof String text && !text.isBlank()) return text;
        Object output = root.get("output");
        if (output instanceof List<?> items) {
            for (Object itemValue : items) {
                if (!(itemValue instanceof Map<?, ?> item)) continue;
                Object content = item.get("content");
                if (!(content instanceof List<?> parts)) continue;
                for (Object partValue : parts) {
                    if (!(partValue instanceof Map<?, ?> part)) continue;
                    if (part.get("refusal") instanceof String refusal && !refusal.isBlank()) {
                        throw new IllegalStateException("LLM refused the cognitive update: " + refusal);
                    }
                    if (part.get("text") instanceof String text && !text.isBlank()) return text;
                }
            }
        }
        throw new IllegalStateException("LLM response does not contain output text");
    }

    private String chatCompletionsOutput(Map<?, ?> root) {
        Object choices = root.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
            if (choice.get("message") instanceof Map<?, ?> message) {
                if (message.get("refusal") instanceof String refusal && !refusal.isBlank()) {
                    throw new IllegalStateException("LLM refused the cognitive update: " + refusal);
                }
                if (message.get("content") instanceof String content && !content.isBlank()) return content;
            }
        }
        throw new IllegalStateException("LLM response does not contain completion content");
    }

    private String errorMessage(Map<?, ?> root) {
        Object error = root.get("error");
        if (error instanceof Map<?, ?> map && map.get("message") != null) return limited(string(map.get("message")));
        return limited(string(error));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Collections.unmodifiableMap(result);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String limited(String value) {
        String normalised = value == null ? "Unknown provider error" : value.replaceAll("\\s+", " ").strip();
        return normalised.length() <= 500 ? normalised : normalised.substring(0, 500);
    }
}
