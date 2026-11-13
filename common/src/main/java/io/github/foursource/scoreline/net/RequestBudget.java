package io.github.foursource.scoreline.net;

/**
 * Limits how often one player's requests are answered: a burst of up to {@code capacity} requests, then one more per refill interval.
 * <p>
 * One token returns per full interval, never more than {@code capacity}. Time left over after a refill counts towards the next token. The first call sets the reference time, and a time earlier than
 * the latest one seen counts as no time passed, so any clock works, including one that starts below zero or jumps backwards.
 * <p>
 * The caller passes the current time in; the class never reads a clock. Not thread-safe: all calls for one budget must come from the same thread.
 */
public final class RequestBudget {
    private final int capacity;
    private int tokens;
    private final long refillIntervalMillis;
    private long referenceMillis;
    private boolean started;

    /**
     * Creates a full budget.
     *
     * @param capacity             the most requests allowed in a burst; at least 1
     * @param refillIntervalMillis the time for one token to return; at least 1
     * @throws IllegalArgumentException if either value is below 1
     */
    public RequestBudget(int capacity, long refillIntervalMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be at least 1, was " + capacity);
        }
        if (refillIntervalMillis <= 0) {
            throw new IllegalArgumentException("refillIntervalMillis must be at least 1, was " + refillIntervalMillis);
        }

        this.capacity = capacity;
        this.tokens = capacity;
        this.refillIntervalMillis = refillIntervalMillis;
        this.started = false;
    }

    /**
     * Takes one token if one is available.
     *
     * @param nowMillis the current time from the caller's clock, in milliseconds
     * @return {@code true} if the request may be answered, {@code false} if the budget is used up; a refused request changes nothing
     */
    public boolean tryAcquire(long nowMillis) {
        // The first call sets the reference time; the budget starts full, so there is nothing to refill
        if (!started) {
            referenceMillis = nowMillis;
            started = true;
        }

        // Full intervals since the reference time; the division rounds down, so a partial interval gives nothing yet
        long potentialRefills = (nowMillis - referenceMillis) / refillIntervalMillis;

        // Zero or negative when no full interval has passed or the clock went backwards; both mean no time passed
        if (potentialRefills > 0) {
            tokens = (int) Math.min(tokens + potentialRefills, capacity);

            // Advance by the full intervals used; the remainder counts towards the next
            // token; intervals beyond the capacity are used up too, so they are not paid out later
            referenceMillis += potentialRefills * refillIntervalMillis;
        }

        if (tokens > 0) {
            tokens--;
            return true;
        }

        return false;
    }
}
