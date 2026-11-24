package io.github.foursource.scoreline.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

/**
 * The client's connection to the server, as the loader sees it: everything {@link ScorelineClient} needs from the network.
 */
public interface ServerConnection {
    /**
     * Checks whether the server the client is connected to can receive a payload type.
     *
     * @param type the payload type to check
     * @return {@code true} if the server registered the channel for {@code type}; {@code false} if it did not, or if the client is not connected (for example in the main menu)
     */
    boolean checkServerHasChannel(Type<?> type);

    /**
     * Sends a payload from the client to the server it is connected to.
     * <p>
     * The caller checks {@link #checkServerHasChannel(Type)} first, which also ensures there is a connection.
     *
     * @param payload the payload to send
     */
    void sendToServer(CustomPacketPayload payload);
}
