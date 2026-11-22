package io.github.foursource.scoreline.client;

import java.util.List;

import io.github.foursource.scoreline.ScorelineCommon;
import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.RequestTimer;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import io.github.foursource.scoreline.platform.Services;
import net.minecraft.client.Minecraft;

/**
 * The client side of the protocol: asks the server for stats while TAB is held and logs the answers.
 * <p>
 * One instance lives as long as the game client and is reused across servers; {@link #onDisconnect()} resets it between them. The loader module forwards its events here, and every method runs on the
 * client thread. In v0.2 the answers are only logged; the TAB overlay that shows them follows in v0.3.
 */
public class ScorelineClient {
    /** Decides when a request is sent; knows whether the current server has the mod. */
    private final RequestTimer requestTimer;

    /**
     * Creates the client side with no server known yet, so nothing is sent before a hello arrives.
     */
    public ScorelineClient() {
        this.requestTimer = new RequestTimer();
    }

    /**
     * Handles the server's hello: from now on, requests may be sent while TAB is held.
     *
     * @param hello the hello the server sent at login
     */
    public void onHello(HelloPayload hello) {
        ScorelineCommon.logProtocol("Scoreline hello: version={} refresh={}s", hello.protocolVersion(), hello.refreshSeconds());
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
     * @param nowMillis the current time in milliseconds; passed in so the time source stays with the caller
     */
    public void onTick(long nowMillis) {
        // No connection, or a server without the mod: there is nobody to ask
        if (!Services.NETWORK.serverHasChannel(StatsRequestPayload.TYPE)) {
            return;
        }
        // The same key check vanilla uses to show the player list, so requests run exactly while the list is open
        if (requestTimer.shouldSend(Minecraft.getInstance().options.keyPlayerList.isDown(), nowMillis)) {
            Services.NETWORK.sendToServer(new StatsRequestPayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS)));
        }
    }

    /**
     * Logs the stats the server sent, at INFO in a development environment and at DEBUG otherwise.
     *
     * @param response the server's answer: one row per online player, with one value per stat ID
     */
    public void onStatsResponse(StatsResponsePayload response) {
        for (int playerIndex = 0; playerIndex < response.players().size(); playerIndex++) {
            PlayerStats playerStats = response.players().get(playerIndex);
            StringBuilder line = new StringBuilder("Scoreline stats_response: uuid=");
            line.append(playerStats.player());
            // The payload guarantees one value per stat ID, in the same order
            for (int statIndex = 0; statIndex < response.statIds().size(); statIndex++) {
                line.append(' ').append(response.statIds().get(statIndex)).append('=').append(playerStats.values().get(statIndex));
            }
            ScorelineCommon.logProtocol(line.toString());
        }
    }
}
