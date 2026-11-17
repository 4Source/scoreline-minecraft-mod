package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.platform.services.INetworkHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge's implementation of {@link INetworkHelper}, named in {@code META-INF/services/} so {@code Services} finds it.
 * <p>
 * The channel checks use {@code hasChannel}, which NeoForge adds to vanilla's connection classes by patching them; that is why {@code common} cannot call it directly. The client methods use client
 * classes, but only inside their bodies, which never run on a dedicated server.
 */
public class NeoForgeNetworkHelper implements INetworkHelper {

    @Override
    public boolean hasChannel(ServerPlayer player, Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean serverHasChannel(Type<?> type) {
        // null while not connected, for example in the main menu
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

}
