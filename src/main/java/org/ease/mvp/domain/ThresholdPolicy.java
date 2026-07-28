package org.ease.mvp.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record ThresholdPolicy(
        double advisory,
        double assistive,
        double delegated,
        double restrictive,
        double minimumConfidence,
        String version,
        String owner,
        String calibrationStatus
) {
    public ThresholdPolicy {
        if (!(0 <= advisory && advisory <= assistive && assistive <= delegated
                && delegated <= restrictive && restrictive <= 1)) {
            throw new IllegalArgumentException("Thresholds must be ordered in [0,1]");
        }
        if (!Double.isFinite(minimumConfidence)
                || minimumConfidence < 0
                || minimumConfidence > 1) {
            throw new IllegalArgumentException("qMin must be in [0,1]");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Threshold version is required");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Threshold owner is required");
        }
        if (calibrationStatus == null || calibrationStatus.isBlank()) {
            throw new IllegalArgumentException("Threshold calibrationStatus is required");
        }
    }

    public AutonomyMode maximumModeFor(double mismatch) {
        if (mismatch >= restrictive) return AutonomyMode.RESTRICTIVE;
        if (mismatch >= delegated) return AutonomyMode.DELEGATED;
        if (mismatch >= assistive) return AutonomyMode.ASSISTIVE;
        return AutonomyMode.ADVISORY;
    }

    public String regionFor(double mismatch) {
        if (mismatch >= restrictive) return "RESTRICTIVE_MAY_BE_CONSIDERED";
        if (mismatch >= delegated) return "DELEGATED_MAY_BE_CONSIDERED";
        if (mismatch >= assistive) return "ASSISTIVE_MAY_BE_CONSIDERED";
        if (mismatch >= advisory) return "ADVISORY_RESPONSE";
        return "MONITOR";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tauA", advisory);
        map.put("tauAS", assistive);
        map.put("tauD", delegated);
        map.put("tauR", restrictive);
        map.put("qMin", minimumConfidence);
        map.put("version", version);
        map.put("owner", owner);
        map.put("calibrationStatus", calibrationStatus);
        return map;
    }
}
