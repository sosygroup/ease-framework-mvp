package org.ease.mvp.bdi.desire;

import java.util.List;

public record DesireSet(List<String> statements) {
    public DesireSet {
        statements = List.copyOf(statements);
    }
}
