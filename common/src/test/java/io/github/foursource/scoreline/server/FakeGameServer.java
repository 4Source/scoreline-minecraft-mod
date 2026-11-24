package io.github.foursource.scoreline.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.stats.Stat;

/**
 * A game server for tests: its players are {@link FakePlayer}s, and it records every payload sent instead of sending it.
 */
class FakeGameServer implements GameServer<FakePlayer> {
    /** A payload {@link #sendTo} was asked to send, and to whom. */
    record Sent(FakePlayer player, CustomPacketPayload payload) {}

    /** The players online, in the server's order; tests change it. */
    final List<FakePlayer> online = new ArrayList<>();

    /** Every payload sent so far, in order. */
    final List<Sent> sent = new ArrayList<>();

    @Override
    public List<FakePlayer> getOnlinePlayers() {
        return online;
    }

    @Override
    public UUID getUUIDOf(FakePlayer player) {
        return player.uuid();
    }

    @Override
    public boolean checkPlayerHasChannel(FakePlayer player, CustomPacketPayload.Type<?> type) {
        return player.channels().contains(type);
    }

    @Override
    public void sendTo(FakePlayer player, CustomPacketPayload payload) {
        sent.add(new Sent(player, payload));
    }

    @Override
    public int getStatValueOf(FakePlayer player, Stat<?> stat) {
        Integer value = player.values().get(stat);
        if (value == null) {
            throw new IllegalStateException("no value of " + stat.getName() + " for " + player.uuid());
        }
        return value;
    }
}
