package io.github.foursource.scoreline.client;

import static io.github.foursource.scoreline.net.Protocol.DEATHS;
import static io.github.foursource.scoreline.net.Protocol.PLAY_TIME;
import static io.github.foursource.scoreline.net.Protocol.REFRESH_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.CapturedLog;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;

public class ScorelineClientTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    /** The one request the client sends in v0.2. */
    private static final StatsRequestPayload REQUEST = new StatsRequestPayload(List.of(PLAY_TIME, DEATHS));

    private FakeServerConnection connection;
    private ScorelineClient client;

    /** A connection whose server has the request channel, and a client that has not had a hello yet. */
    @BeforeEach
    void setUp() {
        connection = new FakeServerConnection();
        connection.channels.add(StatsRequestPayload.TYPE);
        client = new ScorelineClient(connection);
    }

    /**
     * Bug: wrong IDs or order, or nothing sent
     */
    @Test
    void t216_keyDownAfterHelloSendsRequest() {
        client.onHello(HelloPayload.current());

        client.onTick(true, 0);

        assertEquals(List.of(REQUEST), connection.sent);
    }

    /**
     * Bug: the timer not asked, so a server without the mod is asked; the hello's version not passed on, so an invalid hello enables requests
     */
    @Test
    void t217_noValidHelloSendsNothing() {
        client.onTick(true, 0);
        assertEquals(List.of(), connection.sent);

        client.onHello(new HelloPayload(0, REFRESH_SECONDS));
        client.onTick(true, 1);
        assertEquals(List.of(), connection.sent);
    }

    /**
     * Bug: requests sent to a server that cannot take them
     */
    @Test
    void t218_noRequestChannelSendsNothing() {
        connection.channels.clear();
        client.onHello(HelloPayload.current());

        client.onTick(true, 0);

        assertEquals(List.of(), connection.sent);
    }

    /**
     * Bug: the timer asked before the channel check: it records the press at 0, so nothing is sent until R
     */
    @Test
    void t219_timerNotAskedWithoutChannel() {
        connection.channels.clear();
        client.onHello(HelloPayload.current());
        client.onTick(true, 0);

        connection.channels.add(StatsRequestPayload.TYPE);
        client.onTick(true, 1);

        assertEquals(List.of(REQUEST), connection.sent);
    }

    /**
     * Bug: the time, the key or the hello's interval not passed on to the timer
     */
    @Test
    void t220_heldKeySendsOncePerHelloInterval() {
        // Not the protocol's default interval, so a client that ignores the hello's value fails
        client.onHello(new HelloPayload(1, 5));

        client.onTick(true, 0);
        assertEquals(List.of(REQUEST), connection.sent);

        client.onTick(true, 4_999);
        assertEquals(List.of(REQUEST), connection.sent);

        client.onTick(true, 5_000);
        assertEquals(List.of(REQUEST, REQUEST), connection.sent);
    }

    /**
     * Bug: the key state ignored
     */
    @Test
    void t221_keyUpSendsNothing() {
        client.onHello(HelloPayload.current());

        client.onTick(false, 0);

        assertEquals(List.of(), connection.sent);
    }

    /**
     * Bug: onDisconnect not forwarded
     */
    @Test
    void t222_disconnectStopsRequestsUntilNextHello() {
        client.onHello(HelloPayload.current());
        client.onDisconnect();

        client.onTick(true, 0);
        assertEquals(List.of(), connection.sent);

        client.onHello(HelloPayload.current());
        client.onTick(true, 1);
        assertEquals(List.of(REQUEST), connection.sent);
    }

    /**
     * Bug: the hello log removed, its values swapped, or written at INFO into players' consoles
     */
    @Test
    void t223_helloIsLogged() {
        try (CapturedLog log = CapturedLog.start()) {
            client.onHello(new HelloPayload(1, 2));

            assertEquals(List.of("DEBUG [scoreline.net.payload] Client received: hello version=1 refresh=2s"), log.lines());
        }
    }

    /**
     * Bug: values of one player under another, a player dropped, IDs and values out of step, the inner loop bounded by the player count
     */
    @Test
    void t224_responseLogsOneLinePerPlayer() {
        // Three players for two IDs, so a loop bounded by the wrong list fails
        StatsResponsePayload response = new StatsResponsePayload(List.of(PLAY_TIME, DEATHS),
                List.of(new PlayerStats(A, List.of(11L, 12L)), new PlayerStats(B, List.of(21L, 22L)), new PlayerStats(C, List.of(31L, 32L))));

        try (CapturedLog log = CapturedLog.start()) {
            client.onStatsResponse(response);

            assertEquals(List.of("DEBUG [scoreline.net.payload] Client received: stats_response uuid=" + A + " " + PLAY_TIME + "=11 " + DEATHS + "=12",
                    "DEBUG [scoreline.net.payload] Client received: stats_response uuid=" + B + " " + PLAY_TIME + "=21 " + DEATHS + "=22",
                    "DEBUG [scoreline.net.payload] Client received: stats_response uuid=" + C + " " + PLAY_TIME + "=31 " + DEATHS + "=32"), log.lines());
        }
    }

    /**
     * Bug: a line without a player
     */
    @Test
    void t225_responseWithoutPlayersLogsNothing() {
        try (CapturedLog log = CapturedLog.start()) {
            client.onStatsResponse(new StatsResponsePayload(List.of(PLAY_TIME, DEATHS), List.of()));

            assertEquals(List.of(), log.lines());
        }
    }
}
