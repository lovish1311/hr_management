package com.example.hr_management_backend.features.scribbil.service;

import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Continuous Nonlinear Decay Scoring Engine for Draw & Guess (Scribbil).
 *
 * Implements the mathematical specification from docs/game_of_scribbil (lines 287-620):
 * - MAX_GUESS_SCORE = 650
 * - SCORING_INTERVAL = 250 milliseconds (0.25 seconds)
 * - DECAY_EXPONENT = 3.6359076786
 * - MIN_SCORE = 0
 * - Quantized Elapsed Time: floor(elapsedTime / 0.25) * 0.25
 * - remainingRatio = clamp((ROUND_DURATION - quantizedElapsedTime) / ROUND_DURATION, 0.0, 1.0)
 * - rawScore = MAX_GUESS_SCORE * pow(remainingRatio, DECAY_EXPONENT)
 * - guessScore = clamp(round(rawScore), MIN_SCORE, MAX_GUESS_SCORE)
 * - Drawer Score: round(sum(all correct guess scores) * DRAWER_SHARE)
 */
@Component
public class DrawGuessScoringEngine {

    public static final int MAX_GUESS_SCORE = 650;
    public static final int MIN_SCORE = 0;
    public static final long SCORING_INTERVAL_MS = 250L;
    public static final double DECAY_EXPONENT = 3.6359076786;
    public static final double DEFAULT_DRAWER_SHARE = 0.50;

    /**
     * Calculates the server-authoritative score for a correct guess.
     *
     * @param elapsedSeconds       authoritative elapsed time in seconds since drawing started
     * @param roundDurationSeconds configured total duration of the drawing round in seconds
     * @return calculated score clamped between 0 and 650
     */
    public int calculateGuessScore(double elapsedSeconds, double roundDurationSeconds) {
        if (roundDurationSeconds <= 0.0) {
            return MIN_SCORE;
        }

        // Clamp elapsed time to valid bounds [0.0, roundDurationSeconds]
        if (elapsedSeconds <= 0.0) {
            return MAX_GUESS_SCORE;
        }
        if (elapsedSeconds >= roundDurationSeconds) {
            return MIN_SCORE;
        }

        // 250 ms (0.25s) quantization: floor(elapsed / 0.25) * 0.25
        // Using integer millisecond math to avoid floating point division precision drift
        long elapsedMs = Math.round(elapsedSeconds * 1000.0);
        long quantizedElapsedMs = (elapsedMs / SCORING_INTERVAL_MS) * SCORING_INTERVAL_MS;
        double quantizedElapsedSeconds = quantizedElapsedMs / 1000.0;

        if (quantizedElapsedSeconds >= roundDurationSeconds) {
            return MIN_SCORE;
        }

        double remainingRatio = (roundDurationSeconds - quantizedElapsedSeconds) / roundDurationSeconds;
        remainingRatio = Math.max(0.0, Math.min(1.0, remainingRatio));

        double rawScore = MAX_GUESS_SCORE * Math.pow(remainingRatio, DECAY_EXPONENT);
        long roundedScore = Math.round(rawScore);

        return (int) Math.max(MIN_SCORE, Math.min(MAX_GUESS_SCORE, roundedScore));
    }

    /**
     * Quantizes raw elapsed milliseconds to the nearest 250ms boundary.
     *
     * @param elapsedMilliseconds raw elapsed time in milliseconds
     * @return quantized elapsed milliseconds
     */
    public long quantizeElapsedMilliseconds(long elapsedMilliseconds) {
        if (elapsedMilliseconds <= 0L) {
            return 0L;
        }
        return (elapsedMilliseconds / SCORING_INTERVAL_MS) * SCORING_INTERVAL_MS;
    }

    /**
     * Calculates the drawer's score based on the sum of all correct guesser scores.
     *
     * @param guesserScores collection of all individual correct guess scores awarded in the round
     * @param drawerShare   configurable share factor (e.g. 0.50)
     * @return rounded drawer score
     */
    public int calculateDrawerScore(Collection<Integer> guesserScores, double drawerShare) {
        if (guesserScores == null || guesserScores.isEmpty()) {
            return 0;
        }
        long totalGuesserScore = 0L;
        for (Integer score : guesserScores) {
            if (score != null && score > 0) {
                totalGuesserScore += score;
            }
        }
        double rawDrawerScore = totalGuesserScore * drawerShare;
        return (int) Math.round(rawDrawerScore);
    }

    /**
     * Convenience method using the default 50% drawer share.
     */
    public int calculateDrawerScore(Collection<Integer> guesserScores) {
        return calculateDrawerScore(guesserScores, DEFAULT_DRAWER_SHARE);
    }
}
