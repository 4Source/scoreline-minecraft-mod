package io.github.foursource.scoreline.tools.perf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class PerfCompareMainTest {
    @TempDir
    Path dir;

    private Path baseline;
    private Path current;
    private Map<String, String> env = Map.of();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void folders() throws IOException {
        baseline = Files.createDirectories(dir.resolve("baseline"));
        current = Files.createDirectories(dir.resolve("new"));
    }

    /** Runs the comparison with the given options before the two folders. */
    private int run(String... options) {
        List<String> args = new ArrayList<>(List.of(options));
        args.add(baseline.toString());
        args.add(current.toString());
        return runRaw(args.toArray(String[]::new));
    }

    private int runRaw(String... args) {
        return PerfCompareMain.run(args, env, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    /**
     * Writes a JMH result file into {@code version/round-<round>/}: one benchmark per (name, time score, allocation score) triple; the names may carry a {@code [players=n]} parameter.
     */
    private static void jmh(Path version, int round, Object... nameTimeAllocation) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < nameTimeAllocation.length; i += 3) {
            String name = (String) nameTimeAllocation[i];
            String params = "";
            int bracket = name.indexOf('[');
            if (bracket >= 0) {
                String[] param = name.substring(bracket + 1, name.length() - 1).split("=");
                params = String.format(Locale.ROOT, "\"params\": {\"%s\": \"%s\"},", param[0], param[1]);
                name = name.substring(0, bracket);
            }
            json.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, """
                    {"benchmark": "io.github.foursource.scoreline.net.%s", %s
                     "primaryMetric": {"score": %s, "scoreUnit": "ns/op"},
                     "secondaryMetrics": {"gc.alloc.rate.norm": {"score": %s, "scoreUnit": "B/op"}}}
                    """, name, params, nameTimeAllocation[i + 1], nameTimeAllocation[i + 2]));
        }
        json.append("]");
        Path folder = Files.createDirectories(version.resolve("round-" + round));
        Files.writeString(folder.resolve(PerfCompareMain.JMH_FILE), json);
    }

    /**
     * Bug: the command line counts an improvement as a failure
     */
    @Test
    void t181_improvementExitsZero() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.encodeHello", 300, 100);
        jmh(current, 1, "ProtocolBenchmark.encodeHello", 199, 100);

        assertEquals(PerfCompareMain.OK, run());
        assertTrue(out().contains("IMPROVED"), out());
    }

    /**
     * Bug: the exit code ignores NEW and REMOVED
     */
    @Test
    void t183_newPassesRemovedFails() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.kept", 100, 100, "ProtocolBenchmark.old", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.kept", 100, 100, "ProtocolBenchmark.added", 100, 100);

        assertEquals(PerfCompareMain.REGRESSION, run());
        assertTrue(out().contains("| ProtocolBenchmark.added (time) | ns/op | - | 100.00 | - | NEW |"), out());
        assertTrue(out().contains("| ProtocolBenchmark.old (time) | ns/op | 100.00 | - | - | **REMOVED** (missing in the new code: the benchmark broke) |"), out());
    }

    /**
     * Bug: with each version running its own benchmarks, a deleted benchmark fails; or the table does not say that the sources differed
     */
    @Test
    void t184_removedPassesInTheFallback() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.kept", 100, 100, "ProtocolBenchmark.old", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.kept", 100, 100);

        assertEquals(PerfCompareMain.OK, run("--fallback"));
        assertTrue(out().startsWith("The new benchmarks do not compile against the baseline"), out());
        assertTrue(out().contains("| REMOVED (each version ran its own benchmarks) |"), out());
    }

    /**
     * Bug: metrics keyed by benchmark name without parameters, so values of different player counts mix
     */
    @Test
    void t100_onlyTheSlowerPlayerCountRegresses() throws IOException {
        for (int round = 1; round <= 3; round++) {
            jmh(baseline, round, "ProtocolBenchmark.encodeStatsResponse[players=50]", 1000, 100, "ProtocolBenchmark.encodeStatsResponse[players=200]", 4000, 100);
            jmh(current, round, "ProtocolBenchmark.encodeStatsResponse[players=50]", 1000, 100, "ProtocolBenchmark.encodeStatsResponse[players=200]", 8000, 100);
        }

        assertEquals(PerfCompareMain.REGRESSION, run());
        assertTrue(out().contains("| ProtocolBenchmark.encodeStatsResponse[players=50] (time) | ns/op | 1000.00 | 1000.00 | 1.00 | OK |"), out());
        assertTrue(out().contains("| ProtocolBenchmark.encodeStatsResponse[players=200] (time) | ns/op | 4000.00 | 8000.00 | 2.00 | **REGRESSION** |"), out());
    }

    /**
     * Bug: the first regression stops the report; or the exit code ignores it
     */
    @Test
    void t101_oneRegressionExitsOneAndListsAll() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100, "ProtocolBenchmark.b", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.a", 300, 100, "ProtocolBenchmark.b", 100, 100);

        assertEquals(PerfCompareMain.REGRESSION, run());
        assertTrue(out().contains("ProtocolBenchmark.a (time)"), out());
        assertTrue(out().contains("ProtocolBenchmark.b (allocation)"), out());
        assertTrue(out().contains("4 metrics, 1 failing, 0 improved"), out());
    }

    /**
     * Bug: broken input read as "no results", so the job is green
     */
    @Test
    void t102_invalidJsonExitsTwoAndNamesTheFile() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100);
        Path round = Files.createDirectories(current.resolve("round-1"));
        Path broken = round.resolve(PerfCompareMain.JMH_FILE);
        Files.writeString(broken, "[{\"benchmark\": ");

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains(broken.toString()), err());
    }

    /**
     * Bug: a new folder without results (the benchmarks did not run) passes as "nothing regressed"; folders that are not rounds are read
     */
    @Test
    void t175_newFolderWithoutResultsExitsTwo() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100);
        Files.createDirectories(current.resolve("round-1"));
        Files.createDirectories(current.resolve("logs"));
        jmh(current.resolve("logs"), 1, "ProtocolBenchmark.a", 100, 100);

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains("no results of the new code"), err());
    }

    /**
     * Bug: a missing folder (wrong path in the script) read as an empty baseline
     */
    @Test
    void t191_missingFolderExitsTwo() throws IOException {
        jmh(current, 1, "ProtocolBenchmark.a", 100, 100);
        baseline = dir.resolve("does-not-exist");

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains("does-not-exist"), err());
    }

    /**
     * Bug: wrong arguments run a comparison of nothing
     */
    @Test
    void t176_wrongArgumentsExitTwo() {
        assertEquals(PerfCompareMain.BAD_INPUT, runRaw("only-one"));
        assertEquals(PerfCompareMain.BAD_INPUT, runRaw("--speed", "a", "b"));
        assertTrue(err().contains("unknown option --speed"), err());
        assertEquals(PerfCompareMain.BAD_INPUT, runRaw("a", "b", "--accepted"));
        assertTrue(err().contains("--accepted needs a value"), err());
    }

    /**
     * Bug: a benchmark that failed in one round is compared on fewer samples without anyone noticing
     */
    @Test
    void t135_differentSampleCountsExitTwo() throws IOException {
        for (int round = 1; round <= 3; round++) {
            jmh(baseline, round, "ProtocolBenchmark.a", 100, 100);
        }
        jmh(current, 1, "ProtocolBenchmark.a", 100, 100);
        jmh(current, 2, "ProtocolBenchmark.a", 100, 100);

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains("ProtocolBenchmark.a (time)"), err());
    }

    /**
     * Bug: a comparison that measured no baseline passes as "nothing regressed"; or a baseline without benchmarks fails although it is allowed
     */
    @Test
    void t138_emptyBaselineNeedsPermission() throws IOException {
        jmh(current, 1, "ProtocolBenchmark.a", 100, 100);

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains(PerfCompareMain.ALLOW_NO_BASELINE), err());

        env = Map.of(PerfCompareMain.ALLOW_NO_BASELINE, "1");
        assertEquals(PerfCompareMain.OK, run());
        assertEquals("no baseline: nothing compared", out().strip());
    }

    /**
     * Bug: a baseline whose rounds hold no benchmarks fails
     */
    @Test
    void t145_baselineRoundsWithoutResultsAreNoBaseline() throws IOException {
        Files.createDirectories(baseline.resolve("round-1"));
        jmh(current, 1, "ProtocolBenchmark.a", 100, 100);
        env = Map.of(PerfCompareMain.ALLOW_NO_BASELINE, "1");

        assertEquals(PerfCompareMain.OK, run());
        assertEquals("no baseline: nothing compared", out().strip());
    }

    /**
     * Bug: the accepted file silences everything, or an old entry hides a later regression; a malformed entry is read as accepting nothing
     */
    @Test
    void t139_acceptedRegressionsPassOthersFail() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100, "ProtocolBenchmark.b", 100, 100, "ProtocolBenchmark.c", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.a", 300, 100, "ProtocolBenchmark.b", 100, 100, "ProtocolBenchmark.c", 100, 100);
        Path accepted = dir.resolve("perf-accepted.txt");
        Files.writeString(accepted, "# accepted for this release\n\n ProtocolBenchmark.a (time) \tthe hello grew\nProtocolBenchmark.b (time)\tno longer slower\n");

        assertEquals(PerfCompareMain.OK, run("--accepted", accepted.toString()));
        assertTrue(out().contains("| ACCEPTED (the hello grew) |"), out());
        assertTrue(out().contains("accepted but not needed: ProtocolBenchmark.b (time)"), out());

        jmh(current, 1, "ProtocolBenchmark.a", 300, 100, "ProtocolBenchmark.b", 100, 100, "ProtocolBenchmark.c", 300, 100);
        assertEquals(PerfCompareMain.REGRESSION, run("--accepted", accepted.toString()));
        assertTrue(out().contains("| ProtocolBenchmark.c (time) | ns/op | 100.00 | 300.00 | 3.00 | **REGRESSION** |"), out());

        for (String malformed : List.of("ProtocolBenchmark.a (time) the hello grew\n", "\tthe hello grew\n", "ProtocolBenchmark.a (time)\t \n")) {
            Files.writeString(accepted, malformed);
            assertEquals(PerfCompareMain.BAD_INPUT, run("--accepted", accepted.toString()), malformed);
        }
        assertTrue(err().contains("needs a key, a tab and a reason"), err());

        assertEquals(PerfCompareMain.BAD_INPUT, run("--accepted", dir.resolve("missing.txt").toString()));
        assertTrue(err().contains("missing.txt"), err());
    }

    /**
     * Bug: pull request mode compares time too, so noise fails pull requests
     */
    @Test
    void t172_allocationOnlyIgnoresTime() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.a", 1000, 100);

        assertEquals(PerfCompareMain.OK, run("--allocation-only"));
        assertFalse(out().contains("(time)"), out());
    }

    /**
     * Bug: one noisy fork fails a pull request; or the extra measurement hides a regression that repeats; or the script re-runs the wrong benchmarks
     */
    @Test
    void t156_regressionsAreListedAndCountOnlyIfTheyRepeat() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a[players=50]", 100, 100, "ProtocolBenchmark.b", 100, 100, "ProtocolBenchmark.c", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.a[players=50]", 100, 1000, "ProtocolBenchmark.b", 100, 1000, "ProtocolBenchmark.c", 100, 100);
        Path regressions = dir.resolve("regressions.txt");

        assertEquals(PerfCompareMain.REGRESSION, run("--allocation-only", "--regressions", regressions.toString()));
        assertEquals("ProtocolBenchmark.a\nProtocolBenchmark.b\n", Files.readString(regressions));

        // The extra measurement: a is back to normal, b still allocates more
        Path recheckBaseline = dir.resolve("recheck-baseline");
        Path recheckNew = dir.resolve("recheck-new");
        jmh(recheckBaseline, 1, "ProtocolBenchmark.a[players=50]", 100, 100, "ProtocolBenchmark.b", 100, 100);
        jmh(recheckNew, 1, "ProtocolBenchmark.a[players=50]", 100, 100, "ProtocolBenchmark.b", 100, 1000);

        assertEquals(PerfCompareMain.REGRESSION, run("--allocation-only", "--recheck", recheckBaseline.toString(), recheckNew.toString()));
        assertTrue(out().contains("| ProtocolBenchmark.a[players=50] (allocation) | B/op | 100.00 | 1000.00 | 10.00 | OK (regression not repeated when measured once more) |"), out());

        jmh(recheckNew, 1, "ProtocolBenchmark.a[players=50]", 100, 100, "ProtocolBenchmark.b", 100, 100);
        assertEquals(PerfCompareMain.OK, run("--allocation-only", "--recheck", recheckBaseline.toString(), recheckNew.toString()));

        // An extra measurement that measured nothing (the script's run broke) must not clear anything
        Path emptyBaseline = Files.createDirectories(dir.resolve("empty-baseline").resolve("round-1"));
        Path emptyNew = Files.createDirectories(dir.resolve("empty-new"));
        assertEquals(PerfCompareMain.BAD_INPUT, run("--allocation-only", "--recheck", emptyBaseline.getParent().toString(), emptyNew.toString()));
        assertEquals(PerfCompareMain.BAD_INPUT, run("--allocation-only", "--recheck", recheckBaseline.toString(), emptyNew.toString()));
        assertTrue(err().contains("the extra measurement has no results"), err());
    }

    /**
     * Bug: a pull request that changes the time unit compares numbers of different units
     */
    @Test
    void t136_unitMismatchExitsTwo() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100);
        Path round = Files.createDirectories(current.resolve("round-1"));
        Files.writeString(round.resolve(PerfCompareMain.JMH_FILE), """
                [{"benchmark": "io.github.foursource.scoreline.net.ProtocolBenchmark.a", "primaryMetric": {"score": 0.1, "scoreUnit": "us/op"}}]
                """);

        assertEquals(PerfCompareMain.BAD_INPUT, run());
        assertTrue(err().contains("ns/op") && err().contains("us/op"), err());
    }

    /**
     * Bug: the list of regressed benchmarks cannot be written and the script measures nothing once more, silently
     */
    @Test
    void t178_unwritableRegressionsFileExitsTwo() throws IOException {
        jmh(baseline, 1, "ProtocolBenchmark.a", 100, 100);
        jmh(current, 1, "ProtocolBenchmark.a", 100, 1000);

        assertEquals(PerfCompareMain.BAD_INPUT, run("--regressions", dir.toString()));
        assertTrue(err().contains("cannot read or write"), err());
    }
}
