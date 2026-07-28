package org.ease.mvp.llm.client;

import java.util.Map;

public record StructuredLlmRequest(
        String schemaName,
        String systemPrompt,
        String userPrompt,
        Map<String, Object> jsonSchema,
        int maximumOutputTokens
) {
}
