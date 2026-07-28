package org.ease.mvp.llm.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record LlmSettings(
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
    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    public static final String DEFAULT_MODEL = "gpt-5.6-luna";

    public LlmSettings {
        endpoint = required(endpoint, "endpoint", 2_048);
        model = required(model, "model", 200);
        protocol = protocol == null ? LlmApiProtocol.RESPONSES : protocol;
        apiKey = apiKey == null ? "" : apiKey.strip();
        authHeader = authHeader == null || authHeader.isBlank() ? "Authorization" : authHeader.strip();
        authScheme = authScheme == null ? "Bearer" : authScheme.strip();
        if (!authHeader.matches("[A-Za-z0-9-]{1,80}")) {
            throw new IllegalArgumentException("authHeader must be a valid HTTP header name");
        }
        if (authHeader.equalsIgnoreCase("Host") || authHeader.equalsIgnoreCase("Content-Length")) {
            throw new IllegalArgumentException("authHeader cannot override a transport-controlled header");
        }
        if (apiKey.length() > 8_192 || authScheme.length() > 80) {
            throw new IllegalArgumentException("LLM authentication configuration is too long");
        }
        if (containsControlCharacter(apiKey) || containsControlCharacter(authScheme)) {
            throw new IllegalArgumentException("LLM authentication configuration contains control characters");
        }
        if (timeoutSeconds < 2 || timeoutSeconds > 120) {
            throw new IllegalArgumentException("timeoutSeconds must be between 2 and 120");
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("endpoint must be a valid absolute URI");
        }
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("endpoint must use http or https");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("endpoint must not contain embedded credentials");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("endpoint must not contain a fragment");
        }
        rejectQueryCredentials(uri);
    }

    public static LlmSettings fromEnvironment() {
        Map<String, String> environment = System.getenv();
        String key = environment.getOrDefault("EASE_LLM_API_KEY", "");
        boolean enabled = Boolean.parseBoolean(environment.getOrDefault(
                "EASE_LLM_ENABLED", String.valueOf(!key.isBlank())
        ));
        return new LlmSettings(
                enabled,
                environment.getOrDefault("EASE_LLM_ENDPOINT", DEFAULT_ENDPOINT),
                LlmApiProtocol.parse(environment.get("EASE_LLM_PROTOCOL")),
                environment.getOrDefault("EASE_LLM_MODEL", DEFAULT_MODEL),
                key,
                environment.getOrDefault("EASE_LLM_AUTH_HEADER", "Authorization"),
                environment.getOrDefault("EASE_LLM_AUTH_SCHEME", "Bearer"),
                Boolean.parseBoolean(environment.getOrDefault("EASE_LLM_DATA_DISCLOSURE_CONSENT", "false")),
                integer(environment.get("EASE_LLM_TIMEOUT_SECONDS"), 30)
        );
    }

    public LlmSettings updated(
            boolean newEnabled,
            String newEndpoint,
            LlmApiProtocol newProtocol,
            String newModel,
            String newApiKey,
            String newAuthHeader,
            String newAuthScheme,
            boolean newEvidenceDisclosureConsent,
            int newTimeoutSeconds
    ) {
        return new LlmSettings(
                newEnabled,
                newEndpoint,
                newProtocol,
                newModel,
                newApiKey == null || newApiKey.isBlank() ? apiKey : newApiKey,
                newAuthHeader,
                newAuthScheme,
                newEvidenceDisclosureConsent,
                newTimeoutSeconds
        );
    }

    public LlmSettings withoutApiKey() {
        return new LlmSettings(
                enabled, endpoint, protocol, model, "", authHeader, authScheme,
                evidenceDisclosureConsent, timeoutSeconds
        );
    }

    public String publicEndpoint() {
        return endpoint;
    }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", enabled);
        map.put("endpoint", publicEndpoint());
        map.put("protocol", protocol.name());
        map.put("model", model);
        map.put("apiKeyConfigured", !apiKey.isBlank());
        map.put("authHeader", authHeader);
        map.put("authScheme", authScheme);
        map.put("evidenceDisclosureConsent", evidenceDisclosureConsent);
        map.put("timeoutSeconds", timeoutSeconds);
        map.put("secretStorage", "PROCESS_MEMORY_ONLY");
        return map;
    }

    private static String required(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String result = value.strip();
        if (result.length() > maximumLength) throw new IllegalArgumentException(field + " is too long");
        return result;
    }

    private static int integer(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Integer.parseInt(value);
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static void rejectQueryCredentials(URI uri) {
        if (uri.getRawQuery() == null) return;
        for (String parameter : uri.getRawQuery().split("&")) {
            String rawName = parameter.split("=", 2)[0];
            String name;
            try {
                name = URLDecoder.decode(rawName, StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("endpoint contains an invalid query parameter");
            }
            boolean sensitive = name.equals("code") || name.equals("sig") || name.equals("sas")
                    || name.endsWith("key") || name.endsWith("token") || name.endsWith("secret")
                    || name.endsWith("signature") || name.endsWith("credential")
                    || name.endsWith("password") || name.endsWith("authorization");
            if (sensitive) {
                throw new IllegalArgumentException(
                        "endpoint must not contain credentials in its query; use the API key field"
                );
            }
        }
    }
}
