package io.github.foursource.scoreline.net;

/**
 * The fixed values of the Scoreline network protocol, shared by client and server.
 */
public final class Protocol {
    private Protocol() {}

    /** The protocol version this code speaks; sent in the hello. Raised when the wire format changes incompatibly. */
    public static final int VERSION = 1;

    /** Seconds between two requests while TAB is held; sent in the hello. Fixed in v0.2, set from a config file later. */
    public static final int REFRESH_SECONDS = 2;

    /** Requests one player may send in a burst before the server stops answering; see {@link RequestBudget}. */
    public static final int BUDGET_CAPACITY = 5;

    /** Milliseconds until the budget gets one request back. */
    public static final long BUDGET_REFILL_MILLIS = 1000;

    /** Most stat IDs one request may carry; bounds what a client can make the server decode. */
    public static final int MAX_STAT_IDS = 16;

    /** Most characters one stat ID may have; bounds what a client can make the server decode. */
    public static final int MAX_STAT_ID_LENGTH = 256;

    /** Vanilla's stat name for play time, in ticks (20 per second). */
    public static final String PLAY_TIME = "minecraft.custom:minecraft.play_time";

    /** Vanilla's stat name for the number of deaths. */
    public static final String DEATHS = "minecraft.custom:minecraft.deaths";
}
