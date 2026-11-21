package io.github.foursource.scoreline.gametest;

import java.util.List;
import java.util.stream.Collectors;

import io.github.foursource.scoreline.Constants;
import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Game tests of what the server sends when a player logs in.
 */
public final class JoinTests {
    private JoinTests() {}

    /** How often T-152 leaves and rejoins. */
    private static final int T152_REJOINS = 10;

    /**
     * Lists this group's tests for {@link GameTestRegistration}.
     *
     * @return the tests of this group
     */
    static List<GameTestCase> tests() {
        return List.of(new GameTestCase("t121_hello_sent_at_login", JoinTests::t121_helloSentAtLogin), new GameTestCase("t123_vanilla_gets_nothing", JoinTests::t123_vanillaGetsNothing),
                new GameTestCase("t152_logout_forgets_the_player", JoinTests::t152_logoutForgetsThePlayer));
    }

    // Bug: login listener missing, or onJoin checks the channel before it exists
    private static void t121_helloSentAtLogin(GameTestHelper helper) {
        MockClient client = MockClient.connect(helper, MockClient.Kind.MODDED);
        List<HelloPayload> hellos = client.receivedPayloads(HelloPayload.class);
        helper.assertTrue(hellos.size() == 1, "expected 1 hello, got " + hellos.size());
        helper.assertTrue(hellos.getFirst().protocolVersion() == 1, "wrong protocol: " + hellos.getFirst());
        helper.succeed();
    }

    // Bug: the hasChannel check in onJoin removed: NeoForge throws on sending, so vanilla players fail to join
    private static void t123_vanillaGetsNothing(GameTestHelper helper) {
        MockClient client = MockClient.connect(helper, MockClient.Kind.VANILLA);
        // Any Scoreline payload counts, not only the hello: nothing of ours may reach a vanilla client
        List<CustomPacketPayload> payloads = client.receivedPayloads(CustomPacketPayload.class);
        List<CustomPacketPayload> modPayloads = payloads.stream().filter(payload -> payload.type().id().getNamespace().equals(Constants.MOD_ID)).collect(Collectors.toList());
        helper.assertTrue(modPayloads.isEmpty(), "expected 0 scoreline payloads, got " + modPayloads.size());
        helper.assertTrue(client.isConnected(), "expected client to be connected, got " + client.isConnected());
        helper.succeed();
    }

    // Bug: the logout listener is missing or not wired, so the gate keeps the drained budget (onJoin keeps an existing one) and its map grows with every player who ever joined
    private static void t152_logoutForgetsThePlayer(GameTestHelper helper) {
        List<String> stats = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);
        MockClient client = MockClient.connect(helper, MockClient.Kind.MODDED);
        for (int round = 0; round <= T152_REJOINS; round++) {
            // The whole budget, in one tick: all answered only if this login started with a full one
            for (int i = 0; i < Protocol.BUDGET_CAPACITY; i++) {
                client.sendPayload(new StatsRequestPayload(stats));
            }
            int answered = client.receivedPayloads(StatsResponsePayload.class).size();
            helper.assertTrue(answered == Protocol.BUDGET_CAPACITY, "login " + round + ": expected " + Protocol.BUDGET_CAPACITY + " responses, got " + answered);
            if (round < T152_REJOINS) {
                client.disconnect(Component.literal("T-152 leaves"));
                client = client.rejoin(helper);
            }
        }
        helper.succeed();
    }
}
