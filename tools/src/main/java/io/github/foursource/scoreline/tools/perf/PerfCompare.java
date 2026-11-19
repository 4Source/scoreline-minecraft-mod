package io.github.foursource.scoreline.tools.perf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compares the metrics of a baseline with those of the new code, by the median of each metric's samples.
 * <p>
 * The limits:
 * <ul>
 * <li>Time: a new median more than {@value #TIME_LIMIT} × the baseline's is a {@link Verdict#REGRESSION}; one below the baseline's ÷ {@value #IMPROVEMENT} is {@link Verdict#IMPROVED}.</li>
 * <li>Allocation: a regression only if the new median is more than {@value #ALLOCATION_LIMIT} × the baseline's <b>and</b> more than {@value #ALLOCATION_MIN_BYTES} bytes more, so small values do not
 * fail on a ratio. From a baseline below {@value #ZERO_BYTES} byte ("zero"), {@value #FROM_ZERO_BYTES} bytes or more is a regression: a path that allocated nothing and now makes a small object per
 * call is exactly what the check is for. Allocation is never marked improved.</li>
 * </ul>
 */
public final class PerfCompare {
    /** A time median above baseline × this is a regression. */
    public static final double TIME_LIMIT = 1.5;
    /** A time median below baseline ÷ this is an improvement (reported only). */
    public static final double IMPROVEMENT = 1.5;
    /** An allocation median above baseline × this (and above {@link #ALLOCATION_MIN_BYTES} more) is a regression. */
    public static final double ALLOCATION_LIMIT = 1.10;
    /** An allocation increase of at most this many bytes is never a regression, unless the baseline allocated nothing. */
    public static final double ALLOCATION_MIN_BYTES = 32;
    /** A baseline allocation below this many bytes per call counts as "allocates nothing". */
    public static final double ZERO_BYTES = 1;
    /** From a baseline that allocates nothing, this many bytes per call or more is a regression. */
    public static final double FROM_ZERO_BYTES = 8;

    /** The outcome for one metric. */
    public enum Verdict {
        /** Within the limits. */
        OK,
        /** Clearly worse; fails the comparison. */
        REGRESSION,
        /** A regression listed with a reason in the accepted file; reported, does not fail. */
        ACCEPTED,
        /** Clearly faster; reported only. */
        IMPROVED,
        /** Only in the new code. */
        NEW,
        /** Only in the baseline: fails, because both versions run the same benchmarks, unless each ran its own. */
        REMOVED
    }

    /**
     * What a comparison takes into account besides the metrics.
     *
     * @param allocationOnly compare only {@link Metric.Kind#ALLOCATION} metrics (pull requests)
     * @param fallback each version ran its own benchmark sources, so a metric missing in the new code is reported, not failed
     * @param accepted accepted regressions: metric key → reason
     */
    public record Options(boolean allocationOnly, boolean fallback, Map<String, String> accepted) {
        /** Neither filter nor fallback, nothing accepted. */
        public static final Options NONE = new Options(false, false, Map.of());

        /**
         * @throws NullPointerException if {@code accepted} is {@code null}
         */
        public Options {
            accepted = Map.copyOf(accepted);
        }
    }

    /**
     * One line of the comparison.
     *
     * @param key the metric's {@link Metric#key() key}
     * @param name the metric's name, without the kind
     * @param unit the unit of the medians
     * @param baselineMedian the baseline's median, {@code NaN} for {@link Verdict#NEW}
     * @param newMedian the new code's median, {@code NaN} for {@link Verdict#REMOVED}
     * @param verdict the outcome
     * @param note why the verdict is what it is, or empty
     */
    public record Row(String key, String name, String unit, double baselineMedian, double newMedian, Verdict verdict, String note) {
        /** @return new ÷ baseline, or {@code NaN} if either is missing or the baseline is 0 */
        public double ratio() {
            return baselineMedian == 0 ? Double.NaN : newMedian / baselineMedian;
        }

        /**
         * @param fallback whether each version ran its own benchmarks
         * @return whether this row fails the comparison
         */
        public boolean fails(boolean fallback) {
            return verdict == Verdict.REGRESSION || (verdict == Verdict.REMOVED && !fallback);
        }

        Row with(Verdict newVerdict, String newNote) {
            return new Row(key, name, unit, baselineMedian, newMedian, newVerdict, newNote);
        }
    }

    /**
     * The outcome of a comparison.
     *
     * @param rows one row per key: the new code's metrics in their order, then those only in the baseline
     * @param acceptedButNotNeeded keys listed as accepted that did not regress
     * @param fallback whether each version ran its own benchmarks
     */
    public record Result(List<Row> rows, List<String> acceptedButNotNeeded, boolean fallback) {
        /** Copies the lists. */
        public Result {
            rows = List.copyOf(rows);
            acceptedButNotNeeded = List.copyOf(acceptedButNotNeeded);
        }

        /** @return whether any row fails the comparison */
        public boolean failed() {
            return rows.stream().anyMatch(row -> row.fails(fallback));
        }
    }

    private PerfCompare() {}

    /**
     * Compares two versions metric by metric. Metrics with the same key are merged first, so the samples of several rounds may come as separate metrics.
     *
     * @param baseline the baseline's metrics
     * @param current the new code's metrics
     * @param options what else to take into account
     * @return the comparison
     * @throws IllegalArgumentException if one version has two units for a key, or the versions differ in a key's unit or number of samples (a benchmark that failed in some rounds)
     */
    public static Result compare(Collection<Metric> baseline, Collection<Metric> current, Options options) {
        Objects.requireNonNull(options, "options");
        Map<String, Metric> before = merge(filter(baseline, options));
        Map<String, Metric> after = merge(filter(current, options));

        List<Row> rows = new ArrayList<>();
        for (Metric metric : after.values()) {
            Metric old = before.get(metric.key());
            double newMedian = median(metric.samples());
            if (old == null) {
                rows.add(new Row(metric.key(), metric.name(), metric.unit(), Double.NaN, newMedian, Verdict.NEW, ""));
                continue;
            }
            if (!old.unit().equals(metric.unit())) {
                throw new IllegalArgumentException(metric.key() + " is in " + old.unit() + " in the baseline but in " + metric.unit() + " in the new code");
            }
            if (old.samples().size() != metric.samples().size()) {
                throw new IllegalArgumentException(
                        metric.key() + " has " + old.samples().size() + " samples in the baseline but " + metric.samples().size() + " in the new code; a benchmark failed in some rounds");
            }
            double baselineMedian = median(old.samples());
            Row row = new Row(metric.key(), metric.name(), metric.unit(), baselineMedian, newMedian, verdict(metric.kind(), baselineMedian, newMedian), "");
            String reason = options.accepted().get(metric.key());
            rows.add(reason != null && row.verdict() == Verdict.REGRESSION ? row.with(Verdict.ACCEPTED, reason) : row);
        }
        for (Metric metric : before.values()) {
            if (!after.containsKey(metric.key())) {
                String note = options.fallback() ? "each version ran its own benchmarks" : "missing in the new code: the benchmark broke";
                rows.add(new Row(metric.key(), metric.name(), metric.unit(), median(metric.samples()), Double.NaN, Verdict.REMOVED, note));
            }
        }

        List<String> notNeeded = new ArrayList<>();
        for (String key : options.accepted().keySet().stream().sorted().toList()) {
            if (rows.stream().noneMatch(row -> row.key().equals(key) && row.verdict() == Verdict.ACCEPTED)) {
                notNeeded.add(key);
            }
        }
        return new Result(rows, notNeeded, options.fallback());
    }

    /**
     * Keeps a regression only if it repeats: a {@link Verdict#REGRESSION} of the first comparison whose metric is not a regression in the recheck becomes {@link Verdict#OK}, with a note.
     *
     * @param first the comparison of all rounds
     * @param recheck the comparison of the extra measurement of the regressed benchmarks
     * @return the first comparison with unrepeated regressions cleared
     */
    public static Result confirm(Result first, Result recheck) {
        List<Row> rows = new ArrayList<>();
        for (Row row : first.rows()) {
            // Cleared only by a recheck that measured the metric and found it within the limits; a metric the recheck lacks stays a regression
            boolean cleared = recheck.rows().stream().anyMatch(other -> other.key().equals(row.key()) && other.verdict() != Verdict.REGRESSION && other.verdict() != Verdict.REMOVED);
            rows.add(row.verdict() == Verdict.REGRESSION && cleared ? row.with(Verdict.OK, "regression not repeated when measured once more") : row);
        }
        return new Result(rows, first.acceptedButNotNeeded(), first.fallback());
    }

    /**
     * Merges metrics with the same key into one, concatenating their samples in order.
     *
     * @param metrics the metrics, for example those of all rounds
     * @return the merged metrics by key, in the order each key first appears
     * @throws IllegalArgumentException if two metrics with the same key have different units
     */
    public static Map<String, Metric> merge(Collection<Metric> metrics) {
        Map<String, Metric> merged = new LinkedHashMap<>();
        for (Metric metric : metrics) {
            merged.merge(metric.key(), metric, (a, b) -> {
                if (!a.unit().equals(b.unit())) {
                    throw new IllegalArgumentException(a.key() + " has two units: " + a.unit() + " and " + b.unit());
                }
                List<Double> samples = new ArrayList<>(a.samples());
                samples.addAll(b.samples());
                return new Metric(a.name(), a.kind(), a.unit(), samples);
            });
        }
        return merged;
    }

    private static List<Metric> filter(Collection<Metric> metrics, Options options) {
        return metrics.stream().filter(metric -> !options.allocationOnly() || metric.kind() == Metric.Kind.ALLOCATION).toList();
    }

    /**
     * @param samples at least one value
     * @return the middle value, or the mean of the two middle values for an even count
     */
    static double median(List<Double> samples) {
        double[] sorted = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    static Verdict verdict(Metric.Kind kind, double baselineMedian, double newMedian) {
        return switch (kind) {
        case TIME -> {
            if (newMedian > baselineMedian * TIME_LIMIT) {
                yield Verdict.REGRESSION;
            }
            yield newMedian < baselineMedian / IMPROVEMENT ? Verdict.IMPROVED : Verdict.OK;
        }
        case ALLOCATION -> {
            if (baselineMedian < ZERO_BYTES) {
                yield newMedian >= FROM_ZERO_BYTES ? Verdict.REGRESSION : Verdict.OK;
            }
            // Both conditions as products and sums, never a ratio
            yield newMedian > baselineMedian * ALLOCATION_LIMIT && newMedian > baselineMedian + ALLOCATION_MIN_BYTES ? Verdict.REGRESSION : Verdict.OK;
        }
        };
    }
}
