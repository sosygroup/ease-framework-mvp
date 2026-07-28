package org.ease.mvp.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record Evidence(
        int purchasedPerishables,
        int discardedPerishables,
        int advisorySuggestions,
        int ignoredSuggestions,
        double sustainabilityConfidence,
        boolean externalDisclosureAttempt,
        boolean externalDisclosureConsent,
        boolean automaticListChangeConsent,
        Instant observedAt,
        String provenance
) {
    public Evidence {
        if (purchasedPerishables <= 0) {
            throw new IllegalArgumentException("purchasedPerishables must be greater than zero");
        }
        if (discardedPerishables < 0 || discardedPerishables > purchasedPerishables) {
            throw new IllegalArgumentException("discardedPerishables must be between zero and purchasedPerishables");
        }
        if (advisorySuggestions < 0) {
            throw new IllegalArgumentException("advisorySuggestions cannot be negative");
        }
        if (ignoredSuggestions < 0 || ignoredSuggestions > advisorySuggestions) {
            throw new IllegalArgumentException("ignoredSuggestions must be between zero and advisorySuggestions");
        }
        if (sustainabilityConfidence < 0 || sustainabilityConfidence > 1) {
            throw new IllegalArgumentException("sustainabilityConfidence must be in [0,1]");
        }
    }

    public double wasteRatio() {
        return (double) discardedPerishables / purchasedPerishables;
    }

    public double ignoredRatio() {
        return advisorySuggestions == 0 ? 0 : (double) ignoredSuggestions / advisorySuggestions;
    }

    public Evidence withDiscardedPerishables(int correctedValue, String correctionProvenance) {
        return withCorrections(correctedValue, null, null, null, correctionProvenance);
    }

    public Evidence withCorrections(
            Integer correctedDiscardedPerishables,
            Double correctedSustainabilityConfidence,
            Boolean correctedExternalDisclosureConsent,
            Boolean correctedAutomaticListChangeConsent,
            String correctionProvenance
    ) {
        return new Evidence(
                purchasedPerishables,
                correctedDiscardedPerishables == null
                        ? discardedPerishables
                        : correctedDiscardedPerishables,
                advisorySuggestions,
                ignoredSuggestions,
                correctedSustainabilityConfidence == null
                        ? sustainabilityConfidence
                        : correctedSustainabilityConfidence,
                externalDisclosureAttempt,
                correctedExternalDisclosureConsent == null
                        ? externalDisclosureConsent
                        : correctedExternalDisclosureConsent,
                correctedAutomaticListChangeConsent == null
                        ? automaticListChangeConsent
                        : correctedAutomaticListChangeConsent,
                Instant.now(),
                correctionProvenance
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("purchasedPerishables", purchasedPerishables);
        map.put("discardedPerishables", discardedPerishables);
        map.put("wasteRatio", wasteRatio());
        map.put("advisorySuggestions", advisorySuggestions);
        map.put("ignoredSuggestions", ignoredSuggestions);
        map.put("ignoredRatio", ignoredRatio());
        map.put("sustainabilityConfidence", sustainabilityConfidence);
        map.put("externalDisclosureAttempt", externalDisclosureAttempt);
        map.put("externalDisclosureConsent", externalDisclosureConsent);
        map.put("automaticListChangeConsent", automaticListChangeConsent);
        map.put("observedAt", observedAt.toString());
        map.put("provenance", provenance);
        return map;
    }
}
