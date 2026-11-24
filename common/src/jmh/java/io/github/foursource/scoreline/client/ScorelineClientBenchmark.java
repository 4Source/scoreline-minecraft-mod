package io.github.foursource.scoreline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;

/**
 * The client's side of the protocol: the check every tick, one connection, and logging a response. The network log lines are DEBUG, below the benchmark's log level, so they are not written.
 */
@State(Scope.Thread)
public class ScorelineClientBenchmark {
    /** Milliseconds per client tick (20 ticks per second). */
    private static final long TICK_MILLIS = 50;

    /** A response for a number of players. Only {@link #statsResponse} takes it, so the other benchmarks run once, not once per player count. */
    @State(Scope.Thread)
    public static class Response {
        @Param({ "1", "10", "50" })
        public int players;

        ScorelineClient client;
        StatsResponsePayload response;

        @Setup
        public void setUp() {
            client = new ScorelineClient(new BenchmarkServerConnection());
            List<PlayerStats> rows = new ArrayList<>(players);
            for (int i = 0; i < players; i++) {
                rows.add(new PlayerStats(new UUID(0, i), List.of(72_000L + i, (long) i)));
            }
            response = new StatsResponsePayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS), rows);
        }
    }

    private ScorelineClient tickClient;
    private long now;
    private ScorelineClient connectionClient;
    private HelloPayload hello;

    @Setup
    public void setUp() {
        BenchmarkServerConnection connection = new BenchmarkServerConnection();
        hello = HelloPayload.current();

        tickClient = new ScorelineClient(connection);
        tickClient.onHello(hello);
        connectionClient = new ScorelineClient(connection);
    }

    /** Key held, one tick later per call: one call in 40 sends a request, as in the game. */
    @Benchmark
    public void tick() {
        now += TICK_MILLIS;
        tickClient.onTick(true, now);
    }

    /** One connection to a modded server: the hello arrives, then the player leaves. */
    @Benchmark
    public ScorelineClient helloDisconnect(Blackhole blackhole) {
        connectionClient.onHello(hello);
        // Hands the client out between the calls, so the JIT cannot drop onHello's writes as overwritten by onDisconnect
        blackhole.consume(connectionClient);
        connectionClient.onDisconnect();
        return connectionClient;
    }

    /** A response with playtime and deaths for {@code players} players. */
    @Benchmark
    public void statsResponse(Response input) {
        input.client.onStatsResponse(input.response);
    }
}
