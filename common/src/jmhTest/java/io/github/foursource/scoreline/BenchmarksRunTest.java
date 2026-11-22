package io.github.foursource.scoreline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.format.OutputFormatFactory;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

/**
 * Runs every benchmark once, in this JVM, so the build notices a broken benchmark. The timings are thrown away: without a fork and warm-up they mean nothing.
 */
public class BenchmarksRunTest {
    /** All of Scoreline's benchmarks. */
    private static final String INCLUDE = "io\\.github\\.foursource\\.scoreline\\..*Benchmark\\..*";

    // T-129: JMH only logs a benchmark's error unless asked to fail, so a broken benchmark would pass the build
    // T-130: a wrong include pattern runs nothing, or a benchmark is skipped for one of its parameter values, and the test passes anyway
    @Test
    public void t130_runsEveryBenchmarkOnce() throws RunnerException {
        Options options = new OptionsBuilder().include(INCLUDE).forks(0).warmupIterations(0).measurementIterations(1).measurementTime(TimeValue.milliseconds(10)).shouldFailOnError(true).build();

        Collection<RunResult> results = new Runner(options).run();

        Set<String> ran = new TreeSet<>();
        for (RunResult result : results) {
            Map<String, String> params = new TreeMap<>();
            for (String key : result.getParams().getParamsKeys()) {
                params.put(key, result.getParams().getParam(key));
            }
            ran.add(label(result.getParams().getBenchmark(), params));
        }
        Set<String> listed = listedBenchmarks();
        assertFalse(listed.isEmpty(), "JMH's benchmark list is empty");
        // JMH's own list (META-INF/BenchmarkList), expanded by parameter values: every entry must have a result, so a new benchmark needs no second edit here
        assertEquals(listed, ran);
    }

    /** Every benchmark in JMH's generated list, once per combination of its parameter values. */
    private static Set<String> listedBenchmarks() {
        Set<String> labels = new TreeSet<>();
        for (BenchmarkListEntry entry : BenchmarkList.defaultList().find(OutputFormatFactory.createFormatInstance(System.out, VerboseMode.SILENT), List.of(INCLUDE), List.of())) {
            List<Map<String, String>> combinations = new ArrayList<>(List.of(new TreeMap<>()));
            if (entry.getParams().hasValue()) {
                for (Map.Entry<String, String[]> param : entry.getParams().get().entrySet()) {
                    List<Map<String, String>> next = new ArrayList<>();
                    for (Map<String, String> combination : combinations) {
                        for (String value : param.getValue()) {
                            Map<String, String> extended = new TreeMap<>(combination);
                            extended.put(param.getKey(), value);
                            next.add(extended);
                        }
                    }
                    combinations = next;
                }
            }
            for (Map<String, String> combination : combinations) {
                labels.add(label(entry.getUsername(), combination));
            }
        }
        return labels;
    }

    private static String label(String benchmark, Map<String, String> params) {
        return params.isEmpty() ? benchmark : benchmark + params;
    }
}
