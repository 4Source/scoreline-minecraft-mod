package io.github.foursource.scoreline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.foursource.scoreline.platform.Services;

/**
 * Shared entry point of Scoreline. Each loader module calls {@link #init(String)} once when the mod is loaded.
 * <p>
 * Code in {@code common} may use vanilla Minecraft only. Loader-specific behavior is passed in by the loader module (for example a {@link io.github.foursource.scoreline.server.GameServer}); only
 * start-up code like {@link #init(String)} reads it through {@link io.github.foursource.scoreline.platform.Services}.
 */
public final class ScorelineCommon {
    /**
     * The mod's logger, named {@code scoreline}.
     */
    public static final Logger LOG = LoggerFactory.getLogger(Constants.MOD_ID);
    /**
     * The logger for the network, named {@code scoreline.net}.
     * <p>
     * As a child of {@link #LOG}, it follows {@code scoreline}'s settings unless configured on its own.
     */
    public static final Logger NET_LOG = LoggerFactory.getLogger(Constants.MOD_ID + ".net");
    /**
     * The logger for payloads, named {@code scoreline.net.payload}.
     * <p>
     * As a child of {@link #NET_LOG}, it follows {@code scoreline}'s settings unless configured on its own.
     */
    public static final Logger PAYLOAD_LOG = LoggerFactory.getLogger(Constants.MOD_ID + ".net.payload");

    private ScorelineCommon() {}

    /**
     * Starts the shared part of the mod.
     *
     * @param modVersion the mod's version as read by the loader from the mod metadata, for example {@code 0.1.0}
     */
    public static void init(String modVersion) {
        // The smoke run and the build workflow search the log for exactly this line.
        LOG.info("{} {} loaded", Constants.MOD_NAME, modVersion);
        LOG.info("Running at {} in a {} environment", Services.PLATFORM.getName(), Services.PLATFORM.getEnvironmentName());
    }
}
