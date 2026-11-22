package io.github.foursource.scoreline.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;

/**
 * The loader's network access for common code. Each loader implements it; {@link io.github.foursource.scoreline.platform.Services#NETWORK} holds the instance.
 * <p>
 * {@code common} compiles against vanilla Minecraft, which has neither channel negotiation nor a way to send a mod's payload: both come from the loader. Every Scoreline channel is optional, so a peer
 * without the mod can connect. A payload may therefore only be sent after checking that the other side has its channel; NeoForge refuses to send one the peer does not know.
 * <p>
 * The server methods are called on the server thread, the client methods on the client thread.
 */
public interface INetworkHelper {

    /**
     * Checks on the server whether a player's client can receive a payload type.
     *
     * @param player the connected player
     * @param type the payload type to check
     * @return {@code true} if the client registered the channel for {@code type}, so it has the mod
     */
    boolean hasChannel(ServerPlayer player, Type<?> type);

    /**
     * Sends a payload from the server to one player.
     * <p>
     * The caller checks {@link #hasChannel(ServerPlayer, Type)} first.
     *
     * @param player the player to send to
     * @param payload the payload to send
     */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Checks on the client whether the server it is connected to can receive a payload type.
     *
     * @param type the payload type to check
     * @return {@code true} if the server registered the channel for {@code type}; {@code false} if it did not, or if the client is not connected (for example in the main menu)
     */
    boolean serverHasChannel(Type<?> type);

    /**
     * Sends a payload from the client to the server it is connected to.
     * <p>
     * The caller checks {@link #serverHasChannel(Type)} first, which also ensures there is a connection.
     *
     * @param payload the payload to send
     */
    void sendToServer(CustomPacketPayload payload);
}
