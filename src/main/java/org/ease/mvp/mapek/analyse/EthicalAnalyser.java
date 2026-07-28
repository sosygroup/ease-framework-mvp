package org.ease.mvp.mapek.analyse;

import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.domain.ThresholdPolicy;
import org.ease.mvp.mapek.monitor.MonitoringSnapshot;

import java.util.List;

public final class EthicalAnalyser {
    private final double sustainabilityTarget;
    private final double fullDeviationWasteRatio;
    private final double sustainabilityWeight;
    private final double autonomyWeight;
    private final ThresholdPolicy thresholds;
    private final String evaluatorVersion;

    public EthicalAnalyser(
            double sustainabilityTarget,
            double fullDeviationWasteRatio,
            double sustainabilityWeight,
            double autonomyWeight,
            ThresholdPolicy thresholds,
            String evaluatorVersion
    ) {
        this.sustainabilityTarget = sustainabilityTarget;
        this.fullDeviationWasteRatio = fullDeviationWasteRatio;
        this.sustainabilityWeight = sustainabilityWeight;
        this.autonomyWeight = autonomyWeight;
        this.thresholds = thresholds;
        this.evaluatorVersion = evaluatorVersion;
    }

    public AnalysisResult analyse(MonitoringSnapshot monitoring, AutonomyMode currentMode) {
        List<MismatchIndicator> indicators = mismatchIndicators(monitoring.evidence(), currentMode);
        return new AnalysisResult(indicators, aggregate(indicators));
    }

    private List<MismatchIndicator> mismatchIndicators(Evidence evidence, AutonomyMode currentMode) {
        boolean privacyViolation = evidence.externalDisclosureAttempt() && !evidence.externalDisclosureConsent();
        MismatchIndicator privacy = new MismatchIndicator(
                "Cprivacy", privacyViolation ? 1 : 0, 1, privacyViolation ? 1 : 0, 1,
                privacyViolation, List.of("data-egress event", "consent registry"), "privacy-gate-v1"
        );

        double deviation = clamp((evidence.wasteRatio() - sustainabilityTarget)
                / (fullDeviationWasteRatio - sustainabilityTarget));
        double severity = evidence.advisorySuggestions() == 0
                ? clamp(evidence.wasteRatio() / fullDeviationWasteRatio)
                : clamp(evidence.ignoredRatio());
        MismatchIndicator sustainability = new MismatchIndicator(
                "Csustainability", deviation, severity, deviation * severity,
                evidence.sustainabilityConfidence(), false,
                List.of("inventory removals", "bin sensor observations", "advisory interaction history"),
                evaluatorVersion
        );

        boolean autonomyMismatch = currentMode.intrusionRank() >= AutonomyMode.DELEGATED.intrusionRank()
                && !evidence.automaticListChangeConsent();
        MismatchIndicator autonomy = new MismatchIndicator(
                "Cautonomy", autonomyMismatch ? 1 : 0, autonomyMismatch ? 1 : 0,
                autonomyMismatch ? 1 : 0, 1, false,
                List.of("autonomy-mode controller", "consent registry"), "autonomy-consent-v1"
        );
        return List.of(privacy, sustainability, autonomy);
    }

    private GovernanceState aggregate(List<MismatchIndicator> indicators) {
        List<String> hardViolations = indicators.stream()
                .filter(MismatchIndicator::hardViolation)
                .map(MismatchIndicator::constraintId)
                .toList();
        MismatchIndicator sustainability = indicator(indicators, "Csustainability");
        MismatchIndicator autonomy = indicator(indicators, "Cautonomy");
        double denominator = sustainabilityWeight + autonomyWeight;
        double aggregateMismatch = (sustainabilityWeight * sustainability.mismatch()
                + autonomyWeight * autonomy.mismatch()) / denominator;
        double aggregateConfidence = (sustainabilityWeight * sustainability.confidence()
                + autonomyWeight * autonomy.confidence()) / denominator;
        boolean review = hardViolations.isEmpty() && aggregateConfidence < thresholds.minimumConfidence();
        AutonomyMode maximum = hardViolations.isEmpty()
                ? thresholds.maximumModeFor(aggregateMismatch)
                : AutonomyMode.RESTRICTIVE;
        String region = hardViolations.isEmpty()
                ? thresholds.regionFor(aggregateMismatch)
                : "HARD_CONSTRAINT_RESPONSE";
        if (review) region = "INSUFFICIENT_CONFIDENCE_HUMAN_REVIEW";
        return new GovernanceState(
                hardViolations, aggregateMismatch, aggregateConfidence, region,
                maximum, review, thresholds.version()
        );
    }

    private MismatchIndicator indicator(List<MismatchIndicator> indicators, String id) {
        return indicators.stream()
                .filter(indicator -> indicator.constraintId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
