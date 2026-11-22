package io.github.foursource.scoreline.tools.perf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns one JMH result file ({@code -rf json}) of one round into metrics, one sample each.
 * <p>
 * Each benchmark gives a {@link Metric.Kind#TIME} metric from its primary score and, if the run used {@code -prof gc}, an {@link Metric.Kind#ALLOCATION} metric from {@code gc.alloc.rate.norm}. The
 * name is the benchmark's simple class name and method, followed by its parameters sorted by name, for example {@code ProtocolBenchmark.encodeStatsResponse[players=50]}, so two parameter values never
 * mix.
 */
public final class JmhResults {
    /** JMH's secondary metric for bytes allocated per operation. */
    static final String ALLOCATION_METRIC = "gc.alloc.rate.norm";

    private JmhResults() {}

    /**
     * Reads a JMH result file.
     *
     * @param file the JSON file JMH wrote
     * @return the metrics in the file's order, each with one sample
     * @throws PerfInputException if the file cannot be read, is not valid JSON or lacks a field JMH always writes
     */
    public static List<Metric> read(Path file) throws PerfInputException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Metric> metrics = new ArrayList<>();
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject benchmark = element.getAsJsonObject();
                String name = name(benchmark);

                JsonObject primary = benchmark.getAsJsonObject("primaryMetric");
                metrics.add(metric(name, Metric.Kind.TIME, primary));

                JsonObject secondary = benchmark.getAsJsonObject("secondaryMetrics");
                if (secondary != null && secondary.has(ALLOCATION_METRIC)) {
                    metrics.add(metric(name, Metric.Kind.ALLOCATION, secondary.getAsJsonObject(ALLOCATION_METRIC)));
                }
            }
            return metrics;
        }
        catch (IOException | RuntimeException e) {
            // Gson reports bad syntax and wrong structure as runtime exceptions (JsonParseException, IllegalStateException, ...)
            throw new PerfInputException(file + ": not a readable JMH result file: " + e.getMessage(), e);
        }
    }

    /** {@code io.github...ProtocolBenchmark.encodeHello} with params {@code {players: 50}} → {@code ProtocolBenchmark.encodeHello[players=50]}. */
    private static String name(JsonObject benchmark) {
        String fullName = benchmark.get("benchmark").getAsString();
        int methodDot = fullName.lastIndexOf('.');
        int classDot = fullName.lastIndexOf('.', methodDot - 1);
        String name = fullName.substring(classDot + 1);

        JsonObject params = benchmark.getAsJsonObject("params");
        if (params == null || params.isEmpty()) {
            return name;
        }
        // Sorted, so the name does not depend on the order JMH writes the parameters in
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> param : params.entrySet()) {
            sorted.put(param.getKey(), param.getValue().getAsString());
        }
        StringBuilder withParams = new StringBuilder(name).append('[');
        sorted.forEach((key, value) -> withParams.append(key).append('=').append(value).append(','));
        withParams.setCharAt(withParams.length() - 1, ']');
        return withParams.toString();
    }

    private static Metric metric(String name, Metric.Kind kind, JsonObject score) {
        double value = score.get("score").getAsDouble();
        // NaN fails every comparison, so it would pass as OK
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(name + " has the score " + value);
        }
        return new Metric(name, kind, score.get("scoreUnit").getAsString(), List.of(value));
    }
}
