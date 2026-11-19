package io.github.foursource.scoreline.gametest;

import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * One game test, as a test group hands it to {@link GameTestRegistration}.
 *
 * @param path the test's ID without the namespace, starting with its plan test ID (for example {@code t121_hello_sent_at_login}); lowercase letters, digits and {@code _ - . /} only, as Minecraft IDs
 * require
 * @param function the test itself; it fails by throwing, for example through a failed assert, and passes by calling {@link GameTestHelper#succeed()}
 * @param maxTicks the ticks after which the test fails if it has not passed (20 ticks are one second)
 */
public record GameTestCase(String path, Consumer<GameTestHelper> function, int maxTicks) {
    /**
     * Creates a test that must pass within one second, enough for every test that does not wait for the game.
     *
     * @param path the test's ID without the namespace
     * @param function the test itself
     */
    public GameTestCase(String path, Consumer<GameTestHelper> function) {
        this(path, function, 20);
    }
}
