package io.github.foursource.scoreline.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * A player logged into the game-test server through a client that exists only in memory.
 * <p>
 * The player joins the way a real one does: it is in the player list, the login event fires (so Scoreline sends its hello), and it can send and receive payloads. Its connection runs over a Netty
 * {@link EmbeddedChannel} instead of a socket: what the server sends stays in the channel's outbound queue as packet objects, where {@link #receivedPayloads} reads it. Payloads are never encoded to
 * bytes, so the codecs are not tested here (the unit tests and golden files cover them).
 * <p>
 * Minecraft's own mock players do not fit: none of them has a connection whose channels are set up, so they can neither send nor receive Scoreline's payloads. This follows NeoForge's own test
 * framework ({@code ExtendedGameTestHelper.makeTickingMockServerPlayerInLevel}).
 * <p>
 * Each client logs out when its test ends, so it does not show up in later tests' responses.
 */
public final class MockClient {
    /** Which client the mock stands for. */
    public enum Kind {
        /** A client with NeoForge and Scoreline: all registered channels are set up. */
        MODDED,
        /**
         * A client without mods: no channels, as NeoForge marks a real vanilla client. A modded client whose Scoreline channel version differs never gets this far: NeoForge's negotiation fails on a
         * version mismatch even for optional channels, so the client cannot connect.
         */
        VANILLA
    }

    /** The account; kept so {@link #rejoin} logs in with the same UUID. */
    private final GameProfile profile;
    /** Kept so {@link #rejoin} comes back as the same kind of client. */
    private final Kind kind;
    private final ServerPlayer player;
    /** The in-memory channel; its outbound queue holds everything the server sent to this client. */
    private final EmbeddedChannel channel;
    /** Set by {@link #disconnect}, so the player is logged out at most once. */
    private boolean disconnected;

    private MockClient(GameProfile profile, Kind kind, ServerPlayer player, EmbeddedChannel channel) {
        this.profile = profile;
        this.kind = kind;
        this.player = player;
        this.channel = channel;
    }

    /**
     * Logs a new player in, with a fresh random UUID, so it never collides with another test's player.
     *
     * @param helper the running test's helper; the player joins its level and logs out when the test ends
     * @param kind the kind of client
     * @return the logged-in client
     */
    public static MockClient connect(GameTestHelper helper, Kind kind) {
        String name = kind == Kind.MODDED ? "scoreline-mock" : "scoreline-vanilla-mock";
        return login(helper, new GameProfile(UUID.randomUUID(), name), kind);
    }

    /**
     * Logs a player in with the given account. The order matters: the channels are set up before {@code placeNewPlayer}, because that fires the login event, and Scoreline sends its hello only to a
     * client that already has the channel.
     *
     * @param helper the running test's helper
     * @param profile the account
     * @param kind the kind of client
     * @return the logged-in client
     */
    private static MockClient login(GameTestHelper helper, GameProfile profile, Kind kind) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // The cookie carries the client's settings into the login; its connection type tells the player's listener whether the client is modded
        ConnectionType type = kind == Kind.MODDED ? ConnectionType.NEOFORGE : ConnectionType.OTHER;
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, type);
        // A subclass, not ServerPlayer itself: NeoForge's config sync skips players of a subclass,
        // as it does for its own test players, because they never ran the configuration phase
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation()) {};

        // The server's end of the connection; the embedded channel replaces the socket
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        if (kind == Kind.MODDED) {
            // Stands in for the configuration phase: NeoForge treats the client as having every registered channel
            NetworkRegistry.configureMockConnection(connection);
        }
        else {
            // What NeoForge does for a real vanilla client (NetworkRegistry.initializeOtherConnection): no channels
            ChannelAttributes.setPayloadSetup(connection, NetworkPayloadSetup.empty());
            ChannelAttributes.setConnectionType(connection, ConnectionType.OTHER);
        }
        server.getPlayerList().placeNewPlayer(connection, player, cookie);

        // A real client reports when it has loaded the world; until then the player cannot be hurt
        player.connection.markClientLoaded();

        MockClient client = new MockClient(profile, kind, player, channel);
        helper.testInfo.addListener(client.disconnectWhenDone());

        return client;
    }

    /**
     * Returns the server's player object for this client, for example to set its stats.
     *
     * @return the player
     */
    public ServerPlayer player() {
        return this.player;
    }

    /**
     * Lists the custom payloads the server has sent to this client so far, oldest first. Vanilla packets, such as those of the login, are left out.
     *
     * @param <T> the payload type
     * @param type the payload class to keep; {@code CustomPacketPayload.class} keeps all
     * @return the payloads of that type
     */
    public <T extends CustomPacketPayload> List<T> receivedPayloads(Class<T> type) {
        // Custom payloads travel inside a ClientboundCustomPayloadPacket
        return channel.outboundMessages().stream().filter(ClientboundCustomPayloadPacket.class::isInstance).map(message -> ((ClientboundCustomPayloadPacket) message).payload())
                .filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * Returns the custom payloads of a type received since the last call, and forgets everything the server sent so far. For long tests, so the outbound queue does not grow without bound.
     *
     * @param <T> the payload type
     * @param type the payload class to keep
     * @return the payloads of that type, oldest first
     */
    public <T extends CustomPacketPayload> List<T> takeReceivedPayloads(Class<T> type) {
        List<T> payloads = receivedPayloads(type);
        // The queue holds packet objects, not buffers, so releasing them frees nothing but the queue
        channel.releaseOutbound();
        return payloads;
    }

    /**
     * Creates the listener that logs this client out when its test ends, passed or failed.
     *
     * @return the listener, for {@code GameTestInfo#addListener}
     */
    private GameTestListener disconnectWhenDone() {
        return new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
                // nothing to do before the test runs
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                disconnect(Component.literal("Test finished"));
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                disconnect(Component.literal("Test finished"));
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
                // we never rerun tests
            }
        };
    }

    /**
     * Logs the player out the way the server kicks a real one: it leaves the player list and the logout event fires, so Scoreline forgets it. Does nothing if the player is already gone.
     *
     * @param reason the kick message a real client would show
     */
    public void disconnect(Component reason) {
        if (disconnected || player.hasDisconnected()) {
            return;
        }
        player.connection.disconnect(reason);
        this.disconnected = true;
    }

    /**
     * Logs the same account in again, as a new connection and a new player object, like a real rejoin. Minecraft saves the stats at logout and loads them at login, so they carry over. Call it only
     * after {@link #disconnect}: the server refuses a second login of a UUID that is still online.
     *
     * @param helper the running test's helper
     * @return the new client for the same account
     */
    public MockClient rejoin(GameTestHelper helper) {
        return login(helper, profile, kind);
    }

    /**
     * Tells whether the player is still on the server.
     *
     * @return true until the player was logged out or kicked
     */
    public boolean isConnected() {
        return !player.hasDisconnected();
    }

    /**
     * Sends a payload to the server, as a real client would. It enters where the server handles every decoded custom payload, so NeoForge's channel check and Scoreline's handler run; only the
     * decoding is skipped. The handler runs at once, so a reply is in {@link #receivedPayloads} when this returns.
     *
     * @param payload the payload to send
     */
    public void sendPayload(CustomPacketPayload payload) {
        player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(payload));
    }
}
