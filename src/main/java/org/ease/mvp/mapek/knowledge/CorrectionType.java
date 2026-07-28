package org.ease.mvp.mapek.knowledge;

public enum CorrectionType {
    HISTORICAL_FACT_CORRECTION,
    PROSPECTIVE_CONSENT_CHANGE;

    public static CorrectionType parse(String value) {
        if (value == null || value.isBlank()) return HISTORICAL_FACT_CORRECTION;
        try {
            return valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "correctionType must be HISTORICAL_FACT_CORRECTION or PROSPECTIVE_CONSENT_CHANGE"
            );
        }
    }
}
