package io.github.foursource.scoreline.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

/**
 * Game tests that pin down Minecraft behaviour Scoreline relies on, so a Minecraft update that changes it fails the build (spec, Testing).
 */
public final class VanillaBehaviorTests {
    private VanillaBehaviorTests() {}

    /**
     * Lists this group's tests for {@link GameTestRegistration}.
     *
     * @return the tests of this group
     */
    static List<GameTestCase> tests() {
        return List.of(new GameTestCase("t47_death_counts_once", VanillaBehaviorTests::t47_deathCountsOnce));
    }

    // Bug: Minecraft changed where deaths are counted, so Scoreline's deaths column would be wrong
    private static void t47_deathCountsOnce(GameTestHelper helper) {
        MockClient client = MockClient.connect(helper, MockClient.Kind.MODDED);
        ServerPlayer player = client.player();
        Stat<?> deaths = Stats.CUSTOM.get(Stats.DEATHS);
        // Compared with the value before, so the test does not depend on a fresh player starting at 0
        int before = player.getStats().getValue(deaths);

        // Damage that ignores invulnerability, so the player's game mode does not matter
        player.kill(helper.getLevel());

        int after = player.getStats().getValue(deaths);
        helper.assertTrue(after == before + 1, "deaths went from " + before + " to " + after);
        helper.succeed();
    }
}
