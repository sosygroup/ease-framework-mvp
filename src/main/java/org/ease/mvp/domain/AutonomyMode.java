package org.ease.mvp.domain;

import java.util.Map;

public enum AutonomyMode {
    ADVISORY(0, "Suggest; the user decides"),
    ASSISTIVE(1, "Propose a concrete change and request confirmation"),
    DELEGATED(2, "Perform an authorised change automatically"),
    RESTRICTIVE(3, "Enforce a non-negotiable safeguard");

    private final int intrusionRank;
    private final String allocation;

    AutonomyMode(int intrusionRank, String allocation) {
        this.intrusionRank = intrusionRank;
        this.allocation = allocation;
    }

    public int intrusionRank() {
        return intrusionRank;
    }

    public String allocation() {
        return allocation;
    }

    public Map<String, Object> toMap() {
        return Map.of("name", name(), "rank", intrusionRank, "allocation", allocation);
    }
}
