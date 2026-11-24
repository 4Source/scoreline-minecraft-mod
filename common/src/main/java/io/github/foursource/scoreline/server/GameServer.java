package io.github.foursource.scoreline.server;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.stats.Stat;

/**
 * The running game server as the loader sees it: everything {@link ScorelineServer} needs from the game.
 *
 * @param <P> how the loader represents a player, for example {@code ServerPlayer}; {@link ScorelineServer} only passes it back to these methods
 */
public interface GameServer<P> {
    /**
     * Returns every player online now.
     *
     * @return the online players, in the server's order; the server's own list, not a copy, so callers do not change it
     */
    List<P> getOnlinePlayers();

    /**
     * Returns a player's UUID.
     *
     * @param player the player
     * @return the player's UUID
     */
    UUID getUUIDOf(P player);

    /**
     * Checks whether a player's client can receive a payload type.
     *
     * @param player the player
     * @param type the payload type to check
     * @return {@code true} if the player's client registered the channel for {@code type}; {@code false} for a client without the channel
     */
    boolean checkPlayerHasChannel(P player, CustomPacketPayload.Type<?> type);

    /**
     * Sends a payload to a player's client.
     * <p>
     * The caller checks {@link #checkPlayerHasChannel} first.
     *
     * @param player the player to send to
     * @param payload the payload to send
     */
    void sendTo(P player, CustomPacketPayload payload);

    /**
     * Returns a player's current value of a statistic.
     *
     * @param player the player
     * @param stat the statistic, as resolved from its stat ID
     * @return the value as the game counts it
     */
    int getStatValueOf(P player, Stat<?> stat);
}
