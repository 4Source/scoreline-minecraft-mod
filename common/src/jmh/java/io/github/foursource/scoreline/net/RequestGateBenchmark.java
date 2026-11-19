package io.github.foursource.scoreline.net;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * The server's side of the protocol logic on the hot path: admitting a request, the answered and the refused way. Creating the gate and joins run once per server start or player, not per message, so
 * they are not benchmarked.
 */
@State(Scope.Thread)
public class RequestGateBenchmark {
    /** Gates with a number of joined players. Each benchmark has a gate of its own: a requester with budget left, and one without. */
    @State(Scope.Thread)
    public static class JoinedPlayers {
        @Param({ "1", "10", "50" })
        public int players;

        List<String> requested;
        UUID requester;

        /** {@code players} joined; the time advances by one refill interval per call, so the budget never runs out. */
        RequestGate admitGate;
        long now;

        /** {@code players} joined; the requester's budget is used up and the time stays fixed, so every call is refused. */
        RequestGate refusedGate;

        @Setup
        public void setUp() {
            Set<String> known = Set.of(Protocol.PLAY_TIME, Protocol.DEATHS);
            requested = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);
            requester = new UUID(0, players - 1);

            admitGate = new RequestGate(known, Protocol.BUDGET_CAPACITY, Protocol.BUDGET_REFILL_MILLIS);
            refusedGate = new RequestGate(known, Protocol.BUDGET_CAPACITY, Protocol.BUDGET_REFILL_MILLIS);
            for (int i = 0; i < players; i++) {
                UUID player = new UUID(0, i);
                admitGate.onJoin(player);
                refusedGate.onJoin(player);
            }

            // Use up the budget at time 0; the benchmark asks at time 0 too, so nothing refills
            for (int i = 0; i < Protocol.BUDGET_CAPACITY; i++) {
                refusedGate.admit(requester, requested, 0);
            }
            if (refusedGate.admit(requester, requested, 0).isPresent()) {
                throw new IllegalStateException("the requester's budget should be used up");
            }
        }
    }

    @Benchmark
    public Optional<List<String>> admit(JoinedPlayers input) {
        input.now += Protocol.BUDGET_REFILL_MILLIS;
        return input.admitGate.admit(input.requester, input.requested, input.now);
    }

    @Benchmark
    public Optional<List<String>> admitRefused(JoinedPlayers input) {
        return input.refusedGate.admit(input.requester, input.requested, 0);
    }
}
