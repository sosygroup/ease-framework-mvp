package org.ease.mvp.mapek.monitor;

import org.ease.mvp.bdi.belief.BeliefRevision;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.Evidence;

public final class EvidenceMonitor {
    private final BeliefRevision beliefRevision;

    public EvidenceMonitor(BeliefRevision beliefRevision) {
        this.beliefRevision = beliefRevision;
    }

    public MonitoringSnapshot monitor(Evidence evidence, AutonomyMode currentMode) {
        // Evidence has already passed the invariants enforced by its record constructor.
        return new MonitoringSnapshot(evidence, beliefRevision.revise(evidence, currentMode));
    }
}
