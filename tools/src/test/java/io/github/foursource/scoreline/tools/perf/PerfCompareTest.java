package io.github.foursource.scoreline.tools.perf;

import io.github.foursource.scoreline.tools.perf.Metric.Kind;
import io.github.foursource.scoreline.tools.perf.PerfCompare.Options;
import io.github.foursource.scoreline.tools.perf.PerfCompare.Row;
import io.github.foursource.scoreline.tools.perf.PerfCompare.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PerfCompareTest {
    private static final String NAME = "ProtocolBenchmark.decodeHello";

    private static Metric time(Double... samples) {
        return new Metric(NAME, Kind.TIME, "ns/op", List.of(samples));
    }

    private static Metric allocation(Double... samples) {
        return new Metric(NAME, Kind.ALLOCATION, "B/op", List.of(samples));
    }

    /** Compares one baseline metric with one new metric of the same key. */
    private static Row compareOne(Metric baseline, Metric current) {
        List<Row> rows = PerfCompare.compare(List.of(baseline), List.of(current), Options.NONE).rows();
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    /**
     * Bug: starter, equal versions are not OK or the ratio is wrong
     */
    @Test
    void t92_equalTimesAreOk() {
        Row row = compareOne(time(100.0, 100.0, 100.0), time(100.0, 100.0, 100.0));

        assertEquals(Verdict.OK, row.verdict());
        assertEquals(1.0, row.ratio());
    }

    /**
     * Bug: off-by-one at the limit; the limit read as a percentage
     */
    @Test
    void t93_timeLimitIsOneAndAHalfTimesTheBaseline() {
        Metric baseline = time(100.0, 100.0, 100.0);

        assertEquals(Verdict.OK, compareOne(baseline, time(100.0, 100 * 1.5, 200.0)).verdict());
        assertEquals(Verdict.REGRESSION, compareOne(baseline, time(100.0, 100 * 1.5 + 1, 200.0)).verdict());
    }

    /**
     * Bug: the mean instead of the median, so one noisy round fails the pull request
     */
    @Test
    void t94_oneNoisyRoundDoesNotFail() {
        Row row = compareOne(time(100.0, 100.0, 100.0), time(100.0, 100.0, 1000.0));

        assertEquals(Verdict.OK, row.verdict());
        assertEquals(100.0, row.newMedian());
    }

    /**
     * Bug: the median of an even count picks one sample instead of the middle of two
     */
    @Test
    void t95_evenCountMedianIsTheMiddleOfTwo() {
        Row row = compareOne(time(100.0, 200.0), time(150.0, 150.0));

        assertEquals(Verdict.OK, row.verdict());
        assertEquals(150.0, row.baselineMedian());
        assertEquals(150.0, row.newMedian());
    }

    /**
     * Bug: the time limit used for allocation, so a 40% allocation increase passes
     */
    @Test
    void t96_allocationLimitIsTenPercent() {
        Metric baseline = allocation(1000.0);

        assertEquals(Verdict.OK, compareOne(baseline, allocation(1000 * 1.10)).verdict());
        assertEquals(Verdict.REGRESSION, compareOne(baseline, allocation(1000 * 1.10 + 1)).verdict());
    }

    /**
     * Bug: a ratio against 0 divides by zero or flags every byte; a path that allocated nothing starts making a small object per call and passes on the 32-byte margin
     */
    @Test
    void t97_smallAndZeroAllocations() {
        Metric ten = allocation(10.0);
        assertEquals(Verdict.OK, compareOne(ten, allocation(10.0 + 32)).verdict());
        assertEquals(Verdict.REGRESSION, compareOne(ten, allocation(43.0)).verdict());

        Metric zero = allocation(0.0001);
        assertEquals(Verdict.OK, compareOne(zero, allocation(7.0)).verdict());
        assertEquals(Verdict.REGRESSION, compareOne(zero, allocation(8.0)).verdict());

        Row fromExactZero = compareOne(allocation(0.0), allocation(0.0));
        assertEquals(Verdict.OK, fromExactZero.verdict());
        assertTrue(Double.isNaN(fromExactZero.ratio()));
    }

    /**
     * Bug: an improvement counted as a change that fails
     */
    @Test
    void t98_fasterTimeIsImproved() {
        Row row = compareOne(time(300.0, 300.0, 300.0), time(199.0, 199.0, 199.0));

        assertEquals(Verdict.IMPROVED, row.verdict());
    }

    /**
     * Bug: a new benchmark fails the pull request that adds it; a benchmark that crashed in the new code passes as removed
     */
    @Test
    void t99_metricsOnOneSideAreNewOrRemoved() {
        Metric onlyNew = new Metric("RequestGateBenchmark.admit", Kind.TIME, "ns/op", List.of(10.0));
        Metric onlyOld = new Metric("RequestGateBenchmark.oldAdmit", Kind.TIME, "ns/op", List.of(10.0));

        List<Row> rows = PerfCompare.compare(List.of(onlyOld), List.of(onlyNew), Options.NONE).rows();

        assertEquals(2, rows.size());
        assertEquals(Verdict.NEW, rows.get(0).verdict());
        assertEquals(onlyNew.key(), rows.get(0).key());
        assertEquals(Verdict.REMOVED, rows.get(1).verdict());
        assertEquals(onlyOld.key(), rows.get(1).key());
    }

    /**
     * Bug: time and allocation of one benchmark keyed by name alone, so one overwrites the other
     */
    @Test
    void t170_timeAndAllocationOfOneBenchmarkStayApart() {
        List<Row> rows = PerfCompare.compare(List.of(time(100.0), allocation(100.0)), List.of(time(100.0), allocation(1000.0)), Options.NONE).rows();

        assertEquals(List.of(Verdict.OK, Verdict.REGRESSION), rows.stream().map(Row::verdict).toList());
    }

    /**
     * Bug: rounds read as separate metrics are not merged, so only one round's sample counts
     */
    @Test
    void t171_roundsOfOneMetricAreMergedBeforeTheMedian() {
        List<Row> rows = PerfCompare.compare(List.of(time(100.0), time(100.0), time(100.0)), List.of(time(100.0), time(1000.0), time(100.0)), Options.NONE).rows();

        assertEquals(1, rows.size());
        assertEquals(Verdict.OK, rows.get(0).verdict());
    }

    /**
     * Bug: rounds of one version mixed across units (for example us instead of ns), compared as if the numbers matched
     */
    @Test
    void t144_twoUnitsInOneVersionAreRejected() {
        Metric ns = time(100.0);
        Metric us = new Metric(NAME, Kind.TIME, "us/op", List.of(0.1));

        assertThrows(IllegalArgumentException.class, () -> PerfCompare.compare(List.of(ns, us), List.of(ns), Options.NONE).rows());
    }

    /**
     * Bug: a benchmark that crashed in the new code passes as removed; or a removed benchmark fails although each version ran its own benchmarks
     */
    @Test
    void t182_removedFailsUnlessEachVersionRanItsOwn() {
        Metric onlyOld = new Metric("RequestGateBenchmark.oldAdmit", Kind.TIME, "ns/op", List.of(10.0));
        Metric kept = time(100.0);

        PerfCompare.Result sameSources = PerfCompare.compare(List.of(onlyOld, kept), List.of(kept), PerfCompare.Options.NONE);
        assertTrue(sameSources.failed());
        assertTrue(sameSources.rows().get(1).note().contains("broke"), sameSources.rows().get(1).note());

        PerfCompare.Result ownSources = PerfCompare.compare(List.of(onlyOld, kept), List.of(kept), new PerfCompare.Options(false, true, Map.of()));
        assertFalse(ownSources.failed());
        assertEquals(Verdict.REMOVED, ownSources.rows().get(1).verdict());
        assertTrue(ownSources.fallback());
    }

    /**
     * Bug: the comparison itself accepts uneven samples, so only the command line notices
     */
    @Test
    void t186_differentSampleCountsAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PerfCompare.compare(List.of(time(100.0, 100.0, 100.0)), List.of(time(100.0, 100.0)), Options.NONE).rows());

        assertTrue(e.getMessage().contains(time(1.0).key()), e.getMessage());
    }

    /**
     * Bug: the comparison itself compares numbers of different units
     */
    @Test
    void t187_unitMismatchBetweenVersionsIsRejected() {
        Metric us = new Metric(NAME, Kind.TIME, "us/op", List.of(0.1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PerfCompare.compare(List.of(time(100.0)), List.of(us), Options.NONE).rows());

        assertTrue(e.getMessage().contains("ns/op") && e.getMessage().contains("us/op"), e.getMessage());
    }

    /**
     * Bug: the accepted keys silence everything, or an old entry hides a later regression
     */
    @Test
    void t188_onlyListedRegressionsAreAccepted() {
        Metric slowBefore = new Metric("ProtocolBenchmark.encodeHello", Kind.TIME, "ns/op", List.of(100.0));
        Metric slowAfter = new Metric("ProtocolBenchmark.encodeHello", Kind.TIME, "ns/op", List.of(300.0));
        Map<String, String> accepted = Map.of(slowAfter.key(), "hello carries the stat modes now", time(1.0).key(), "no longer needed");

        PerfCompare.Result result = PerfCompare.compare(List.of(slowBefore, time(100.0), allocation(100.0)), List.of(slowAfter, time(100.0), allocation(1000.0)),
                new PerfCompare.Options(false, false, accepted));

        assertEquals(List.of(Verdict.ACCEPTED, Verdict.OK, Verdict.REGRESSION), result.rows().stream().map(Row::verdict).toList());
        assertEquals("hello carries the stat modes now", result.rows().get(0).note());
        assertTrue(result.failed());
        assertEquals(List.of(time(1.0).key()), result.acceptedButNotNeeded());
    }

    /**
     * Bug: pull request mode compares time too, and only the command line filters it
     */
    @Test
    void t190_allocationOnlySkipsTime() {
        PerfCompare.Result result = PerfCompare.compare(List.of(time(100.0), allocation(100.0)), List.of(time(1000.0), allocation(100.0)), new PerfCompare.Options(true, false, Map.of()));

        assertEquals(List.of(allocation(1.0).key()), result.rows().stream().map(Row::key).toList());
        assertFalse(result.failed());
    }

    /**
     * Bug: the recheck clears a regression that repeats, or one it did not measure
     */
    @Test
    void t189_onlyRepeatedRegressionsCount() {
        Metric other = new Metric("ProtocolBenchmark.encodeHello", Kind.ALLOCATION, "B/op", List.of(100.0));
        Metric otherWorse = new Metric("ProtocolBenchmark.encodeHello", Kind.ALLOCATION, "B/op", List.of(1000.0));
        PerfCompare.Result first = PerfCompare.compare(List.of(allocation(100.0), other, time(100.0)), List.of(allocation(1000.0), otherWorse, time(100.0)), PerfCompare.Options.NONE);
        assertEquals(List.of(Verdict.REGRESSION, Verdict.REGRESSION, Verdict.OK), first.rows().stream().map(Row::verdict).toList());

        // The recheck: the first benchmark is back to normal, the second still allocates more
        PerfCompare.Result recheck = PerfCompare.compare(List.of(allocation(100.0), other), List.of(allocation(100.0), otherWorse), PerfCompare.Options.NONE);
        PerfCompare.Result confirmed = PerfCompare.confirm(first, recheck);

        assertEquals(List.of(Verdict.OK, Verdict.REGRESSION, Verdict.OK), confirmed.rows().stream().map(Row::verdict).toList());
        assertTrue(confirmed.rows().get(0).note().contains("not repeated"));
        assertTrue(confirmed.failed());

        // A recheck that lacks the metric, or lost it, clears nothing
        PerfCompare.Result lacking = PerfCompare.confirm(first, PerfCompare.compare(List.of(other), List.of(otherWorse), PerfCompare.Options.NONE));
        assertEquals(List.of(Verdict.REGRESSION, Verdict.REGRESSION, Verdict.OK), lacking.rows().stream().map(Row::verdict).toList());
        PerfCompare.Result lost = PerfCompare.confirm(first, PerfCompare.compare(List.of(allocation(100.0)), List.of(), new PerfCompare.Options(false, true, Map.of())));
        assertEquals(Verdict.REGRESSION, lost.rows().get(0).verdict());
    }
}
