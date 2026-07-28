package org.ease.mvp.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record EthicalConstraint(
        String id,
        String principle,
        ConstraintKind kind,
        String stakeholderId,
        String authority,
        String target,
        String applicability,
        int precedence,
        double weight,
        boolean contestable,
        String violationHandling,
        String revisionPolicy,
        String version
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("principle", principle);
        map.put("kind", kind.name());
        map.put("stakeholderId", stakeholderId);
        map.put("authority", authority);
        map.put("target", target);
        map.put("applicability", applicability);
        map.put("precedence", precedence);
        map.put("weight", weight);
        map.put("contestable", contestable);
        map.put("violationHandling", violationHandling);
        map.put("revisionPolicy", revisionPolicy);
        map.put("version", version);
        return map;
    }
}
