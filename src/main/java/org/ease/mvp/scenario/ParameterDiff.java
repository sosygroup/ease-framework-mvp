package org.ease.mvp.scenario;

import org.ease.mvp.support.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ParameterDiff {
    private ParameterDiff() {
    }

    static Map<String, Object> between(
            Map<String, Object> referenceConfiguration,
            Map<String, Object> scenarioConfiguration,
            Map<String, Object> referenceEvidence,
            Map<String, Object> scenarioEvidence,
            Map<String, Object> correctedEvidence
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        compare(
                "configuration",
                referenceConfiguration,
                scenarioConfiguration,
                result
        );
        compare("evidence", referenceEvidence, scenarioEvidence, result);
        if (correctedEvidence != null) {
            compare("correction", scenarioEvidence, correctedEvidence, result);
        }
        return result;
    }

    private static void compare(
            String path,
            Object reference,
            Object actual,
            Map<String, Object> differences
    ) {
        if (reference instanceof Map<?, ?> referenceMap
                && actual instanceof Map<?, ?> actualMap) {
            Set<String> keys = new LinkedHashSet<>();
            referenceMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            actualMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for (String key : keys) {
                compare(
                        path + "." + key,
                        referenceMap.get(key),
                        actualMap.get(key),
                        differences
                );
            }
            return;
        }
        if (reference instanceof List<?> referenceList && actual instanceof List<?> actualList) {
            Map<String, Object> referenceById = byId(referenceList);
            Map<String, Object> actualById = byId(actualList);
            if (referenceById != null && actualById != null) {
                Set<String> ids = new LinkedHashSet<>(referenceById.keySet());
                ids.addAll(actualById.keySet());
                for (String id : ids) {
                    compare(
                            path + "[" + id + "]",
                            referenceById.get(id),
                            actualById.get(id),
                            differences
                    );
                }
                return;
            }
        }
        if (!equivalent(reference, actual)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("reference", reference);
            change.put("value", actual);
            differences.put(path, change);
        }
    }

    private static Map<String, Object> byId(List<?> list) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> item) || item.get("id") == null) return null;
            result.put(String.valueOf(item.get("id")), value);
        }
        return result;
    }

    private static boolean equivalent(Object first, Object second) {
        if (first instanceof Number a && second instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        if (first == null || second == null) return first == second;
        if (first.equals(second)) return true;
        if (first instanceof List<?> || first instanceof Map<?, ?>
                || second instanceof List<?> || second instanceof Map<?, ?>) {
            return Json.stringify(first).equals(Json.stringify(second));
        }
        return false;
    }
}
