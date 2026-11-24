package io.github.foursource.scoreline.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

/**
 * A connection for tests: the server's channels are a set the test changes, and every payload sent is recorded instead of sent.
 */
class FakeServerConnection implements ServerConnection {
    /** The payload types the server can receive; tests add and remove them. */
    final Set<Type<?>> channels = new HashSet<>();

    /** Every payload sent so far, in order. */
    final List<CustomPacketPayload> sent = new ArrayList<>();

    @Override
    public boolean checkServerHasChannel(Type<?> type) {
        return channels.contains(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        sent.add(payload);
    }
}
