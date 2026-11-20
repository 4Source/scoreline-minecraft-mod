package io.github.foursource.scoreline.gametest;

import java.util.List;
import java.util.stream.Stream;

import io.github.foursource.scoreline.Constants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers every Scoreline game test; holds no tests itself.
 * <p>
 * FML finds this class through {@link EventBusSubscriber} while it scans the mod's classes, and attaches its static listeners to the mod bus. A test is registered twice: its function in the registry
 * of test functions, and a test instance that ties the function to a structure, an environment and a time limit. Each test's ID is {@code scoreline:} plus its {@link GameTestCase#path()}.
 * <p>
 * The class lives in the {@code gametest} source set, so the released jar does not carry it.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class GameTestRegistration {
    /** Set to {@code true} by the profiling run ({@code runProfileServer}) only; then the load of {@link LoadTest} is the only test registered. */
    static final String LOAD_TEST_PROPERTY = "scoreline.loadTest";

    /**
     * The tests of this run. Normally all groups (a new group is one more entry here). In the profiling run only {@link LoadTest}: other tests' requests would land in its timings, and the normal
     * run's {@code --tests scoreline:*} cannot leave a test out, so the load is not registered there at all.
     */
    private static final List<GameTestCase> TESTS = Boolean.getBoolean(LOAD_TEST_PROPERTY) ? LoadTest.tests()
            : Stream.of(JoinTests.tests(), StatsRequestTests.tests(), VanillaBehaviorTests.tests()).flatMap(List::stream).toList();

    private GameTestRegistration() {}

    /**
     * Puts each test function into the registry of test functions.
     * <p>
     * NeoForge fires this event once per registry; {@link RegisterEvent#register} only acts for the registry it names.
     *
     * @param event the registration event on the mod bus
     */
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        for (GameTestCase testCase : TESTS) {
            event.register(Registries.TEST_FUNCTION, Identifier.fromNamespaceAndPath(Constants.MOD_ID, testCase.path()), () -> testCase.function());
        }
    }

    /**
     * Registers the environment and one test instance per test. NeoForge fires this event only when game tests are enabled, as in the game-test server.
     *
     * @param event the registration event on the mod bus
     */
    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        // An environment without settings; the event cannot look up Minecraft's own minecraft:default
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "default"));

        for (GameTestCase testCase : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, testCase.path());
            // minecraft:empty is one block of air: the tests need players, not blocks. Required, so a failure counts towards the exit code
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), testCase.maxTicks(), 0, true)));
        }
    }
}
