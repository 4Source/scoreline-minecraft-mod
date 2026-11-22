package io.github.foursource.scoreline.tools.perf;

import java.util.List;
import java.util.Objects;

/**
 * One measured quantity of one version, with one sample per round.
 * <p>
 * The name identifies the measurement across versions and includes any parameters, for example {@code ProtocolBenchmark.encodeStatsResponse[players=50]}. A metric is identified by its name and kind
 * together, because a benchmark yields both a time and an allocation metric under the same name.
 *
 * @param name the measurement's name, parameters included
 * @param kind whether the samples are times or allocated bytes
 * @param unit the unit of the samples as the source reports it, for example {@code ns/op}
 * @param samples one value per round, at least one
 */
public record Metric(String name, Kind kind, String unit, List<Double> samples) {

    /** What a metric measures; decides which limit applies. */
    public enum Kind {
        /** Time per operation; higher is slower. */
        TIME("time"),
        /** Bytes allocated per operation. */
        ALLOCATION("allocation");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** @return the lower-case name shown in the report */
        public String label() {
            return label;
        }
    }

    /**
     * @throws NullPointerException if any argument or sample is {@code null}
     * @throws IllegalArgumentException if there are no samples
     */
    public Metric {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(unit, "unit");
        samples = List.copyOf(samples);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("metric " + name + " has no samples");
        }
    }

    /** @return the key that matches this metric with the other version's: name and kind */
    public String key() {
        return name + " (" + kind.label() + ")";
    }
}
