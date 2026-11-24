package io.github.foursource.scoreline.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * The server's answer to a stats request, with one row per online player. Joins and leaves run once per player, not per message, so they are not benchmarked. The network log lines are DEBUG, below
 * the benchmark's log level, so they are not written.
 */
@State(Scope.Thread)
public class ScorelineServerBenchmark {
    @Param({ "1", "10", "50" })
    public int players;

    private ScorelineServer<UUID> server;
    private UUID requester;
    private List<String> requested;
    /** Advances by one refill interval per call, so the budget never runs out. */
    private long now;

    @Setup
    public void setUp() {
        // The server resolves stat IDs through vanilla's registries, which the bootstrap fills
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        List<UUID> online = new ArrayList<>(players);
        for (int i = 0; i < players; i++) {
            online.add(new UUID(0, i));
        }
        server = new ScorelineServer<>(new BenchmarkGameServer(online));
        for (UUID player : online) {
            server.onJoin(player);
        }
        requester = online.getFirst();
        requested = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);
    }

    @Benchmark
    public Optional<StatsResponsePayload> answer() {
        now += Protocol.BUDGET_REFILL_MILLIS;
        return server.onStatsRequest(requester, requested, now);
    }
}
