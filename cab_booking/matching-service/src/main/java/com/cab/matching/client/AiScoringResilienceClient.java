package com.cab.matching.client;

import feign.RetryableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiScoringResilienceClient {

    private final AiScoringClient aiScoringClient;

    @CircuitBreaker(name = "aiScoringService", fallbackMethod = "ruleBasedFallback")
    @Retry(name = "aiScoringService")
    @Bulkhead(name = "aiScoringService")
    public List<RankingEntry> rankCandidates(List<DriverFeatureDto> candidates, String rideId) {
        log.info("Calling AI scoring | candidates={} | rideId={}", candidates.size(), rideId);
        AiScoringResponse response = aiScoringClient.getBestMatch(candidates);
        log.info("AI suggested bestDriver={} | score={} | rideId={}",
                response.getBestDriverId(), response.getHighestScore(), rideId);
        return response.getRanking() != null ? response.getRanking() : List.of();
    }

    public List<RankingEntry> ruleBasedFallback(
            List<DriverFeatureDto> candidates,
            String rideId,
            Throwable ex) {
        if (ex instanceof RetryableException) {
            log.warn("AI scoring unavailable after resilience handling, using rule-based fallback | rideId={} | reason={}",
                    rideId, ex.getMessage());
        } else {
            log.warn("AI scoring failed with non-retryable error, using rule-based fallback | rideId={} | reason={}",
                    rideId, ex.getMessage());
        }

        List<DriverFeatureDto> sortedCandidates = new ArrayList<>(candidates);
        sortedCandidates.sort(Comparator
                .comparingDouble(DriverFeatureDto::getDistance)
                .thenComparing(Comparator.comparingDouble(DriverFeatureDto::getRating).reversed()));

        List<RankingEntry> fallbackRanking = new ArrayList<>();
        for (DriverFeatureDto candidate : sortedCandidates) {
            double score = Math.max(0.0, 100.0 - (candidate.getDistance() * 10.0))
                    + Math.min(5.0, candidate.getRating());
            fallbackRanking.add(RankingEntry.builder()
                    .driverId(candidate.getDriverId())
                    .score(score)
                    .details("fallback-distance-rating")
                    .build());
        }
        return fallbackRanking;
    }
}
