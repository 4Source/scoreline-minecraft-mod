package io.github.foursource.scoreline.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.foursource.scoreline.net.Protocol;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * Game tests of how the server answers stats requests. Every request goes the way a real client sends it: through {@link MockClient#sendPayload}, NeoForge's dispatch, Scoreline's handler and the
 * request gate of the running server.
 * <p>
 * Tests may run in parallel, so other tests' players can be online too. Rows are therefore found by UUID, never counted.
 */
public final class StatsRequestTests {
    /** The stats every test asks for, in this order; the values in a row follow it. */
    private static final List<String> STATS = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);

    private StatsRequestTests() {}

    /**
     * Sets a player's playtime and deaths. Mock players are never ticked, so the values do not change during the test.
     *
     * @param player the player
     * @param playTime the playtime, in ticks
     * @param deaths the number of deaths
     */
    private static void setStats(ServerPlayer player, int playTime, int deaths) {
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.PLAY_TIME), playTime);
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.DEATHS), deaths);
    }

    /**
     * Finds a player's values in a response, or fails the test if the response has no row for the player.
     *
     * @param helper the running test's helper
     * @param response the response
     * @param player the player's UUID
     * @return the player's values, in the order of {@link #STATS}
     */
    private static List<Long> valuesOf(GameTestHelper helper, StatsResponsePayload response, UUID player) {
        for (PlayerStats row : response.players()) {
            if (row.player().equals(player)) {
                return row.values();
            }
        }
        throw helper.assertionException("no row for " + player);
    }

    /**
     * Lists this group's tests for {@link GameTestRegistration}.
     *
     * @return the tests of this group
     */
    static List<GameTestCase> tests() {
        return List.of(new GameTestCase("t48_reads_own_stats", StatsRequestTests::t48_readsOwnStats), new GameTestCase("t49_reads_both_stats", StatsRequestTests::t49_readsBothStats),
                new GameTestCase("t50_gate_limits_player_requests", StatsRequestTests::t50_gateLimitsPlayerRequests),
                new GameTestCase("t124_lists_vanilla_players", StatsRequestTests::t124_listsVanillaPlayers),
                new GameTestCase("t125_vanilla_request_is_rejected", StatsRequestTests::t125_vanillaRequestIsRejected), new GameTestCase("t151_hostile_client", StatsRequestTests::t151_hostileClient));
    }

    // Bug: stat mapping swapped or wrong stat read; wrong UUID; handler not registered or not replying
    private static void t48_readsOwnStats(GameTestHelper helper) {
        MockClient client = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(client.player(), 1234, 2);

        client.sendPayload(new StatsRequestPayload(STATS));

        List<StatsResponsePayload> responses = client.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responses.size() == 1, "expected 1 response, got " + responses.size());
        List<Long> values = valuesOf(helper, responses.getFirst(), client.player().getUUID());
        helper.assertTrue(values.equals(List.of(1234L, 2L)), "wrong values: " + values);
        helper.succeed();
    }

    // Bug: answers only the requester, or copies the requesters values to everyone
    private static void t49_readsBothStats(GameTestHelper helper) {
        MockClient clientA = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(clientA.player(), 100, 1);

        MockClient clientB = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(clientB.player(), 200, 3);

        clientA.sendPayload(new StatsRequestPayload(STATS));

        List<StatsResponsePayload> responsesA = clientA.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesA.size() == 1, "expected 1 response for client A, got " + responsesA.size());
        List<StatsResponsePayload> responsesB = clientB.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesB.size() == 0, "expected 0 response for client B, got " + responsesB.size());
        List<Long> valuesA = valuesOf(helper, responsesA.getFirst(), clientA.player().getUUID());
        helper.assertTrue(valuesA.equals(List.of(100L, 1L)), "wrong values for client A: " + valuesA);
        List<Long> valuesB = valuesOf(helper, responsesA.getFirst(), clientB.player().getUUID());
        helper.assertTrue(valuesB.equals(List.of(200L, 3L)), "wrong values for client B: " + valuesB);
        helper.succeed();
    }

    // Bug: the glue bypasses the gate
    private static void t50_gateLimitsPlayerRequests(GameTestHelper helper) {
        MockClient client = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(client.player(), 1234, 2);

        // All in the same tick, far less than one refill interval, so the last one finds the budget empty
        for (int i = 0; i < Protocol.BUDGET_CAPACITY + 1; i++) {
            client.sendPayload(new StatsRequestPayload(STATS));
        }

        List<StatsResponsePayload> responses = client.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responses.size() == Protocol.BUDGET_CAPACITY, "expected " + Protocol.BUDGET_CAPACITY + " response, got " + responses.size());
        helper.succeed();
    }

    // Bug: vanilla players left out of the list
    private static void t124_listsVanillaPlayers(GameTestHelper helper) {
        MockClient clientM = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(clientM.player(), 100, 1);

        MockClient clientV = MockClient.connect(helper, MockClient.Kind.VANILLA);
        setStats(clientV.player(), 200, 3);

        clientM.sendPayload(new StatsRequestPayload(STATS));

        List<StatsResponsePayload> responsesM = clientM.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesM.size() == 1, "expected 1 response for modded client, got " + responsesM.size());
        List<Long> valuesM = valuesOf(helper, responsesM.getFirst(), clientM.player().getUUID());
        helper.assertTrue(valuesM.equals(List.of(100L, 1L)), "wrong values for modded client: " + valuesM);
        List<Long> valuesV = valuesOf(helper, responsesM.getFirst(), clientV.player().getUUID());
        helper.assertTrue(valuesV.equals(List.of(200L, 3L)), "wrong values for vanilla client: " + valuesV);
        helper.succeed();
    }

    // Bug: the server answers a client that cannot receive the response, so the reply throws; NeoForge lets optional payloads in from any client
    private static void t125_vanillaRequestIsRejected(GameTestHelper helper) {
        MockClient clientV = MockClient.connect(helper, MockClient.Kind.VANILLA);
        setStats(clientV.player(), 200, 3);

        MockClient clientM = MockClient.connect(helper, MockClient.Kind.MODDED);
        setStats(clientM.player(), 100, 1);

        // Only a modified client would send this; a real vanilla client never does
        clientV.sendPayload(new StatsRequestPayload(STATS));

        List<StatsResponsePayload> responsesV = clientV.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesV.size() == 0, "expected 0 response for vanilla client, got " + responsesV.size());
        List<StatsResponsePayload> responsesM = clientM.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesM.size() == 0, "expected 0 response for modded client, got " + responsesM.size());

        helper.assertTrue(clientV.isConnected(), "expected vanilla client to still be connected");

        // The server still answers others, and the vanilla player stays in their lists
        clientM.sendPayload(new StatsRequestPayload(STATS));

        responsesM = clientM.receivedPayloads(StatsResponsePayload.class);
        helper.assertTrue(responsesM.size() == 1, "expected 1 response for modded client, got " + responsesM.size());

        List<Long> valuesM = valuesOf(helper, responsesM.getFirst(), clientM.player().getUUID());
        helper.assertTrue(valuesM.equals(List.of(100L, 1L)), "wrong values for modded client: " + valuesM);
        List<Long> valuesV = valuesOf(helper, responsesM.getFirst(), clientV.player().getUUID());
        helper.assertTrue(valuesV.equals(List.of(200L, 3L)), "wrong values for vanilla client: " + valuesV);

        helper.succeed();
    }

    // Bug: an unusual but legal request throws in the handler or disconnects the client; a misbehaving client disturbs other players
    private static void t151_hostileClient(GameTestHelper helper) {
        MockClient bystander = MockClient.connect(helper, MockClient.Kind.MODDED);
        MockClient oddRequester = MockClient.connect(helper, MockClient.Kind.MODDED);
        MockClient flooder = MockClient.connect(helper, MockClient.Kind.MODDED);

        // (2) The largest legal request: the most IDs, each as long as allowed, none of them known
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_STAT_IDS; i++) {
            String id = "questmod:unknown_" + i + "_";
            unknown.add(id + "x".repeat(Protocol.MAX_STAT_ID_LENGTH - id.length()));
        }
        oddRequester.sendPayload(new StatsRequestPayload(unknown));
        helper.assertTrue(oddRequester.receivedPayloads(StatsResponsePayload.class).isEmpty(), "expected no response to unknown IDs");

        // (1) A flood in one tick, far less than one refill interval: only the budget is answered
        for (int i = 0; i < Protocol.BUDGET_CAPACITY + 100; i++) {
            flooder.sendPayload(new StatsRequestPayload(STATS));
        }
        int answered = flooder.receivedPayloads(StatsResponsePayload.class).size();
        helper.assertTrue(answered == Protocol.BUDGET_CAPACITY, "expected " + Protocol.BUDGET_CAPACITY + " responses, got " + answered);

        // (3) Everyone is still connected, and other players are still answered
        helper.assertTrue(oddRequester.isConnected() && flooder.isConnected() && bystander.isConnected(), "a client was disconnected");
        bystander.sendPayload(new StatsRequestPayload(STATS));
        helper.assertTrue(bystander.receivedPayloads(StatsResponsePayload.class).size() == 1, "the bystander's request was not answered");
        helper.succeed();
    }
}
