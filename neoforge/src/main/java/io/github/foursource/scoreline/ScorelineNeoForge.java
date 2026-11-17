package io.github.foursource.scoreline;

import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import io.github.foursource.scoreline.server.ScorelineServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge entry point for both sides. NeoForge creates this class once when it loads the mod, on the client and on a dedicated server.
 * <p>
 * It registers the payloads and forwards server events to {@link ScorelineServer}; it holds no logic of its own. The client events go through {@link ScorelineNeoForgeClient}, which NeoForge creates
 * on the client only.
 */
@Mod(Constants.MOD_ID)
public final class ScorelineNeoForge {
    /**
     * NeoForge disconnects on a channel version mismatch, even for optional channels.
     * <p>
     * Never change this: protocol versions are negotiated through the hello (spec: Mixed mod versions).
     */
    private static final String CHANNEL_VERSION = "1";

    /**
     * The protocol's server side for the running server; {@code null} while no server runs. In singleplayer every world opened starts a new integrated server, so this is replaced each time.
     */
    private ScorelineServer server;

    /**
     * Called by NeoForge when it loads the mod.
     *
     * @param modBus the mod's own event bus, for setup events such as registering payloads
     * @param modContainer the mod's container, which holds its metadata
     */
    public ScorelineNeoForge(IEventBus modBus, ModContainer modContainer) {
        // The version comes from neoforge.mods.toml, which the build fills from gradle.properties.
        ScorelineCommon.init(modContainer.getModInfo().getVersion().toString());

        modBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    /**
     * Registers the three payloads on both sides. They are optional, so a vanilla client can join this server and a modded client can join a vanilla server.
     * <p>
     * The client-bound payloads are registered without a handler here: their handlers are client code and are added in {@link ScorelineNeoForgeClient}. Handlers run on the main thread, NeoForge's
     * default.
     *
     * @param event the registration event on the mod bus
     */
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional() returns a copy; only payloads registered through that copy are optional
        PayloadRegistrar registrar = event.registrar(CHANNEL_VERSION).optional();
        registrar.playToServer(StatsRequestPayload.TYPE, StatsRequestPayload.STREAM_CODEC, this::onStatsRequest);
        registrar.playToClient(HelloPayload.TYPE, HelloPayload.STREAM_CODEC);
        registrar.playToClient(StatsResponsePayload.TYPE, StatsResponsePayload.STREAM_CODEC);
    }

    /**
     * Answers a stats request and sends the response back to the requester, if there is one.
     *
     * @param payload the request
     * @param context the connection the request came from
     */
    private void onStatsRequest(StatsRequestPayload payload, IPayloadContext context) {
        if (server != null && context.player() instanceof ServerPlayer player) {
            server.onStatsRequest(player, payload.statIds(), Util.getMillis()).ifPresent(context::reply);
        }
    }

    /**
     * Creates the protocol's server side before any player can join.
     *
     * @param event the server start event
     */
    private void onServerStarting(ServerStartingEvent event) {
        server = new ScorelineServer();
    }

    /**
     * Drops the protocol's server side, so the next server starts fresh.
     *
     * @param event the server stop event
     */
    private void onServerStopped(ServerStoppedEvent event) {
        server = null;
    }

    /**
     * Forwards a login to the server side, which sends the hello.
     *
     * @param event the login event; its player is a {@link ServerPlayer} on the server
     */
    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (server != null && event.getEntity() instanceof ServerPlayer player) {
            server.onJoin(player);
        }
    }

    /**
     * Forwards a logout to the server side, which forgets the player.
     *
     * @param event the logout event; its player is a {@link ServerPlayer} on the server
     */
    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server != null && event.getEntity() instanceof ServerPlayer player) {
            server.onLeave(player);
        }
    }
}
