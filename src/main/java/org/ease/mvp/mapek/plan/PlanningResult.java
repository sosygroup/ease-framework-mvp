package org.ease.mvp.mapek.plan;

import org.ease.mvp.bdi.deliberation.DeliberationResult;
import org.ease.mvp.bdi.desire.DesireSet;

public record PlanningResult(DesireSet desires, DeliberationResult deliberation) {
}
