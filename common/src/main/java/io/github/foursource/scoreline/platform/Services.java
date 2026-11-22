package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.ScorelineCommon;
import io.github.foursource.scoreline.platform.services.INetworkHelper;
import io.github.foursource.scoreline.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Gives {@code common} code access to loader-specific behaviour.
 * <p>
 * Each loader module provides an implementation of every service interface and names it in a file under {@code META-INF/services/} whose name is the interface's fully qualified name. Java's
 * {@link ServiceLoader} picks up that implementation at runtime.
 */
public final class Services {

    /**
     * The current loader's platform helper.
     */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * The current loader's network access: channel checks and sending payloads.
     */
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    private Services() {}

    /**
     * Loads the loader's implementation of a service interface.
     *
     * @param clazz the service interface
     * @param <T> the service type
     * @return the first implementation found
     * @throws IllegalStateException if no loader module provides an implementation
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader()).findFirst().orElseThrow(() -> new IllegalStateException("Failed to load service for " + clazz.getName()));
        ScorelineCommon.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
