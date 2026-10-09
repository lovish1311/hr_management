package com.example.hr_management_backend.features.scribbil;

import com.example.hr_management_backend.features.scribbil.service.DrawGuessScoringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DrawGuessScoringEngineTest {

    private DrawGuessScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        scoringEngine = new DrawGuessScoringEngine();
    }

    @Test
    @DisplayName("Should match all required checkpoints for configured 80s round")
    void testRequiredCheckpoints80s() {
        double duration = 80.0;

        // At round start: 650 points
        assertEquals(650, scoringEngine.calculateGuessScore(0.0, duration));

        // At 12.5% of elapsed round time (10.0s): approximately 400 points
        assertEquals(400, scoringEngine.calculateGuessScore(10.0, duration));

        // At 25% elapsed (20.0s): approximately 228 points
        assertEquals(228, scoringEngine.calculateGuessScore(20.0, duration));

        // At 50% elapsed (40.0s): approximately 52 points
        assertEquals(52, scoringEngine.calculateGuessScore(40.0, duration));

        // At 75% elapsed (60.0s): approximately 4 points
        assertEquals(4, scoringEngine.calculateGuessScore(60.0, duration));

        // At round end (80.0s): 0 points
        assertEquals(0, scoringEngine.calculateGuessScore(80.0, duration));
    }

    @Test
    @DisplayName("Should demonstrate 250ms precision and decreasing scores")
    void testQuarterSecondPrecision() {
        double duration = 80.0;

        int score700 = scoringEngine.calculateGuessScore(7.00, duration);
        int score725 = scoringEngine.calculateGuessScore(7.25, duration);
        int score750 = scoringEngine.calculateGuessScore(7.50, duration);

        // 7.00s > 7.25s > 7.50s
        assertTrue(score700 > score725, "Score at 7.00s should be strictly greater than 7.25s");
        assertTrue(score725 > score750, "Score at 7.25s should be strictly greater than 7.50s");

        assertEquals(466, score700);
        assertEquals(460, score725);
        assertEquals(454, score750);

        // Sub-250ms times must map to the same quantized bucket
        assertEquals(score700, scoringEngine.calculateGuessScore(7.10, duration));
        assertEquals(score700, scoringEngine.calculateGuessScore(7.24, duration));
        assertEquals(score725, scoringEngine.calculateGuessScore(7.49, duration));
    }

    @Test
    @DisplayName("Should guarantee monotonic non-increasing scores across full duration")
    void testMonotonicity() {
        double duration = 80.0;
        int previousScore = DrawGuessScoringEngine.MAX_GUESS_SCORE;

        for (int ms = 0; ms <= 80000; ms += 250) {
            double elapsedSec = ms / 1000.0;
            int score = scoringEngine.calculateGuessScore(elapsedSec, duration);

            assertTrue(score <= previousScore,
                    String.format("Monotonicity violation at %d ms: score %d > previous %d", ms, score, previousScore));
            assertTrue(score >= 0 && score <= 650,
                    String.format("Score out of bounds at %d ms: %d", ms, score));

            previousScore = score;
        }

        assertEquals(0, previousScore);
    }

    @Test
    @DisplayName("Should scale proportionally for any configured round duration")
    void testVariableDurations() {
        // Test 60s duration
        assertEquals(650, scoringEngine.calculateGuessScore(0.0, 60.0));
        assertEquals(400, scoringEngine.calculateGuessScore(7.5, 60.0));  // 12.5%
        assertEquals(228, scoringEngine.calculateGuessScore(15.0, 60.0)); // 25.0%
        assertEquals(52, scoringEngine.calculateGuessScore(30.0, 60.0));  // 50.0%
        assertEquals(4, scoringEngine.calculateGuessScore(45.0, 60.0));   // 75.0%
        assertEquals(0, scoringEngine.calculateGuessScore(60.0, 60.0));

        // Test 120s duration
        assertEquals(650, scoringEngine.calculateGuessScore(0.0, 120.0));
        assertEquals(400, scoringEngine.calculateGuessScore(15.0, 120.0)); // 12.5%
        assertEquals(228, scoringEngine.calculateGuessScore(30.0, 120.0)); // 25.0%
        assertEquals(52, scoringEngine.calculateGuessScore(60.0, 120.0));  // 50.0%
        assertEquals(4, scoringEngine.calculateGuessScore(90.0, 120.0));   // 75.0%
        assertEquals(0, scoringEngine.calculateGuessScore(120.0, 120.0));
    }

    @Test
    @DisplayName("Should clamp correctly on out-of-bound inputs")
    void testBoundaryClamping() {
        double duration = 80.0;

        assertEquals(650, scoringEngine.calculateGuessScore(-5.0, duration));
        assertEquals(0, scoringEngine.calculateGuessScore(85.0, duration));
        assertEquals(0, scoringEngine.calculateGuessScore(1000.0, duration));
    }

    @Test
    @DisplayName("Should calculate drawer score as 50% of total guesser scores")
    void testDrawerScoring() {
        List<Integer> guessScores = List.of(466, 400, 228); // sum = 1094
        int drawerScore = scoringEngine.calculateDrawerScore(guessScores);
        assertEquals(547, drawerScore); // 1094 * 0.50 = 547

        // Empty or 0 correct guesses
        assertEquals(0, scoringEngine.calculateDrawerScore(List.of()));
        assertEquals(0, scoringEngine.calculateDrawerScore(null));
    }
}
