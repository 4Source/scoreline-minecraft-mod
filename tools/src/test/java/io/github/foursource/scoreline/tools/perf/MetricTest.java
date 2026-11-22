package io.github.foursource.scoreline.tools.perf;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MetricTest {
    /**
     * Bug: a metric without samples reaches the median, which then reads past the end of an empty array
     */
    @Test
    void t174_metricWithoutSamplesIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Metric("XBenchmark.run", Metric.Kind.TIME, "ns/op", List.of()));

        assertTrue(e.getMessage().contains("XBenchmark.run"), e.getMessage());
    }
}
