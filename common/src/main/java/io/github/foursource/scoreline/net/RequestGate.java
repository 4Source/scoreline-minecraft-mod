package io.github.foursource.scoreline.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides on the server which stats request is answered, and with which stats.
 * <p>
 * Each joined player has their own {@link RequestBudget}; a request is answered only while the budget has a token left. Of the requested stat IDs, only the known ones are answered, in request order
 * and without duplicates; unknown IDs are ignored, as the spec's server rules ask.
 * <p>
 * A player who leaves is forgotten, so the map does not grow with every session. A reconnect therefore starts with a full budget; this is accepted, because a login costs the server far more than the
 * requests it gains.
 */
public class RequestGate {
    /** The stat IDs the server can answer; requested IDs outside this set are dropped. */
    private final Set<String> statIds;

    /** The burst size of each player's budget. */
    private final int capacity;

    /** The time for one token of each player's budget to return. */
    private final long refillIntervalMillis;

    /** The budget of each joined player; a player without an entry is not joined. */
    private final Map<UUID, RequestBudget> players;

    /**
     * Creates a gate with no player joined.
     *
     * @param knownStatIds the stat IDs the server can answer; copied, so later changes to the caller's set do not change the gate
     * @param capacity the most requests a player may send in a burst; at least 1
     * @param refillIntervalMillis the time for one request of the burst to return; at least 1
     * @throws IllegalArgumentException if {@code capacity} or {@code refillIntervalMillis} is below 1
     */
    public RequestGate(Set<String> knownStatIds, int capacity, long refillIntervalMillis) {
        // Checked here, not at the first join, so a wrong value fails where the gate is built and not inside the join event
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be at least 1, was " + capacity);
        }
        if (refillIntervalMillis <= 0) {
            throw new IllegalArgumentException("refillIntervalMillis must be at least 1, was " + refillIntervalMillis);
        }

        this.statIds = Set.copyOf(knownStatIds);
        this.capacity = capacity;
        this.refillIntervalMillis = refillIntervalMillis;
        players = new HashMap<>();
    }

    /**
     * Registers a player who joined the server, with a full budget.
     * <p>
     * A player who is already joined keeps the budget they have, so a join reported twice cannot refill it.
     *
     * @param player the UUID of the player who joined
     */
    public void onJoin(UUID player) {
        // A second join must not replace a used-up budget with a full one
        players.putIfAbsent(player, new RequestBudget(capacity, refillIntervalMillis));
    }

    /**
     * Forgets a player who left the server, together with their budget. Requests from that player are refused until they join again.
     *
     * @param player the UUID of the player who left; a player who is not joined is ignored
     */
    public void onLeave(UUID player) {
        players.remove(player);
    }

    /**
     * Decides whether a request is answered, and with which stats.
     * <p>
     * The budget is checked first, so every admitted request uses a token, even one whose IDs are all unknown or that holds no ID at all. Otherwise a client could send such requests for free.
     *
     * @param player the UUID of the player who sent the request
     * @param requested the stat IDs in the request, in the order the client wants them; not changed
     * @param nowMillis the current time from the caller's clock, in milliseconds
     * @return empty if the request is refused (the player is not joined, or their budget is used up); otherwise the known IDs of {@code requested} in request order without duplicates, as an immutable
     * list that is empty if none is known
     */
    public Optional<List<String>> admit(UUID player, List<String> requested, long nowMillis) {
        RequestBudget budget = players.get(player);

        // No entry: the player never joined, or has left; a request can still arrive after the leave, because packets are handled after the logout event
        if (budget == null) {
            return Optional.empty();
        }

        // Budget before filtering, so a request with no known ID still costs a token
        if (!budget.tryAcquire(nowMillis)) {
            return Optional.empty();
        }

        // A new list instead of removing from the request: the caller's list may be immutable, and must not change
        Set<String> seen = new HashSet<>();
        List<String> admitted = new ArrayList<>();
        for (String id : requested) {
            // contains first, so only known IDs are remembered; seen.add is false for a repeat, so the first occurrence keeps its place
            if (statIds.contains(id) && seen.add(id)) {
                admitted.add(id);
            }
        }

        return Optional.of(List.copyOf(admitted));
    }
}
