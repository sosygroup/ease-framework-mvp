package org.ease.mvp.mapek.analyse;

import org.ease.mvp.domain.AutonomyMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GovernanceState(
        List<String> hardViolations,
        double aggregateMismatch,
        double aggregateConfidence,
        String region,
        AutonomyMode maximumAutomaticallyAdmissibleMode,
        boolean humanReviewRequired,
        String thresholdVersion
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hardViolations", hardViolations);
        map.put("aggregateMismatch", aggregateMismatch);
        map.put("aggregateConfidence", aggregateConfidence);
        map.put("region", region);
        map.put("maximumAutomaticallyAdmissibleMode", maximumAutomaticallyAdmissibleMode.name());
        map.put("humanReviewRequired", humanReviewRequired);
        map.put("thresholdVersion", thresholdVersion);
        return map;
    }
}
