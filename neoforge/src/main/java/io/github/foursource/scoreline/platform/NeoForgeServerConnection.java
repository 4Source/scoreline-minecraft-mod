package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.client.ServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * NeoForge's {@link ServerConnection}: passes each call on to the client's connection, and decides nothing beyond whether there is one.
 */
public class NeoForgeServerConnection implements ServerConnection {
    /**
     * Creates the adapter; it holds nothing and looks the connection up on each call, so one instance serves every server the client joins.
     */
    public NeoForgeServerConnection() {}

    @Override
    public boolean checkServerHasChannel(Type<?> type) {
        // null while not connected, for example in the main menu
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
