package org.ease.mvp.llm.config;

public enum LlmApiProtocol {
    RESPONSES,
    CHAT_COMPLETIONS;

    public static LlmApiProtocol parse(String value) {
        if (value == null || value.isBlank()) return RESPONSES;
        return valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
