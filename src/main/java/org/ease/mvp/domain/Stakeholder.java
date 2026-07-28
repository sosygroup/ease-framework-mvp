package org.ease.mvp.domain;

import java.util.List;
import java.util.Map;

public record Stakeholder(String id, String role, String authority, List<String> ethicalConcerns) {
    public Map<String, Object> toMap() {
        return Map.of(
                "id", id,
                "role", role,
                "authority", authority,
                "ethicalConcerns", ethicalConcerns
        );
    }
}
