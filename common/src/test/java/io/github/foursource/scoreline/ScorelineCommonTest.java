package io.github.foursource.scoreline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.platform.Services;

public class ScorelineCommonTest {

    /**
     * Bug: the start line changed; the smoke test and the build workflow search for exactly it
     */
    @Test
    void t203_initLogsVersionThenPlatform() {
        // Loads Services before the capture; otherwise its DEBUG line from loading the platform depends on the test order.
        assertNotNull(Services.PLATFORM);
        try (CapturedLog log = CapturedLog.start()) {
            ScorelineCommon.init("1.2.3");

            assertEquals(List.of("INFO [scoreline] Scoreline 1.2.3 loaded", "INFO [scoreline] Running at Fake in a production environment"), log.lines());
        }
    }
}
