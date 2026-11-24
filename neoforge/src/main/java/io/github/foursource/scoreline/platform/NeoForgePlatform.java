package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.platform.services.Platform;
import net.neoforged.fml.loading.FMLLoader;

/**
 * The NeoForge implementation of {@link Platform}, registered in {@code META-INF/services}.
 */
public class NeoForgePlatform implements Platform {

    /**
     * Called by {@link java.util.ServiceLoader}, which needs a public no-arg constructor.
     */
    public NeoForgePlatform() {}

    @Override
    public String getName() {

        return "NeoForge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.getCurrent().isProduction();
    }
}
