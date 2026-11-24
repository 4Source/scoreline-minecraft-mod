package io.github.foursource.scoreline.net;

import java.util.Optional;

/**
 * Decides on the client when a stats request is sent.
 * <p>
 * The client asks only after the server sent a hello, so a server without the mod never receives a request. While TAB is held, it asks once at the press and then again each time the interval
 * announced in the hello has passed since the last request. Releasing TAB resets this, so the next press asks at once.
 * <p>
 * The timer is polled every client tick rather than driven by key events: the repeats need a clock anyway, and polling cannot miss a release. It is used on the client thread only and is not
 * thread-safe.
 */
public class RequestTimer {
    /** The shortest interval the client accepts, so a broken server cannot make it ask every tick. */
    private static final long MIN_REFRESH_MILLIS = 1000L;

    /** The time between two requests while TAB is held, from the last valid hello. */
    private long refreshMillis;

    /** The protocol version of the last valid hello; empty while connected to a server without the mod. */
    private Optional<Integer> serverVersion = Optional.empty();

    /** The time of the last request sent; the next repeat is due one interval after it. */
    private long referenceMillis;

    /** Whether TAB was held at the previous poll, so a new press can be told apart from a held key. */
    private boolean previousTabState;

    /**
     * Creates a timer with no server known yet, so it allows no request until a hello arrives.
     */
    public RequestTimer() {}

    /**
     * Records the hello of a server that has the mod.
     * <p>
     * A later valid hello on the same connection replaces the earlier one. It is not a TAB press: the press state and the time of the last request are kept, so while TAB is held the next request is
     * due one new interval after the last one.
     * <p>
     * A hello with a protocol version below 1 is ignored and changes nothing, so it can neither enable requests nor replace the interval of an earlier valid hello. A version above
     * {@link Protocol#VERSION} is accepted: a newer server sends nothing unasked except the hello, and the client asks only for what it knows (spec, Networking, Mixed mod versions).
     *
     * @param protocolVersion the protocol version the server speaks; below 1 means the hello is ignored
     * @param refreshSeconds the interval between requests while TAB is held; below 1 counts as 1 second
     */
    public void onHello(int protocolVersion, int refreshSeconds) {
        if (protocolVersion > 0) {
            // 1000L makes the product a long: refreshSeconds * 1000 as int overflows above about 24 days and can turn negative
            this.refreshMillis = Math.max(MIN_REFRESH_MILLIS, refreshSeconds * 1000L);
            this.serverVersion = Optional.of(protocolVersion);
        }
    }

    /**
     * Forgets the server's hello and the TAB state, so the next server starts fresh: a server without the mod receives nothing, and the first press on a modded one asks at once.
     * <p>
     * Safe to call without a hello, which happens each time the player leaves a server without the mod.
     */
    public void onDisconnect() {
        this.serverVersion = Optional.empty();
        previousTabState = false;
    }

    /**
     * Decides whether a request is sent now. Called every client tick.
     *
     * @param tabHeld whether the player list key is held at this tick
     * @param nowMillis the current time in milliseconds; passed in so the logic stays testable
     * @return {@code true} if a hello was received, TAB is held, and either TAB was just pressed or a full interval has passed since the last request
     */
    public boolean shouldSend(boolean tabHeld, long nowMillis) {
        if (!tabHeld) {
            // A release ends the press, so the next press counts as new and asks at once
            previousTabState = tabHeld;
            return false;
        }

        if (serverVersion.isEmpty()) {
            // The press is not recorded without a hello, so TAB held while joining asks as soon as the hello arrives
            return false;
        }

        if (previousTabState != tabHeld) {
            // A new press: ask at once and count the interval from here
            previousTabState = tabHeld;
            referenceMillis = nowMillis;
            return true;
        }

        if (nowMillis - referenceMillis < refreshMillis) {
            return false;
        }

        // Counted from this request, not from the press, so repeats keep a full interval apart
        referenceMillis = nowMillis;
        return true;
    }
}
