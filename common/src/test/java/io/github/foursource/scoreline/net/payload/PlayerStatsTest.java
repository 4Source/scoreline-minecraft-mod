package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class PlayerStatsTest {
    /**
     * Bug: List.copyOf dropped, so the entry changes when the caller's list changes
     */
    @Test
    void t56_playerStatsCopiesValues() {
        List<Long> values = new ArrayList<>(List.of(72_000L, 3L));
        PlayerStats stats = new PlayerStats(new UUID(1, 2), values);

        values.set(0, 0L);
        values.add(99L);

        assertEquals(List.of(72_000L, 3L), stats.values());
    }

    /**
     * Bug: a null reaches the encoder and fails on the network thread when sending, far from the code that built it
     */
    @Test
    void t62_playerStatsRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new PlayerStats(null, List.of(72_000L, 3L)));
        // List.of cannot hold null, so Arrays.asList builds the list with a null value
        assertThrows(NullPointerException.class, () -> new PlayerStats(new UUID(1, 2), Arrays.asList(72_000L, null)));
    }
}
