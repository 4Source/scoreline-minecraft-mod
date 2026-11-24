package io.github.foursource.scoreline.client;

import java.util.List;

import io.github.foursource.scoreline.ScorelineCommon;
import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.RequestTimer;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;

/**
 * The client side of the protocol: asks the server for stats while TAB is held and logs the answers.
 * <p>
 * One instance lives as long as the game client and is reused across servers; {@link #onDisconnect()} resets it between them. The loader module forwards its events here, and every method runs on the
 * client thread. In v0.2 the answers are only logged; the TAB overlay that shows them follows in v0.3.
 */
public class ScorelineClient {
    /** Decides when a request is sent; knows whether the current server has the mod. */
    private final RequestTimer requestTimer;

    /** The connection to the server. */
    private final ServerConnection connection;

    /**
     * Creates the client side with no server known yet, so nothing is sent before a hello arrives.
     *
     * @param connection the client's connection to the server, as the loader sees it
     */
    public ScorelineClient(ServerConnection connection) {
        this.requestTimer = new RequestTimer();
        this.connection = connection;
    }

    /**
     * Handles the server's hello: from now on, requests may be sent while TAB is held.
     *
     * @param hello the hello the server sent at login
     */
    public void onHello(HelloPayload hello) {
        ScorelineCommon.PAYLOAD_LOG.debug("Client received: hello version={} refresh={}s", hello.protocolVersion(), hello.refreshSeconds());
        requestTimer.onHello(hello.protocolVersion(), hello.refreshSeconds());
    }

    /**
     * Forgets the server, so the next one starts without a hello. Safe to call after leaving a server without the mod.
     */
    public void onDisconnect() {
        requestTimer.onDisconnect();
    }

    /**
     * Sends a request for playtime and deaths when the timer says one is due. Called every client tick, also in the main menu.
     *
     * @param playerListKeyDown whether the player-list key (TAB by default) is held; passed in so the key state stays with the caller
     * @param nowMillis the current time in milliseconds; passed in so the time source stays with the caller
     */
    public void onTick(boolean playerListKeyDown, long nowMillis) {
        // No connection, or a server without the mod: there is nobody to ask
        if (!connection.checkServerHasChannel(StatsRequestPayload.TYPE)) {
            return;
        }
        if (requestTimer.shouldSend(playerListKeyDown, nowMillis)) {
            connection.sendToServer(new StatsRequestPayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS)));
        }
    }

    /**
     * Handles the server's stats_response: Logs the stats the server sent, one line per player.
     *
     * @param response the server's answer: one row per online player, with one value per stat ID
     */
    public void onStatsResponse(StatsResponsePayload response) {
        for (int playerIndex = 0; playerIndex < response.players().size(); playerIndex++) {
            PlayerStats playerStats = response.players().get(playerIndex);
            StringBuilder line = new StringBuilder("Client received: stats_response uuid=");
            line.append(playerStats.player());
            // The payload guarantees one value per stat ID, in the same order
            for (int statIndex = 0; statIndex < response.statIds().size(); statIndex++) {
                line.append(' ').append(response.statIds().get(statIndex)).append('=').append(playerStats.values().get(statIndex));
            }
            ScorelineCommon.PAYLOAD_LOG.debug(line.toString());
        }
    }
}
