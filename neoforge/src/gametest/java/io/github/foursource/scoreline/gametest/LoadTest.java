package io.github.foursource.scoreline.gametest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The load of the manual profiling run: 50 modded players each ask for their stats every 40 ticks for 600 ticks, the spec's target server with the client's 2-second interval. It measures nothing
 * itself and checks only that every request was answered; Java Flight Recorder's method timing, switched on by the run, counts and times Scoreline's server-side calls.
 * <p>
 * It runs only in the profiling run ({@code runProfileServer}), never with the other game tests: they would add their own requests to the timings, and every build would take 30 s longer.
 * <p>
 * The players' requests are spread over the 40 ticks, as real clients' are; all 50 in one tick would measure a burst no server sees. Requests skip the decoding (see {@link MockClient}); the JMH
 * benchmarks measure the codecs.
 */
public final class LoadTest {
    /** Players on the server, the spec's target. */
    static final int PLAYERS = 50;
    /** Ticks between two requests of one player: the client's 2 s at 20 ticks per second. */
    static final int INTERVAL_TICKS = Protocol.REFRESH_SECONDS * 20;
    /** Ticks the load runs: 30 s. */
    static final int LOAD_TICKS = 600;
    /**
     * Ticks after the last request before the count is checked. A response can reach the client's queue a tick after its request (seen: on the last tick, 748 of 750 counted while the recording showed
     * all 750 answered), so the check waits for the stragglers.
     */
    static final int DRAIN_TICKS = 5;
    /** One tick of a server at 20 ticks per second. */
    private static final long TICK_NANOS = 50_000_000;

    private static final List<String> STATS = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);

    private LoadTest() {}

    /**
     * Lists the load test for {@link GameTestRegistration}.
     *
     * @return the load test
     */
    static List<GameTestCase> tests() {
        // A few ticks of margin, so the check on the last tick runs before the time limit
        return List.of(new GameTestCase("t108_load_50_players", LoadTest::t108_load50Players, LOAD_TICKS + 20));
    }

    /**
     * Waits until the tick's real start time, 50 ms after the previous one. The game-test server runs its ticks back to back without waiting, unlike a real server; the server reads the real clock for
     * the request budget, so unpaced the 600 ticks pass in a few seconds and most requests are refused (seen: 250 of 750 answered).
     *
     * @param tick the load tick, from 1
     * @param startNanos holds the time of tick 1, set on that tick
     */
    private static void paceToRealTime(long tick, long[] startNanos) {
        if (tick == 1) {
            startNanos[0] = System.nanoTime();
            return;
        }
        long waitNanos = startNanos[0] + (tick - 1) * TICK_NANOS - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Bug: requests refused by the budget (the game's time not passed in, or the interval too short), so the profiling run measures the refused path
    private static void t108_load50Players(GameTestHelper helper) {
        List<MockClient> clients = new ArrayList<>(PLAYERS);
        for (int i = 0; i < PLAYERS; i++) {
            clients.add(MockClient.connect(helper, MockClient.Kind.MODDED));
        }
        int[] sent = new int[1];
        int[] answered = new int[1];
        long[] startNanos = new long[1];

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 1 || tick > LOAD_TICKS + DRAIN_TICKS) {
                return;
            }
            paceToRealTime(tick, startNanos);
            for (int i = 0; i < PLAYERS; i++) {
                MockClient client = clients.get(i);
                // Player i asks on the ticks where (tick + i) is a multiple of the interval: 15 requests each over 600 ticks, about one per tick in total
                if (tick <= LOAD_TICKS && (tick + i) % INTERVAL_TICKS == 0) {
                    client.sendPayload(new StatsRequestPayload(STATS));
                    sent[0]++;
                }
                // Empties the queue every tick: 750 responses of 50 rows would otherwise pile up, and the timings would include garbage collection
                answered[0] += client.takeReceivedPayloads(StatsResponsePayload.class).size();
            }
            if (tick == LOAD_TICKS + DRAIN_TICKS) {
                int expected = PLAYERS * (LOAD_TICKS / INTERVAL_TICKS);
                helper.assertTrue(sent[0] == expected, "expected " + expected + " requests, sent " + sent[0]);
                helper.assertTrue(answered[0] == sent[0], "only " + answered[0] + " of " + sent[0] + " requests were answered");
                helper.succeed();
            }
        });
    }
}
