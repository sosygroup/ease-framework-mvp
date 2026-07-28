package org.ease.mvp.mapek.knowledge;

import org.ease.mvp.bdi.intention.CandidateIntention;
import org.ease.mvp.domain.AutonomyMode;
import org.ease.mvp.domain.EthicalConstraint;
import org.ease.mvp.domain.Evidence;
import org.ease.mvp.domain.Stakeholder;
import org.ease.mvp.domain.ThresholdPolicy;
import org.ease.mvp.llm.model.LlmCognitiveUpdate;
import org.ease.mvp.mapek.analyse.GovernanceState;
import org.ease.mvp.mapek.analyse.MismatchIndicator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DecisionTrace(
        String id,
        Instant createdAt,
        long knowledgeVersionBefore,
        long knowledgeVersionAfter,
        String phase,
        Evidence triggeringEvidence,
        List<Stakeholder> activeStakeholders,
        String context,
        List<EthicalConstraint> activeConstraints,
        List<String> beliefs,
        List<String> desires,
        LlmCognitiveUpdate cognitiveUpdate,
        List<MismatchIndicator> mismatchIndicators,
        GovernanceState governance,
        ThresholdPolicy thresholds,
        List<CandidateIntention> candidateIntentions,
        String selectedIntentionId,
        AutonomyMode selectedMode,
        String leastIntrusiveRationale,
        List<String> uncertainty,
        String executionStatus,
        List<String> contestationRoutes,
        String revisesTraceId,
        Contestation contestation
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("createdAt", createdAt.toString());
        map.put("knowledgeVersionBefore", knowledgeVersionBefore);
        map.put("knowledgeVersionAfter", knowledgeVersionAfter);
        map.put("phase", phase);
        map.put("triggeringEvidence", triggeringEvidence.toMap());
        map.put("activeStakeholders", activeStakeholders.stream().map(Stakeholder::toMap).toList());
        map.put("context", context);
        map.put("activeConstraints", activeConstraints.stream().map(EthicalConstraint::toMap).toList());
        map.put("beliefs", beliefs);
        map.put("desires", desires);
        map.put("cognitiveUpdate", cognitiveUpdate == null ? null : cognitiveUpdate.toMap());
        map.put("mismatchIndicators", mismatchIndicators.stream().map(MismatchIndicator::toMap).toList());
        map.put("governance", governance.toMap());
        map.put("thresholds", thresholds.toMap());
        map.put("candidateIntentions", candidateIntentions.stream().map(CandidateIntention::toMap).toList());
        map.put("selectedIntentionId", selectedIntentionId);
        map.put("selectedMode", selectedMode == null ? null : selectedMode.name());
        map.put("leastIntrusiveRationale", leastIntrusiveRationale);
        map.put("uncertainty", uncertainty);
        map.put("executionStatus", executionStatus);
        map.put("contestationRoutes", contestationRoutes);
        map.put("revisesTraceId", revisesTraceId);
        map.put("contestation", contestation == null ? null : contestation.toMap());
        return map;
    }
}
