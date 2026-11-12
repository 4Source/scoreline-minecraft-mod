package io.github.foursource.scoreline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.foursource.scoreline.platform.Services;

/**
 * Shared entry point of Scoreline. Each loader module calls {@link #init(String)} once when the mod is loaded.
 * <p>
 * Code in {@code common} may use vanilla Minecraft only; loader-specific
 * behavior goes through
 * {@link io.github.foursource.scoreline.platform.Services}.
 */
public final class ScorelineCommon {
    public static final Logger LOG = LoggerFactory.getLogger(Constants.MOD_NAME);

    private ScorelineCommon() {
    }

    /**
     * Starts the shared part of the mod.
     *
     * @param modVersion the mod's version as read by the loader from the mod
     *                   metadata, for example {@code 0.1.0}
     */
    public static void init(String modVersion) {
        // The smoke run and the build workflow search the log for exactly this line.
        LOG.info("{} {} loaded", Constants.MOD_NAME, modVersion);
        LOG.info("Running at {} in a {} environment", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }
}