package org.ease.mvp.llm.client;

import org.ease.mvp.llm.config.LlmSettings;

public interface LlmClient {
    StructuredLlmResponse complete(LlmSettings settings, StructuredLlmRequest request) throws Exception;
}
