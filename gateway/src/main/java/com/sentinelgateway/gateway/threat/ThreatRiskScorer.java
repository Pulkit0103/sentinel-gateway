package com.sentinelgateway.gateway.threat;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Computes the overall {@link ThreatAction} from a set of detected signals.
 *
 * The total score is the sum of all individual signal scores.  Scores are
 * additive so multiple patterns in a single request raise the risk level.
 * Thresholds are read from {@link ThreatDetectionProperties}.
 */
@Component
public class ThreatRiskScorer {

    private final ThreatDetectionProperties properties;

    public ThreatRiskScorer(ThreatDetectionProperties properties) {
        this.properties = properties;
    }

    public int totalScore(List<ThreatSignal> signals) {
        return signals.stream().mapToInt(ThreatSignal::score).sum();
    }

    public ThreatAction determineAction(int score) {
        if (score >= properties.getAlertThreshold()) return ThreatAction.BLOCK_AND_ALERT;
        if (score >= properties.getBlockThreshold())  return ThreatAction.BLOCK;
        if (score >= properties.getLogThreshold())    return ThreatAction.LOG;
        return ThreatAction.ALLOW;
    }
}
