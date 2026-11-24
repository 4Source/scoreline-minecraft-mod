package io.github.foursource.scoreline.server;

import static io.github.foursource.scoreline.net.Protocol.BUDGET_CAPACITY;
import static io.github.foursource.scoreline.net.Protocol.BUDGET_REFILL_MILLIS;
import static io.github.foursource.scoreline.net.Protocol.DEATHS;
import static io.github.foursource.scoreline.net.Protocol.PLAY_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.CapturedLog;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

public class ScorelineServerTest {
    private static final int CAP = BUDGET_CAPACITY;
    private static final long T = BUDGET_REFILL_MILLIS;

    private static final UUID A_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static final String UNKNOWN = "x:unknown";

    private static Stat<?> playTime;
    private static Stat<?> deaths;

    private FakeGameServer game;
    private ScorelineServer<FakePlayer> server;
    /** The requester; online, and joined unless a test says otherwise. */
    private FakePlayer a;
    private FakePlayer b;
    /** Never joins. */
    private FakePlayer c;

    /** Fills vanilla's registries, so the server resolves stat IDs to the real {@link Stat} objects; takes several seconds, once per JVM. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        playTime = Stats.CUSTOM.get(Stats.PLAY_TIME);
        deaths = Stats.CUSTOM.get(Stats.DEATHS);
    }

    @BeforeEach
    void setUp() {
        game = new FakeGameServer();
        server = new ScorelineServer<>(game);
        a = player(A_ID, 100, 3);
        b = player(B_ID, 200, 4);
        c = player(C_ID, 300, 5);
        game.online.add(a);
    }

    /** A player whose client has both client-bound channels; the channel set can be changed. */
    private static FakePlayer player(UUID uuid, int playTimeValue, int deathsValue) {
        return new FakePlayer(uuid, new HashSet<>(Set.of(HelloPayload.TYPE, StatsResponsePayload.TYPE)), Map.of(playTime, playTimeValue, deaths, deathsValue));
    }

    /** Lets {@code A} join and forgets the hello that sent. */
    private void joinA() {
        server.onJoin(a);
        game.sent.clear();
    }

    private static StatsResponsePayload response(List<String> statIds, PlayerStats... rows) {
        return new StatsResponsePayload(statIds, List.of(rows));
    }

    private static String receivedLine(UUID requester, long nowMillis, List<String> ids) {
        return "DEBUG [scoreline.net.payload] Server received: stats_request requester=" + requester + " now=" + nowMillis + "ms statIDs=" + ids;
    }

    private static String lacksChannelLine(UUID requester, long nowMillis) {
        return "DEBUG [scoreline.net] Server: requester=" + requester + " now=" + nowMillis + "ms stats_request was rejected as the requester lacks the response channel";
    }

    private static String refusedLine(UUID requester, long nowMillis) {
        return "DEBUG [scoreline.net] Server: requester=" + requester + " now=" + nowMillis + "ms stats_request was rejected as the requester is not on the server or has used up their budget";
    }

    private static String noneKnownLine(UUID requester, long nowMillis) {
        return "DEBUG [scoreline.net] Server: requester=" + requester + " now=" + nowMillis + "ms stats_request was rejected as none of the requested stat IDs is supported by the server";
    }

    /**
     * Bug: hello not sent, sent twice, or sent to another player; the join line removed
     */
    @Test
    void t204_joinSendsHelloToThePlayerAndLogsIt() {
        try (CapturedLog log = CapturedLog.start()) {
            server.onJoin(a);

            assertEquals(List.of(new FakeGameServer.Sent(a, HelloPayload.current())), game.sent);
            assertEquals(List.of("DEBUG [scoreline.net] Player " + A_ID + " joined the server"), log.lines());
        }
    }

    /**
     * Bug: the gate registration skipped for a client without the hello channel, a send to a client without the hello channel, or the hello gated on the response channel instead
     */
    @Test
    void t205_joinWithoutHelloChannelSendsNothingButRegisters() {
        b.channels().remove(HelloPayload.TYPE);

        server.onJoin(b);
        assertEquals(List.of(), game.sent);

        assertTrue(server.onStatsRequest(b, List.of(DEATHS), 0).isPresent());
    }

    /**
     * Bug: leave not forwarded to the gate; the leave line removed
     */
    @Test
    void t206_leaveLogsAndRefusesLaterRequests() {
        joinA();

        try (CapturedLog log = CapturedLog.start()) {
            server.onLeave(a);
            Optional<StatsResponsePayload> answer = server.onStatsRequest(a, List.of(DEATHS), 0);

            assertEquals(Optional.empty(), answer);
            assertEquals(List.of("DEBUG [scoreline.net] Player " + A_ID + " left the server", receivedLine(A_ID, 0, List.of(DEATHS)), refusedLine(A_ID, 0)), log.lines());
        }
    }

    /**
     * Bug: the budget spent before the channel check
     */
    @Test
    void t207_requestWithoutResponseChannelSpendsNoBudget() {
        joinA();
        a.channels().remove(StatsResponsePayload.TYPE);

        try (CapturedLog log = CapturedLog.start()) {
            List<String> expectedLines = new ArrayList<>();
            for (int i = 0; i < CAP; i++) {
                assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(DEATHS), 0), "request " + (i + 1) + " answered");
                expectedLines.add(receivedLine(A_ID, 0, List.of(DEATHS)));
                expectedLines.add(lacksChannelLine(A_ID, 0));
            }
            assertEquals(expectedLines, log.lines());
        }

        a.channels().add(StatsResponsePayload.TYPE);
        for (int i = 0; i < CAP; i++) {
            assertTrue(server.onStatsRequest(a, List.of(DEATHS), 0).isPresent(), "request " + (i + 1) + " of " + CAP + " refused");
        }
    }

    /**
     * Bug: a request from an unknown player answered
     */
    @Test
    void t208_requestFromPlayerWhoNeverJoinedIsRefused() {
        assertEquals(Optional.empty(), server.onStatsRequest(c, List.of(DEATHS), 0));
    }

    /**
     * Bug: the server's gate built with other limits than Protocol.BUDGET_*
     */
    @Test
    void t209_budgetUsesProtocolLimits() {
        joinA();

        for (int i = 0; i < CAP; i++) {
            assertTrue(server.onStatsRequest(a, List.of(DEATHS), 0).isPresent(), "request " + (i + 1) + " of " + CAP + " refused");
        }
        assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(DEATHS), 0));
        assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(DEATHS), T - 1));
        assertTrue(server.onStatsRequest(a, List.of(DEATHS), T).isPresent());
    }

    /**
     * Bug: an unknown-only request costs nothing, or is answered with an empty response
     */
    @Test
    void t210_unknownOnlyRequestIsRefusedButCostsBudget() {
        joinA();

        try (CapturedLog log = CapturedLog.start()) {
            assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(UNKNOWN), 0));
            assertEquals(List.of(receivedLine(A_ID, 0, List.of(UNKNOWN)), noneKnownLine(A_ID, 0)), log.lines());
        }

        for (int i = 0; i < CAP - 1; i++) {
            assertTrue(server.onStatsRequest(a, List.of(DEATHS), 0).isPresent(), "request " + (i + 1) + " of " + (CAP - 1) + " refused");
        }
        assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(DEATHS), 0));
    }

    /**
     * Bug: an empty response sent
     */
    @Test
    void t211_emptyRequestIsNotAnswered() {
        joinA();

        assertEquals(Optional.empty(), server.onStatsRequest(a, List.of(), 0));
    }

    /**
     * Bug: values in a fixed order instead of the request's
     */
    @Test
    void t212_responseKeepsRequestOrderAndDropsUnknownIds() {
        joinA();

        Optional<StatsResponsePayload> answer = server.onStatsRequest(a, List.of(DEATHS, UNKNOWN, PLAY_TIME), 0);

        assertEquals(Optional.of(response(List.of(DEATHS, PLAY_TIME), new PlayerStats(A_ID, List.of(3L, 100L)))), answer);
    }

    /**
     * Bug: only the requester's row, or one player's values in every row
     */
    @Test
    void t213_oneRowPerOnlinePlayerInServerOrder() {
        joinA();
        game.online.clear();
        game.online.addAll(List.of(b, a, c));

        Optional<StatsResponsePayload> answer = server.onStatsRequest(a, List.of(PLAY_TIME, DEATHS), 0);

        assertEquals(Optional.of(response(List.of(PLAY_TIME, DEATHS), new PlayerStats(B_ID, List.of(200L, 4L)), new PlayerStats(A_ID, List.of(100L, 3L)), new PlayerStats(C_ID, List.of(300L, 5L)))),
                answer);
    }

    /**
     * Bug: a value cut on the way to long, or read as unsigned
     */
    @Test
    void t214_extremeStatValuesKeepTheirSign() {
        FakePlayer extreme = new FakePlayer(A_ID, a.channels(), Map.of(playTime, Integer.MAX_VALUE, deaths, -1));
        game.online.set(0, extreme);
        server.onJoin(extreme);

        Optional<StatsResponsePayload> answer = server.onStatsRequest(extreme, List.of(DEATHS, PLAY_TIME), 0);

        assertEquals(Optional.of(response(List.of(DEATHS, PLAY_TIME), new PlayerStats(A_ID, List.of(-1L, 2147483647L)))), answer);
    }

    /**
     * Bug: the request log removed
     */
    @Test
    void t215_everyRequestIsLoggedFirst() {
        joinA();
        List<String> ids = List.of(PLAY_TIME, DEATHS);

        try (CapturedLog log = CapturedLog.start()) {
            assertTrue(server.onStatsRequest(a, ids, 1234).isPresent());
            assertEquals(List.of(receivedLine(A_ID, 1234, ids)), log.lines());
        }

        try (CapturedLog log = CapturedLog.start()) {
            assertEquals(Optional.empty(), server.onStatsRequest(c, ids, 5678));
            assertEquals(List.of(receivedLine(C_ID, 5678, ids), refusedLine(C_ID, 5678)), log.lines());
        }
    }
}
