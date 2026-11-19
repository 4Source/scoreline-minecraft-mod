package io.github.foursource.scoreline.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RequestTimerTest {
    /** The refresh interval of the hello {@code (1, 2)}, in milliseconds. */
    private static final long R = 2 * 1000;

    /** A timer that received the hello {@code (1, 2)}. */
    private static RequestTimer timerWithHello() {
        RequestTimer timer = new RequestTimer();
        timer.onHello(1, 2);
        return timer;
    }

    /**
     * Bug: starter
     */
    @Test
    void t40_sendsOnFirstPress() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));
    }

    /**
     * Bug: client sends to a server without the mod
     */
    @Test
    void t41_noHelloNeverSends() {
        RequestTimer timer = new RequestTimer();

        assertFalse(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, R));
        assertFalse(timer.shouldSend(true, 10 * R));
    }

    /**
     * Bug: a request every tick; off-by-one at the interval
     */
    @Test
    void t42_repeatsAfterFullInterval() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, 50));
        assertFalse(timer.shouldSend(true, R - 1));
        assertTrue(timer.shouldSend(true, R));
    }

    /**
     * Bug: a new press waits for the old interval, so the list shows stale data
     */
    @Test
    void t43_newPressSendsAtOnce() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));
        timer.shouldSend(false, 500);

        assertTrue(timer.shouldSend(true, 600));
    }

    /**
     * Bug: hello state survives the disconnect, so the client sends to the next, vanilla server
     */
    @Test
    void t44_disconnectForgetsHello() {
        RequestTimer timer = timerWithHello();

        timer.onDisconnect();
        assertFalse(timer.shouldSend(true, 0));

        timer.onHello(1, 2);
        assertTrue(timer.shouldSend(true, R));
    }

    /**
     * Bug: an interval below one second makes the client send every tick (T-45: 0, T-79: negative); refreshSeconds * 1000 overflows as int (T-79: Integer.MAX_VALUE)
     */
    @ParameterizedTest(name = "{0}: hello (1, {1}) repeats after {2} ms")
    @CsvSource({ "T-45 zero, 0, 1000", //
            "T-79 negative, -5, 1000", //
            "T-79 largest, 2147483647, 2147483647000" //
    })
    void t45_t79_intervalIsClampedAndLong(String name, int refreshSeconds, long expectedMillis) {
        RequestTimer timer = new RequestTimer();
        timer.onHello(1, refreshSeconds);

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, expectedMillis - 1));
        assertTrue(timer.shouldSend(true, expectedMillis));
    }

    /**
     * Bug: an unsupported protocol version is treated as a valid hello (T-46: 0); the check is == 0 instead of < 1 (T-80: -1)
     */
    @ParameterizedTest(name = "{0}: hello ({1}, 2) is ignored")
    @CsvSource({ "T-46 zero, 0", //
            "T-80 negative, -1" //
    })
    void t46_t80_ignoresUnsupportedVersion(String name, int protocolVersion) {
        RequestTimer timer = new RequestTimer();
        timer.onHello(protocolVersion, 2);

        assertFalse(timer.shouldSend(true, 0));
    }

    /**
     * Bug: the timer uses Protocol.REFRESH_SECONDS instead of the hello's interval, or the smaller of the two
     */
    @Test
    void t72_usesLongerIntervalFromHello() {
        RequestTimer timer = new RequestTimer();
        timer.onHello(1, 5);

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, 4_999));
        assertTrue(timer.shouldSend(true, 5_000));
    }

    /**
     * Bug: the timer uses the larger of the hello's interval and Protocol.REFRESH_SECONDS
     */
    @Test
    void t73_usesShorterIntervalFromHello() {
        RequestTimer timer = new RequestTimer();
        timer.onHello(1, 1);

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, 999));
        assertTrue(timer.shouldSend(true, 1_000));
    }

    /**
     * Bug: the last send time is not updated on a repeat, so after the first interval the client sends every tick; the interval is counted from the press instead of the last send
     */
    @Test
    void t74_intervalCountsFromLastSend() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));
        assertTrue(timer.shouldSend(true, R + 30));

        // Counted from R + 30, not from the press at 0
        assertFalse(timer.shouldSend(true, 2 * R));
        assertTrue(timer.shouldSend(true, 2 * R + 30));
        assertFalse(timer.shouldSend(true, 2 * R + 31));
    }

    /**
     * Bug: TAB is ignored once the interval has passed
     */
    @Test
    void t75_releasedTabNeverSends() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));

        assertFalse(timer.shouldSend(false, R));
    }

    /**
     * Bug: the check is != Protocol.VERSION, so a newer server is treated as one without the mod
     */
    @Test
    void t76_acceptsNewerVersion() {
        RequestTimer timer = new RequestTimer();
        timer.onHello(Protocol.VERSION + 1, 2);

        assertTrue(timer.shouldSend(true, 0));
    }

    /**
     * Bug: the press state is tracked without a hello, so a player who holds TAB while joining gets nothing until they release it
     */
    @Test
    void t77_tabHeldBeforeHelloSendsOnHello() {
        RequestTimer timer = new RequestTimer();

        assertFalse(timer.shouldSend(true, 0));

        timer.onHello(1, 2);
        assertTrue(timer.shouldSend(true, 50));
    }

    /**
     * Bug: the last send or press state survives the disconnect, so the first request to the next server is delayed
     */
    @Test
    void t78_disconnectResetsSendState() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));

        timer.onDisconnect();
        timer.onHello(1, 2);

        // TAB still held, well within the old interval
        assertTrue(timer.shouldSend(true, 50));
    }

    /**
     * Bug: a disconnect without a hello fails or breaks the timer; this is what happens each time the player leaves a vanilla server
     */
    @Test
    void t81_disconnectWithoutHello() {
        RequestTimer timer = new RequestTimer();

        assertDoesNotThrow(timer::onDisconnect);
        assertDoesNotThrow(timer::onDisconnect);

        timer.onHello(1, 2);
        assertTrue(timer.shouldSend(true, 0));
    }

    /**
     * Bug: an ignored hello clears the valid one
     */
    @Test
    void t82_ignoredHelloKeepsValidOne() {
        RequestTimer timer = timerWithHello();

        timer.onHello(0, 2);

        assertTrue(timer.shouldSend(true, 0));
    }

    /**
     * Bug: the clamp compares with the previous interval instead of 1 second, so a server's longer interval sticks to every later server
     */
    @Test
    void t83_nextServerUsesItsOwnInterval() {
        RequestTimer timer = new RequestTimer();
        timer.onHello(1, 5);
        timer.onDisconnect();
        timer.onHello(1, 2);

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, R - 1));
        assertTrue(timer.shouldSend(true, R));
    }

    /**
     * Bug: an ignored hello still sets its interval
     */
    @Test
    void t84_ignoredHelloKeepsInterval() {
        RequestTimer timer = timerWithHello();

        timer.onHello(0, 5);

        assertTrue(timer.shouldSend(true, 0));
        assertFalse(timer.shouldSend(true, R - 1));
        assertTrue(timer.shouldSend(true, R));
    }

    /**
     * Bug: a later hello is ignored, so the old interval stays; or it counts as a TAB press and sends at once
     */
    @Test
    void t85_laterHelloReplacesInterval() {
        RequestTimer timer = timerWithHello();

        assertTrue(timer.shouldSend(true, 0));

        // Same connection, no disconnect: the server announces a new interval
        timer.onHello(1, 5);

        assertFalse(timer.shouldSend(true, R));
        assertFalse(timer.shouldSend(true, 4_999));
        assertTrue(timer.shouldSend(true, 5_000));
    }
}
