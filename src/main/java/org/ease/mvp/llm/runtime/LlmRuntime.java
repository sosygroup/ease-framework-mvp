package org.ease.mvp.llm.runtime;

import org.ease.mvp.llm.client.LlmClient;
import org.ease.mvp.llm.client.OpenAiCompatibleLlmClient;
import org.ease.mvp.llm.client.StructuredLlmRequest;
import org.ease.mvp.llm.client.StructuredLlmResponse;
import org.ease.mvp.llm.config.LlmApiProtocol;
import org.ease.mvp.llm.config.LlmSettings;
import org.ease.mvp.support.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LlmRuntime {
    private final LlmClient client;
    private volatile LlmSettings settings;

    public LlmRuntime() {
        this(new OpenAiCompatibleLlmClient(), LlmSettings.fromEnvironment());
    }

    public LlmRuntime(LlmClient client, LlmSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public synchronized Map<String, Object> configure(
            boolean enabled,
            String endpoint,
            LlmApiProtocol protocol,
            String model,
            String apiKey,
            String authHeader,
            String authScheme,
            boolean evidenceDisclosureConsent,
            int timeoutSeconds
    ) {
        settings = settings.updated(
                enabled, endpoint, protocol, model, apiKey, authHeader, authScheme,
                evidenceDisclosureConsent, timeoutSeconds
        );
        return settings.toPublicMap();
    }

    public synchronized Map<String, Object> clearApiKey() {
        settings = settings.withoutApiKey();
        return settings.toPublicMap();
    }

    public LlmSettings settings() {
        return settings;
    }

    public LlmClient client() {
        return client;
    }

    public Map<String, Object> publicConfiguration() {
        return settings.toPublicMap();
    }

    public Map<String, Object> testConnection() throws Exception {
        LlmSettings current = settings;
        if (!current.enabled()) throw new IllegalStateException("Enable the LLM connector before testing it");
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string", "enum", List.of("ok")),
                        "message", Map.of("type", "string")
                ),
                "required", List.of("status", "message"),
                "additionalProperties", false
        );
        StructuredLlmResponse response = client.complete(current, new StructuredLlmRequest(
                "ease_connection_test",
                "Return the requested connection-test JSON. Do not perform tools or external actions.",
                "Confirm that this endpoint can produce a strict structured response for EASE.",
                schema,
                120
        ));
        Map<String, Object> payload = object(Json.parse(response.outputText()));
        if (!"ok".equals(payload.get("status"))) {
            throw new IllegalStateException("LLM connection test returned an unexpected status");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("message", String.valueOf(payload.get("message")));
        result.put("requestId", response.requestId());
        result.put("responseModel", response.responseModel());
        result.put("latencyMillis", response.latencyMillis());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Expected a JSON object from the LLM endpoint");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
