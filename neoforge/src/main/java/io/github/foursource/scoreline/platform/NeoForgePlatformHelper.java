package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * The NeoForge implementation of {@link IPlatformHelper}, registered in {@code META-INF/services}.
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    /**
     * Called by {@link java.util.ServiceLoader}, which needs a public no-arg constructor.
     */
    public NeoForgePlatformHelper() {}

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.getCurrent().isProduction();
    }
}