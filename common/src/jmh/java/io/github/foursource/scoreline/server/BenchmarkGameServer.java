package io.github.foursource.scoreline.server;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.stats.Stat;

/**
 * A game server that allocates nothing, so a benchmark measures only {@link ScorelineServer}: a player is its UUID, every client has every channel, sending does nothing, and every stat value is 1.
 */
class BenchmarkGameServer implements GameServer<UUID> {
    /** The players online, built once in the benchmark's setup. */
    private final List<UUID> online;

    /**
     * Creates the server with a fixed list of players.
     *
     * @param online the players online; returned as it is, never copied
     */
    BenchmarkGameServer(List<UUID> online) {
        this.online = online;
    }

    @Override
    public List<UUID> getOnlinePlayers() {
        return online;
    }

    @Override
    public UUID getUUIDOf(UUID player) {
        return player;
    }

    @Override
    public boolean checkPlayerHasChannel(UUID player, CustomPacketPayload.Type<?> type) {
        return true;
    }

    @Override
    public void sendTo(UUID player, CustomPacketPayload payload) {}

    @Override
    public int getStatValueOf(UUID player, Stat<?> stat) {
        return 1;
    }
}
