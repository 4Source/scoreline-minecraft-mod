package io.github.foursource.scoreline.platform.services;

/**
 * Loader-specific behavior that common code needs. Each loader implements it; {@link io.github.foursource.scoreline.platform.Services#PLATFORM} holds the instance.
 */
public interface Platform {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getName();

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}