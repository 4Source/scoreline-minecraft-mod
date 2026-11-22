package io.github.foursource.scoreline.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class RequestBudgetTest {
    private static final int CAP = 3;
    private static final long T = 1000;

    /**
     * Bug: budget starts empty
     */
    @Test
    void t01_NewBudgetAllowsRequest() {
        RequestBudget budget = new RequestBudget(CAP, T);

        assertTrue(budget.tryAcquire(0));
    }

    /**
     * Bug: off-by-one in the capacity check
     */
    @Test
    void t02_RequestBurstAllowCapacity() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Allowed request because of CAP=3
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Not allowed as more than CAP
        assertFalse(budget.tryAcquire(0));
    }

    /**
     * Bug: no refill, or refill of more than one token
     */
    @Test
    void t03_RefillOneTokenPerInterval() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(T));

        // Not allowed no more than CAP
        assertFalse(budget.tryAcquire(T));
    }

    /**
     * Bug: refill rounds up
     */
    @Test
    void t04_NoRefillBeforeFullInterval() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Not allowed before refill
        assertFalse(budget.tryAcquire(T - 1));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(T));
    }

    /**
     * Bug: tokens pile up beyond the capacity
     */
    @Test
    void t05_TokensDoNotPileUp() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(100 * T));
        assertTrue(budget.tryAcquire(100 * T));
        assertTrue(budget.tryAcquire(100 * T));

        // Not allowed no more than CAP
        assertFalse(budget.tryAcquire(100 * T));
    }

    /**
     * Bug: leftover time dropped at refill
     */
    @Test
    void t06_KeepRemainingTime() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Allowed request after refill
        assertTrue(budget.tryAcquire((long) (2.5 * T)));
        assertTrue(budget.tryAcquire((long) (2.5 * T)));

        // Not allowed as only 2 refilled
        assertFalse(budget.tryAcquire((long) (2.5 * T)));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(3 * T));
    }

    /**
     * Bug: negative elapsed time adds or removes tokens, or throws; reference time moved back to the earlier time
     */
    @Test
    void t07_ClockGoingBackwardsGivesNoTokens() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(5 * T));
        assertTrue(budget.tryAcquire(5 * T));
        assertTrue(budget.tryAcquire(5 * T));

        // Not allowed even when it was in the past
        assertFalse(budget.tryAcquire(4 * T));

        // Not allowed as not refilled yet
        assertFalse(budget.tryAcquire((long) (5.5 * T)));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(6 * T));

        // Not allowed no more than CAP
        assertFalse(budget.tryAcquire(6 * T));
    }

    /**
     * Bug: zero interval divides by zero; zero capacity never allows a request
     */
    @ParameterizedTest
    @CsvSource({ "0, 1000", // capacity 0
            "-1, 1000", // negative capacity
            "3, 0", // interval 0
            "3, -5" // negative interval
    })
    void t08_InvalidArgumentsAreRejected(int capacity, long interval) {

        assertThrows(IllegalArgumentException.class, () -> new RequestBudget(capacity, interval));
    }

    /**
     * Bug: smallest valid values rejected (< 2 instead of < 1); capacity or interval hard-coded
     */
    @Test
    void t15_SmallestValidAllowed() {
        int capacity = 1;
        long interval = 1;
        RequestBudget budget = assertDoesNotThrow(() -> new RequestBudget(capacity, interval));

        // Drain capacity
        assertTrue(budget.tryAcquire(0));

        // Not allowed before refill
        assertFalse(budget.tryAcquire(0));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(interval));

        // Not allowed no more than CAP
        assertFalse(budget.tryAcquire(interval));
    }

    /**
     * Bug: a refused request still uses a token (count goes negative) or restarts the refill timer
     */
    @Test
    void t16_RefusedRequestChangesNothing() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Not allowed before refill
        assertFalse(budget.tryAcquire(0));
        assertFalse(budget.tryAcquire(T / 2));

        // Allowed request after refill
        assertTrue(budget.tryAcquire(T));

        // Not allowed no more than CAP
        assertFalse(budget.tryAcquire(T));
    }

    /**
     * Bug: refill replaces the remaining tokens instead of adding to them
     */
    @Test
    void t17_RefillAddsToRemainingTokens() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Use one token, CAP - 1 left
        assertTrue(budget.tryAcquire(0));

        // Drain capacity
        assertTrue(budget.tryAcquire(T));
        assertTrue(budget.tryAcquire(T));
        assertTrue(budget.tryAcquire(T));

        // Not allowed before refill
        assertFalse(budget.tryAcquire(T));
    }

    /**
     * Bug: time beyond the capacity is saved up and paid out later (reference time advanced only by the tokens added, not by the intervals passed)
     */
    @Test
    void t18_IdleTimeWhileFullIsNotSaved() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Use one token, CAP - 1 left
        assertTrue(budget.tryAcquire(0));

        // Drain capacity
        assertTrue(budget.tryAcquire(10 * T));
        assertTrue(budget.tryAcquire(10 * T));
        assertTrue(budget.tryAcquire(10 * T));

        // Not allowed before refill
        assertFalse(budget.tryAcquire(10 * T));
    }

    /**
     * Bug: reference time fixed at 0 instead of the first request, so a clock with negative values never refills
     */
    @Test
    void t19_FirstRequestSetsReferenceTime() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Drain capacity
        assertTrue(budget.tryAcquire(-5 * T));
        assertTrue(budget.tryAcquire(-5 * T));
        assertTrue(budget.tryAcquire(-5 * T));

        // Allowed request
        assertTrue(budget.tryAcquire(-4 * T));

        // Not allowed before another refill
        assertFalse(budget.tryAcquire(-4 * T));
    }

    /**
     * Bug: refill count cast to int overflows and turns negative
     */
    @Test
    void t20_VeryLongWaitDoesNotOverflow() {
        RequestBudget budget = new RequestBudget(CAP, 1);

        // Drain capacity
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));
        assertTrue(budget.tryAcquire(0));

        // Allowed requests
        assertTrue(budget.tryAcquire(3_000_000_000L));
        assertTrue(budget.tryAcquire(3_000_000_000L));
        assertTrue(budget.tryAcquire(3_000_000_000L));

        // Not allowed before another refill
        assertFalse(budget.tryAcquire(3_000_000_000L));
    }

    /**
     * Bug: going back in time removes tokens from a budget that is not empty (T-07 misses this, because a drained budget hides it)
     */
    @Test
    void t21_GoingBackKeepsRemainingTokens() {
        RequestBudget budget = new RequestBudget(CAP, T);

        // Allowed request
        assertTrue(budget.tryAcquire(5 * T));

        // Allowed request
        assertTrue(budget.tryAcquire(4 * T));
        assertTrue(budget.tryAcquire(4 * T));

        // Not allowed before another refill
        assertFalse(budget.tryAcquire(4 * T));
    }

    /**
     * Bug: state shared between instances, so one players requests use up everyone's budget
     */
    @Test
    void t22_RequestBurstAllowCapacity() {
        RequestBudget budget1 = new RequestBudget(CAP, T);
        RequestBudget budget2 = new RequestBudget(CAP, T);

        // Allowed request because for budget1 of CAP=3
        assertTrue(budget1.tryAcquire(0));
        assertTrue(budget1.tryAcquire(0));
        assertTrue(budget1.tryAcquire(0));

        // Not allowed as more than budget1 CAP
        assertFalse(budget1.tryAcquire(0));

        // Allowed request because for budget2 of CAP=3
        assertTrue(budget2.tryAcquire(0));
        assertTrue(budget2.tryAcquire(0));
        assertTrue(budget2.tryAcquire(0));

        // Not allowed as more than budget2 CAP
        assertFalse(budget2.tryAcquire(0));
    }
}
