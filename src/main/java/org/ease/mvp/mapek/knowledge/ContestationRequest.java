package org.ease.mvp.mapek.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

public record ContestationRequest(
        String stakeholderId,
        CorrectionType correctionType,
        Integer correctedDiscardedPerishables,
        Double correctedConfidence,
        Boolean correctedExternalDisclosureConsent,
        Boolean correctedAutomaticListChangeConsent,
        String reason
) {
    public ContestationRequest {
        if (stakeholderId == null || stakeholderId.isBlank()) {
            throw new IllegalArgumentException("Contestation stakeholderId is required");
        }
        stakeholderId = stakeholderId.strip();
        correctionType = correctionType == null
                ? CorrectionType.HISTORICAL_FACT_CORRECTION
                : correctionType;
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Contestation reason is required");
        }
        reason = reason.strip();
        if (reason.length() > 1_000) {
            throw new IllegalArgumentException("Contestation reason must not exceed 1000 characters");
        }
        if (correctedDiscardedPerishables == null
                && correctedConfidence == null
                && correctedExternalDisclosureConsent == null
                && correctedAutomaticListChangeConsent == null) {
            throw new IllegalArgumentException("At least one corrected value is required");
        }
        if (correctedDiscardedPerishables != null && correctedDiscardedPerishables < 0) {
            throw new IllegalArgumentException("Corrected discarded perishables cannot be negative");
        }
        if (correctedConfidence != null
                && (!Double.isFinite(correctedConfidence)
                || correctedConfidence < 0
                || correctedConfidence > 1)) {
            throw new IllegalArgumentException("Corrected confidence must be in [0,1]");
        }
        if (correctionType == CorrectionType.PROSPECTIVE_CONSENT_CHANGE
                && (correctedDiscardedPerishables != null || correctedConfidence != null)) {
            throw new IllegalArgumentException(
                    "A prospective consent change can modify consent values only"
            );
        }
    }

    public Map<String, Object> correctedValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (correctedDiscardedPerishables != null) {
            map.put("discardedPerishables", correctedDiscardedPerishables);
        }
        if (correctedConfidence != null) {
            map.put("sustainabilityConfidence", correctedConfidence);
        }
        if (correctedExternalDisclosureConsent != null) {
            map.put("externalDisclosureConsent", correctedExternalDisclosureConsent);
        }
        if (correctedAutomaticListChangeConsent != null) {
            map.put("automaticListChangeConsent", correctedAutomaticListChangeConsent);
        }
        return map;
    }
}
