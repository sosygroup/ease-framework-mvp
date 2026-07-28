package org.ease.mvp.mapek.analyse;

import java.util.List;

public record AnalysisResult(List<MismatchIndicator> indicators, GovernanceState governance) {
    public AnalysisResult {
        indicators = List.copyOf(indicators);
    }
}
