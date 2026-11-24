package io.github.foursource.scoreline.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.foursource.scoreline.ScorelineCommon;
import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.RequestGate;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.stats.Stat;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * The server side of the protocol: greets players that have the mod and answers their stats requests.
 * <p>
 * One instance lives for one running server; the loader module creates it when the server starts and drops it when the server stops, so a new singleplayer world starts with fresh budgets. The loader
 * module forwards its events here, and every method runs on the server thread, where reading player stats is safe.
 *
 * @param <P> how the loader represents a player, for example {@code ServerPlayer}; only passed back to {@link GameServer}.
 */
public class ScorelineServer<P> {
    /** Decides which requests are answered: joined players only, within their budget, and only known stat IDs. */
    private final RequestGate requestGate;
    /** The running game server. */
    private final GameServer<P> server;

    /**
     * Creates the server side with no player joined yet. The stats v0.2 knows are playtime and deaths.
     *
     * @param server the running game server, as the loader sees it
     */
    public ScorelineServer(GameServer<P> server) {
        this.requestGate = new RequestGate(Set.of(Protocol.PLAY_TIME, Protocol.DEATHS), Protocol.BUDGET_CAPACITY, Protocol.BUDGET_REFILL_MILLIS);
        this.server = server;
    }

    /**
     * Registers a player who logged in, and sends the hello if their client has the mod.
     * <p>
     * Called at login, after the configuration phase, so the client's channels are already known.
     *
     * @param player the player who logged in
     */
    public void onJoin(P player) {
        ScorelineCommon.NET_LOG.debug("Player {} joined the server", server.getUUIDOf(player));
        requestGate.onJoin(server.getUUIDOf(player));

        // A vanilla client does not know the channel, and NeoForge refuses to send on it
        if (server.checkPlayerHasChannel(player, HelloPayload.TYPE)) {
            server.sendTo(player, HelloPayload.current());
        }
    }

    /**
     * Forgets a player who logged out, so a request that arrives after the logout is refused.
     *
     * @param player the player who logged out
     */
    public void onLeave(P player) {
        ScorelineCommon.NET_LOG.debug("Player {} left the server", server.getUUIDOf(player));
        requestGate.onLeave(server.getUUIDOf(player));
    }

    /**
     * Decides what a player gets back for a stats request.
     * <p>
     * Returns the payload instead of sending it; the caller sends it back to the requester. A requester whose client lacks the response channel gets nothing: Scoreline's payloads are optional, so
     * NeoForge accepts a request from any client, also from one that could not receive the response.
     *
     * @param requester the player who sent the request
     * @param ids the stat IDs in the request, in the order the client wants them
     * @param nowMillis the current time in milliseconds; passed in so the time source stays with the caller
     * @return the response, with one row per online player and one value per admitted stat ID; empty if nothing is sent: the requester's client lacks the response channel, the request was refused, or
     * none of its IDs is known
     */
    public Optional<StatsResponsePayload> onStatsRequest(P requester, List<String> ids, long nowMillis) {
        ScorelineCommon.PAYLOAD_LOG.debug("Server received: stats_request requester={} now={}ms statIDs={}", server.getUUIDOf(requester), nowMillis, ids);

        // A client without the channel cannot receive the response (a modified client: NeoForge lets
        // optional payloads in from any client); answer nothing and spend none of its budget
        if (!server.checkPlayerHasChannel(requester, StatsResponsePayload.TYPE)) {
            ScorelineCommon.NET_LOG.debug("Server: requester={} now={}ms stats_request was rejected as the requester lacks the response channel", server.getUUIDOf(requester), nowMillis);
            return Optional.empty();
        }

        // Refused: the player has not joined, or their budget is used up
        Optional<List<String>> admitted = requestGate.admit(server.getUUIDOf(requester), ids, nowMillis);
        if (admitted.isEmpty()) {
            ScorelineCommon.NET_LOG.debug("Server: requester={} now={}ms stats_request was rejected as the requester is not on the server or has used up their budget", server.getUUIDOf(requester),
                    nowMillis);
            return Optional.empty();
        }

        // Unknown IDs are ignored (spec, Networking); with none left there is nothing to answer
        List<String> statIds = admitted.get();
        if (statIds.isEmpty()) {
            ScorelineCommon.NET_LOG.debug("Server: requester={} now={}ms stats_request was rejected as none of the requested stat IDs is supported by the server", server.getUUIDOf(requester),
                    nowMillis);
            return Optional.empty();
        }

        // Every online player, the requester included, so the response is never without rows
        List<P> online = server.getOnlinePlayers();

        // The gate only lets known IDs through, so byName always finds them, and every stat ID is a Stat
        List<Stat<?>> stats = statIds.stream().<Stat<?>>map(id -> (Stat<?>) ObjectiveCriteria.byName(id).orElseThrow()).toList();

        List<PlayerStats> playerStats = new ArrayList<>(online.size());
        for (P player : online) {
            List<Long> values = new ArrayList<>(statIds.size());
            // Same order as statIds, which the payload requires
            for (Stat<?> stat : stats) {
                values.add((long) server.getStatValueOf(player, stat));
            }
            playerStats.add(new PlayerStats(server.getUUIDOf(player), values));
        }

        return Optional.of(new StatsResponsePayload(statIds, playerStats));
    }
}
