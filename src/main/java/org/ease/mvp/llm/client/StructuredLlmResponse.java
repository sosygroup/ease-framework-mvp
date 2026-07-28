package org.ease.mvp.llm.client;

import java.util.Map;

public record StructuredLlmResponse(
        String outputText,
        String requestId,
        String responseModel,
        Map<String, Object> usage,
        long latencyMillis
) {
}
