package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.ScorelineCommon;
import io.github.foursource.scoreline.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Gives {@code common} code access to loader-specific behaviour.
 * <p>
 * Each loader module provides an implementation of every service interface and names it in a file under {@code META-INF/services/} whose name is the interface's fully qualified name. Java's
 * {@link ServiceLoader} picks up that implementation at runtime.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader()).findFirst().orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        ScorelineCommon.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
