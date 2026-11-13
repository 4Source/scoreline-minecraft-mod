package io.github.foursource.scoreline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.foursource.scoreline.platform.Services;

/**
 * Shared entry point of Scoreline.
 * <p>
 * Code in {@code common} may use vanilla Minecraft only; loader-specific
 * behavior goes through
 * {@link io.github.foursource.scoreline.platform.Services}.
 */
public final class ScorelineCommon {
    public static final Logger LOG = LoggerFactory.getLogger(Constants.MOD_NAME);

    private ScorelineCommon() {
    }

    public static void init() {

        LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }
}