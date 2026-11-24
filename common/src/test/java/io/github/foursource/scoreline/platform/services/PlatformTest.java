package io.github.foursource.scoreline.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.platform.FakePlatform;

public class PlatformTest {

    /**
     * Bug: the two words swapped
     */
    @Test
    void t202_environmentNameFollowsDevelopmentFlag() {
        assertEquals("development", new FakePlatform(true).getEnvironmentName());
        assertEquals("production", new FakePlatform(false).getEnvironmentName());
    }
}
