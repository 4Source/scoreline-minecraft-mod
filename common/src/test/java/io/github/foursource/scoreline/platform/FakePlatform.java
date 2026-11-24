package io.github.foursource.scoreline.platform;

import io.github.foursource.scoreline.platform.services.Platform;

/**
 * Test implementation of {@link Platform}, named Fake; registered in common/src/test/resources/META-INF/services.
 */
public class FakePlatform implements Platform {
    /** The answer of {@link #isDevelopmentEnvironment()}; fixed per instance. */
    private final boolean isDevelopmentEnvironment;

    /**
     * Creates a fake for a production environment. Called by {@link java.util.ServiceLoader}, which needs a public no-arg constructor.
     */
    public FakePlatform() {
        this(false);
    }

    /**
     * Creates a fake for either environment. A test that needs a development environment creates its own instance instead of changing the shared one in {@link Services#PLATFORM}.
     *
     * @param isDevelopmentEnvironment what {@link #isDevelopmentEnvironment()} returns
     */
    public FakePlatform(boolean isDevelopmentEnvironment) {
        this.isDevelopmentEnvironment = isDevelopmentEnvironment;
    }

    @Override
    public String getName() {

        return "Fake";
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return isDevelopmentEnvironment;
    }
}
