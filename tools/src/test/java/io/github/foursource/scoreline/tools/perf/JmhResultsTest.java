package io.github.foursource.scoreline.tools.perf;

import io.github.foursource.scoreline.tools.perf.Metric.Kind;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class JmhResultsTest {

    /** Two entries of a real {@code :common:jmh} run, raw data removed: one with a players parameter, one without. */
    private static Path sample() throws URISyntaxException {
        return Path.of(JmhResultsTest.class.getResource("/perf/jmh-sample.json").toURI());
    }

    /**
     * Bug: the package left in the name, or the parameters dropped, so player counts mix
     */
    @Test
    void t185_namesCarryTheParameters() throws Exception {
        List<Metric> metrics = JmhResults.read(sample());

        assertEquals(List.of("RequestGateBenchmark.admit[players=50]", "RequestGateBenchmark.admit[players=50]", "RequestTimerBenchmark.shouldSend", "RequestTimerBenchmark.shouldSend"),
                metrics.stream().map(Metric::name).toList());
    }

    /**
     * Bug: time read from a secondary metric, or allocation from gc.alloc.rate (MB/s) instead of gc.alloc.rate.norm (bytes per call)
     */
    @Test
    void t143_timeFromPrimaryScoreAllocationFromNorm() throws Exception {
        List<Metric> metrics = JmhResults.read(sample());

        Metric time = metrics.get(0);
        assertEquals(Kind.TIME, time.kind());
        assertEquals("ns/op", time.unit());
        assertEquals(List.of(80.06238943969305), time.samples());

        Metric allocation = metrics.get(1);
        assertEquals(Kind.ALLOCATION, allocation.kind());
        assertEquals("B/op", allocation.unit());
        assertEquals(List.of(312.0005575568098), allocation.samples());
    }

    /**
     * Bug: parameters named in the order JMH writes them, so the same benchmark gets two names
     */
    @Test
    void t169_parametersAreSortedByName(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("jmh.json");
        Files.writeString(file, """
                [{"benchmark": "a.b.XBenchmark.run", "params": {"size": "2", "players": "10"},
                  "primaryMetric": {"score": 1.0, "scoreUnit": "ns/op"}}]
                """);

        List<Metric> metrics = JmhResults.read(file);

        assertEquals(1, metrics.size(), "no allocation metric without -prof gc");
        assertEquals("XBenchmark.run[players=10,size=2]", metrics.get(0).name());
    }

    /**
     * Bug: a file missing a field JMH always writes read as a benchmark without results
     */
    @Test
    void t173_missingScoreIsAnError(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("jmh.json");
        Files.writeString(file, """
                [{"benchmark": "a.b.XBenchmark.run", "primaryMetric": {"scoreUnit": "ns/op"}}]
                """);

        PerfInputException e = assertThrows(PerfInputException.class, () -> JmhResults.read(file));
        assertTrue(e.getMessage().contains(file.toString()));
    }

    /**
     * Bug: NaN fails every comparison and so passes as OK
     */
    @Test
    void t137_nonFiniteScoreIsAnError(@TempDir Path dir) throws Exception {
        for (String score : new String[] { "NaN", "Infinity" }) {
            Path file = dir.resolve(score + ".json");
            Files.writeString(file, "[{\"benchmark\": \"a.b.XBenchmark.run\", \"primaryMetric\": {\"score\": " + score + ", \"scoreUnit\": \"ns/op\"}}]");

            PerfInputException e = assertThrows(PerfInputException.class, () -> JmhResults.read(file));
            assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
            assertTrue(e.getMessage().contains("XBenchmark.run has the score " + score), e.getMessage());
        }
    }

    /**
     * Bug: a run without -prof gc, or a benchmark with an empty parameter list, read as allocation 0 or named "Name[]"
     */
    @Test
    void t177_noAllocationWithoutProfilerAndNoEmptyBrackets(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("jmh.json");
        Files.writeString(file, """
                [{"benchmark": "a.b.XBenchmark.run", "params": {},
                  "primaryMetric": {"score": 1.0, "scoreUnit": "ns/op"},
                  "secondaryMetrics": {"gc.alloc.rate": {"score": 5.0, "scoreUnit": "MB/sec"}}}]
                """);

        List<Metric> metrics = JmhResults.read(file);

        assertEquals(1, metrics.size());
        assertEquals("XBenchmark.run", metrics.get(0).name());
    }
}
