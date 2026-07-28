package org.ease.mvp.mapek.monitor;

import org.ease.mvp.bdi.belief.BeliefBase;
import org.ease.mvp.domain.Evidence;

public record MonitoringSnapshot(Evidence evidence, BeliefBase beliefs) {
}
