package io.github.foursource.scoreline.platform;

import java.util.List;
import java.util.UUID;

import io.github.foursource.scoreline.server.GameServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge's {@link GameServer}: passes each call on to the running {@link MinecraftServer} or the player, and decides nothing.
 */
public class NeoForgeGameServer implements GameServer<ServerPlayer> {
    /** The server whose players this answers for. */
    private final MinecraftServer server;

    /**
     * Wraps a server; created when the server starts.
     *
     * @param server the running server
     */
    public NeoForgeGameServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public List<ServerPlayer> getOnlinePlayers() {
        return this.server.getPlayerList().getPlayers();
    }

    @Override
    public UUID getUUIDOf(ServerPlayer player) {
        return player.getUUID();
    }

    @Override
    public boolean checkPlayerHasChannel(ServerPlayer player, Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public int getStatValueOf(ServerPlayer player, Stat<?> stat) {
        return player.getStats().getValue(stat);
    }
}
