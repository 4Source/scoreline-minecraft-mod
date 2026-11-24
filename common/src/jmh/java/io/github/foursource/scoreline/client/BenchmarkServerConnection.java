package io.github.foursource.scoreline.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

/**
 * A connection that allocates nothing, so a benchmark measures only {@link ScorelineClient}: the server has every channel, and sending does nothing.
 */
class BenchmarkServerConnection implements ServerConnection {
    @Override
    public boolean checkServerHasChannel(Type<?> type) {
        return true;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {}
}
