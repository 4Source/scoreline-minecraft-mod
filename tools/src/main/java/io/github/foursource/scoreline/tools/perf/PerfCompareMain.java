package io.github.foursource.scoreline.tools.perf;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compares the results of two versions and prints the comparison as a Markdown table.
 * <p>
 * Usage: {@code [--allocation-only] [--fallback] [--accepted <file>] [--regressions <file>] [--recheck <baseline folder> <new folder>] <baseline folder> <new folder>}. Each folder holds one subfolder
 * {@code round-<n>} per round, each with a JMH result file {@code jmh.json}; other files are ignored.
 * <ul>
 * <li>{@code --allocation-only}: compare allocated bytes only (pull requests).</li>
 * <li>{@code --fallback}: each version ran its own benchmark sources, so a metric missing in the new code is reported, not failed.</li>
 * <li>{@code --accepted <file>}: regressions accepted for a release, one per line: metric key, a tab, the reason. Empty lines and lines starting with {@code #} are skipped.</li>
 * <li>{@code --regressions <file>}: writes the benchmarks ({@code Class.method}) with a regression, one per line, so the script can measure them once more.</li>
 * <li>{@code --recheck <b> <n>}: the extra measurement of those benchmarks; a regression counts only if it repeats there.</li>
 * </ul>
 * An empty baseline (no round with results) is unreadable input, unless the environment sets {@value #ALLOW_NO_BASELINE}{@code =1}: then it prints "no baseline: nothing compared".
 * <p>
 * Exit codes: 0 when nothing failed, 1 when a metric is a {@link PerfCompare.Verdict#REGRESSION} or (without {@code --fallback}) {@link PerfCompare.Verdict#REMOVED}, 2 on unreadable input.
 */
public final class PerfCompareMain {
    /** Nothing failed. */
    static final int OK = 0;
    /** At least one regression. */
    static final int REGRESSION = 1;
    /** Unreadable input. */
    static final int BAD_INPUT = 2;

    static final String JMH_FILE = "jmh.json";
    static final String ROUND_PREFIX = "round-";
    /** The environment variable that allows an empty baseline (for a baseline from before the benchmarks existed). */
    static final String ALLOW_NO_BASELINE = "ALLOW_NO_BASELINE";
    static final String USAGE = "usage: PerfCompareMain [--allocation-only] [--fallback] [--accepted <file>] [--regressions <file>] [--recheck <baseline> <new>] <baseline folder> <new folder>";

    private PerfCompareMain() {}

    /** The parsed command line. */
    private record Arguments(boolean allocationOnly, boolean fallback, Path accepted, Path regressions, Path recheckBaseline, Path recheckNew, Path baseline, Path current) {}

    /**
     * Runs the comparison.
     *
     * @param args the options and the two folders
     * @param env the environment, for {@value #ALLOW_NO_BASELINE}
     * @param out where the table goes
     * @param err where errors go
     * @return the exit code
     */
    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = parse(args);
            PerfCompare.Options options = new PerfCompare.Options(arguments.allocationOnly(), arguments.fallback(), arguments.accepted() == null ? Map.of() : readAccepted(arguments.accepted()));

            List<Metric> baseline = readRounds(arguments.baseline());
            List<Metric> current = readRounds(arguments.current());
            if (current.isEmpty()) {
                err.println(arguments.current() + ": no results of the new code");
                return BAD_INPUT;
            }
            if (baseline.isEmpty()) {
                if ("1".equals(env.get(ALLOW_NO_BASELINE))) {
                    out.println("no baseline: nothing compared");
                    return OK;
                }
                err.println(arguments.baseline() + ": no results of the baseline; set " + ALLOW_NO_BASELINE + "=1 only if the baseline has no benchmarks at all");
                return BAD_INPUT;
            }

            PerfCompare.Result result = PerfCompare.compare(baseline, current, options);
            if (arguments.recheckBaseline() != null) {
                List<Metric> recheckBaseline = readRounds(arguments.recheckBaseline());
                List<Metric> recheckNew = readRounds(arguments.recheckNew());
                // An extra measurement that measured nothing must not clear anything
                if (recheckBaseline.isEmpty() || recheckNew.isEmpty()) {
                    err.println(arguments.recheckBaseline() + ", " + arguments.recheckNew() + ": the extra measurement has no results");
                    return BAD_INPUT;
                }
                result = PerfCompare.confirm(result, PerfCompare.compare(recheckBaseline, recheckNew, new PerfCompare.Options(options.allocationOnly(), true, Map.of())));
            }
            if (arguments.regressions() != null) {
                writeRegressions(result, arguments.regressions());
            }
            printTable(result, out);
            return result.failed() ? REGRESSION : OK;
        }
        catch (PerfInputException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return BAD_INPUT;
        }
        catch (IOException e) {
            err.println("cannot read or write " + e.getMessage() + ": " + e.getClass().getSimpleName());
            return BAD_INPUT;
        }
    }

    private static Arguments parse(String[] args) {
        boolean allocationOnly = false;
        boolean fallback = false;
        Path accepted = null;
        Path regressions = null;
        Path recheckBaseline = null;
        Path recheckNew = null;
        List<Path> folders = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "--allocation-only" -> allocationOnly = true;
            case "--fallback" -> fallback = true;
            case "--accepted" -> accepted = Path.of(value(args, ++i));
            case "--regressions" -> regressions = Path.of(value(args, ++i));
            case "--recheck" -> {
                recheckBaseline = Path.of(value(args, ++i));
                recheckNew = Path.of(value(args, ++i));
            }
            default -> {
                if (args[i].startsWith("--")) {
                    throw new IllegalArgumentException("unknown option " + args[i] + "\n" + USAGE);
                }
                folders.add(Path.of(args[i]));
            }
            }
        }
        if (folders.size() != 2) {
            throw new IllegalArgumentException(USAGE);
        }
        return new Arguments(allocationOnly, fallback, accepted, regressions, recheckBaseline, recheckNew, folders.get(0), folders.get(1));
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException(args[index - 1] + " needs a value\n" + USAGE);
        }
        return args[index];
    }

    /**
     * Reads the accepted regressions.
     *
     * @param file one line per regression: key, tab, reason
     * @return key → reason, in file order
     * @throws PerfInputException if a line has no reason
     * @throws IOException if the file cannot be read
     */
    static Map<String, String> readAccepted(Path file) throws PerfInputException, IOException {
        Map<String, String> accepted = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 1 || line.substring(tab + 1).isBlank()) {
                throw new PerfInputException(file + ": every accepted regression needs a key, a tab and a reason: " + line, null);
            }
            // Stripped, so spaces around the key do not make it miss its metric silently
            accepted.put(line.substring(0, tab).strip(), line.substring(tab + 1).strip());
        }
        return accepted;
    }

    /** Writes {@code Class.method} of each regressed metric, once each, so the script can run exactly those benchmarks again. */
    private static void writeRegressions(PerfCompare.Result result, Path file) throws IOException {
        Set<String> benchmarks = new LinkedHashSet<>();
        for (PerfCompare.Row row : result.rows()) {
            if (row.verdict() == PerfCompare.Verdict.REGRESSION) {
                // The part before the parameters, or the whole name if it has none
                benchmarks.add(row.name().split("\\[", 2)[0]);
            }
        }
        // Lines end with \n on every system: bash reads them, and would keep a \r as part of the pattern
        StringBuilder lines = new StringBuilder();
        benchmarks.forEach(benchmark -> lines.append(benchmark).append('\n'));
        Files.writeString(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Reads every round folder of one version.
     *
     * @param folder the version's folder
     * @return all metrics of all rounds, not merged; empty if the folder has no round with results
     * @throws PerfInputException if the folder does not exist or a result file cannot be read
     * @throws IOException if the folder cannot be listed
     */
    static List<Metric> readRounds(Path folder) throws PerfInputException, IOException {
        if (!Files.isDirectory(folder)) {
            throw new PerfInputException(folder + ": not a folder", null);
        }
        List<Metric> metrics = new ArrayList<>();
        List<Path> rounds;
        // Sorted by name, so the samples come in round order
        try (Stream<Path> entries = Files.list(folder)) {
            rounds = entries.sorted().toList();
        }
        for (Path round : rounds) {
            Path results = round.resolve(JMH_FILE);
            if (round.getFileName().toString().startsWith(ROUND_PREFIX) && Files.isRegularFile(results)) {
                metrics.addAll(JmhResults.read(results));
            }
        }
        return metrics;
    }

    private static void printTable(PerfCompare.Result result, PrintStream out) {
        if (result.fallback()) {
            out.println("The new benchmarks do not compile against the baseline, so each version ran its own benchmark sources; a changed benchmark input shows as a change.");
            out.println();
        }
        out.println("| Metric | Unit | Baseline | New | Ratio | Verdict |");
        out.println("| --- | --- | ---: | ---: | ---: | --- |");
        int failures = 0;
        int improved = 0;
        for (PerfCompare.Row row : result.rows()) {
            String verdict = row.verdict().name();
            if (row.fails(result.fallback())) {
                failures++;
                verdict = "**" + verdict + "**";
            }
            else if (row.verdict() == PerfCompare.Verdict.IMPROVED) {
                improved++;
            }
            String note = row.note().isEmpty() ? "" : " (" + row.note() + ")";
            out.println("| " + row.key() + " | " + row.unit() + " | " + number(row.baselineMedian()) + " | " + number(row.newMedian()) + " | " + number(row.ratio()) + " | " + verdict + note + " |");
        }
        out.println();
        out.println(result.rows().size() + " metrics, " + failures + " failing, " + improved + " improved");
        for (String key : result.acceptedButNotNeeded()) {
            out.println("accepted but not needed: " + key);
        }
    }

    private static String number(double value) {
        // ASCII only: the table goes through consoles of any encoding
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }
}
