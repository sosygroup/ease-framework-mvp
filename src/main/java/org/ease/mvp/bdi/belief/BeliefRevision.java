package org.ease.mvp.bdi.belief;

import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;

import java.util.List;

public final class BeliefRevision {
    private final double sustainabilityTarget;

    public BeliefRevision(double sustainabilityTarget) {
        this.sustainabilityTarget = sustainabilityTarget;
    }

    public BeliefBase revise(Evidence evidence, AutonomyMode currentMode) {
        return new BeliefBase(List.of(
                "b1: observed avoidable-waste ratio is " + format(evidence.wasteRatio())
                        + " against target " + format(sustainabilityTarget),
                "b2: " + evidence.ignoredSuggestions() + " of " + evidence.advisorySuggestions()
                        + " recent advisory suggestions were rejected or ignored",
                "b3: current autonomy mode is " + currentMode.name(),
                "b4: automatic shopping-list modification consent is "
                        + (evidence.automaticListChangeConsent() ? "present" : "absent"),
                "b5: unauthorised external disclosure attempt is "
                        + (evidence.externalDisclosureAttempt() && !evidence.externalDisclosureConsent()
                        ? "present" : "absent")
        ));
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }
}
