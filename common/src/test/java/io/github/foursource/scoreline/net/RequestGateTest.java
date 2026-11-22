package io.github.foursource.scoreline.net;

import static io.github.foursource.scoreline.net.Protocol.DEATHS;
import static io.github.foursource.scoreline.net.Protocol.PLAY_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class RequestGateTest {
    /** Differs from {@link Protocol#BUDGET_CAPACITY} on purpose, so a gate that ignores its arguments fails. */
    private static final int CAP = 3;

    /** Differs from {@link Protocol#BUDGET_REFILL_MILLIS} on purpose, so a gate that ignores its arguments fails. */
    private static final long T = 500;

    private static final UUID P = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID Q = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final String UNKNOWN = "questmod:quests_completed";

    /** A gate that knows playtime and deaths, with no player joined. */
    private static RequestGate newGate() {
        return new RequestGate(Set.of(PLAY_TIME, DEATHS), CAP, T);
    }

    /** A gate that knows playtime and deaths, with {@code P} joined. */
    private static RequestGate gateWithP() {
        RequestGate gate = newGate();
        gate.onJoin(P);
        return gate;
    }

    /** Uses up the player's whole budget at time 0 and checks that each of those requests was admitted. */
    private static void drain(RequestGate gate, UUID player) {
        for (int i = 0; i < CAP; i++) {
            assertTrue(gate.admit(player, List.of(DEATHS), 0).isPresent(), "request " + (i + 1) + " of " + CAP + " refused");
        }
    }

    /**
     * Bug: starter
     */
    @Test
    void t34_admitsKnownStats() {
        RequestGate gate = gateWithP();

        assertEquals(Optional.of(List.of(PLAY_TIME, DEATHS)), gate.admit(P, List.of(PLAY_TIME, DEATHS), 0));
    }

    /**
     * Bug: a real vanilla stat that v0.2 does not offer is passed on, because the gate checks the format instead of the known set; order lost
     */
    @Test
    void t35_dropsUnknownStatsKeepsOrder() {
        RequestGate gate = gateWithP();

        assertEquals(Optional.of(List.of(DEATHS, PLAY_TIME)), gate.admit(P, List.of(DEATHS, "minecraft.custom:minecraft.jump", PLAY_TIME), 0));
    }

    /**
     * Bug: duplicates double the response; the last occurrence is kept instead of the first
     */
    @Test
    void t36_dropsDuplicatesKeepsFirst() {
        RequestGate gate = gateWithP();

        assertEquals(Optional.of(List.of(DEATHS, PLAY_TIME)), gate.admit(P, List.of(DEATHS, PLAY_TIME, DEATHS), 0));
    }

    /**
     * Bug: budget kept after logout (map grows); request answered without a join
     */
    @Test
    void t37_answersOnlyJoinedPlayers() {
        RequestGate gate = newGate();

        // Never joined
        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), 0));

        // Joined, then left
        gate.onJoin(P);
        gate.onLeave(P);
        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), 0));

        // Joined again
        gate.onJoin(P);
        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(P, List.of(DEATHS), 0));
    }

    /**
     * Bug: gate ignores the budget; budgets shared between players
     */
    @Test
    void t38_budgetPerPlayer() {
        RequestGate gate = gateWithP();
        gate.onJoin(Q);

        drain(gate, P);
        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), 0));

        // Q's budget is untouched by P's requests
        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(Q, List.of(DEATHS), 0));
    }

    static Stream<Arguments> requestsWithoutKnownStats() {
        return Stream.of(
                // T-39: only unknown IDs
                Arguments.of("T-39 unknown only", List.of(UNKNOWN)),
                // T-68: no IDs at all
                Arguments.of("T-68 empty", List.of()));
    }

    /**
     * Bug: requests without a known stat (T-39: only unknown IDs; T-68: empty) bypass the budget, so a client can spam the server for free
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("requestsWithoutKnownStats")
    void t39_t68_requestsWithoutKnownStatsUseBudget(String name, List<String> requested) {
        RequestGate gate = gateWithP();

        for (int i = 0; i < CAP; i++) {
            assertEquals(Optional.of(List.of()), gate.admit(P, requested, 0));
        }

        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), 0));
    }

    /**
     * Bug: the gate does not pass nowMillis to the budget (passes 0, or builds a new budget per call), so the budget never refills
     */
    @Test
    void t65_budgetRefillsWithTime() {
        RequestGate gate = gateWithP();

        drain(gate, P);

        // Not refilled before a full interval
        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), T - 1));

        // One token back after a full interval
        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(P, List.of(DEATHS), T));
    }

    /**
     * Bug: the gate filters against Protocol's IDs instead of its knownStatIds
     */
    @Test
    void t66_filtersAgainstGivenKnownSet() {
        RequestGate gate = new RequestGate(Set.of(DEATHS), CAP, T);
        gate.onJoin(P);

        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(P, List.of(PLAY_TIME, DEATHS), 0));
    }

    /**
     * Bug: onLeave clears every player's budget
     */
    @Test
    void t67_leaveForgetsOnlyThatPlayer() {
        RequestGate gate = gateWithP();
        gate.onJoin(Q);

        gate.onLeave(P);

        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(Q, List.of(DEATHS), 0));
    }

    /**
     * Bug: invalid values fail only at the first join, inside the join event, far from where the gate was built
     */
    @ParameterizedTest(name = "capacity {0}, interval {1} -> names {2}")
    @CsvSource({ "0, 500, capacity", // capacity 0
            "3, 0, refillIntervalMillis" // interval 0
    })
    void t69_constructorRejectsInvalidBudget(int capacity, long interval, String named) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new RequestGate(Set.of(PLAY_TIME, DEATHS), capacity, interval));

        // Both checks throw the same type, so the message must name the parameter that broke the rule
        assertTrue(thrown.getMessage() != null && thrown.getMessage().contains(named), () -> "expected a message naming \"" + named + "\" but was: " + thrown.getMessage());
    }

    /**
     * Bug: Set.copyOf dropped, so the filter changes when the caller's set changes
     */
    @Test
    void t70_gateCopiesKnownSet() {
        Set<String> known = new HashSet<>(Set.of(PLAY_TIME, DEATHS));
        RequestGate gate = new RequestGate(known, CAP, T);
        gate.onJoin(P);

        known.remove(DEATHS);

        assertEquals(Optional.of(List.of(DEATHS)), gate.admit(P, List.of(DEATHS), 0));
    }

    /**
     * Bug: a second join replaces the budget with a full one, so a repeated join refills it and lets the player flood the server
     */
    @Test
    void t71_secondJoinKeepsBudget() {
        RequestGate gate = gateWithP();

        drain(gate, P);

        gate.onJoin(P);

        // Still used up: the second join did not refill the budget
        assertEquals(Optional.empty(), gate.admit(P, List.of(DEATHS), 0));
    }
}
