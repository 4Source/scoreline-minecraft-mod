package io.github.foursource.scoreline.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.CapturedLog;
import io.github.foursource.scoreline.platform.services.Platform;

public class ServicesTest {

    /** A service interface that no file under {@code META-INF/services} registers. */
    interface UnregisteredService {}

    /**
     * Bug: the registration file still has the old interface name, so nothing is loaded
     */
    @Test
    void t199_platformIsTheRegisteredFake() {
        assertInstanceOf(FakePlatform.class, Services.PLATFORM);
    }

    /**
     * Bug: load returns null or the wrong object; the DEBUG line removed
     */
    @Test
    void t200_loadReturnsRegisteredImplementationAndLogsIt() {
        try (CapturedLog log = CapturedLog.start()) {
            Platform loaded = Services.load(Platform.class);

            assertInstanceOf(FakePlatform.class, loaded);
            assertEquals(List.of("DEBUG [scoreline] Loaded " + loaded + " for service interface " + Platform.class.getName()), log.lines());
        }
    }

    /**
     * Bug: a missing service is ignored (null), or the message hides which one
     */
    @Test
    void t201_loadWithoutRegistrationThrowsNamingTheInterface() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Services.load(UnregisteredService.class));

        assertTrue(e.getMessage().contains(UnregisteredService.class.getName()), e.getMessage());
    }
}
