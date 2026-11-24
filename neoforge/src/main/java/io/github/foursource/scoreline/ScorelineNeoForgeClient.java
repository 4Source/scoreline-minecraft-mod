package io.github.foursource.scoreline;

import io.github.foursource.scoreline.client.ScorelineClient;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import io.github.foursource.scoreline.platform.NeoForgeServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry point for the client. NeoForge creates it on the client only, after {@link ScorelineNeoForge}, so client classes such as {@code Minecraft} are never loaded on a dedicated server.
 * <p>
 * It registers the client-bound payload handlers and forwards client events to {@link ScorelineClient}; it holds no logic of its own.
 */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class ScorelineNeoForgeClient {
    /** The protocol's client side; one for the whole game session, reset on each disconnect. */
    private final ScorelineClient client;

    /**
     * Called by NeoForge when it loads the mod on the client.
     *
     * @param modBus the mod's own event bus, for setup events such as registering payload handlers
     */
    public ScorelineNeoForgeClient(IEventBus modBus) {
        client = new ScorelineClient(new NeoForgeServerConnection());

        modBus.addListener(this::registerClientPayloadHandlers);

        // The event class is given explicitly, so the subscription does not depend on reading the type from the lambda
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> client.onTick(Minecraft.getInstance().options.keyPlayerList.isDown(), Util.getMillis()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> client.onDisconnect());
    }

    /**
     * Adds the handlers for the payloads {@link ScorelineNeoForge} registered without one. They run on the client thread.
     *
     * @param event the client handler registration event on the mod bus
     */
    private void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(HelloPayload.TYPE, (payload, context) -> client.onHello(payload));
        event.register(StatsResponsePayload.TYPE, (payload, context) -> client.onStatsResponse(payload));
    }
}
