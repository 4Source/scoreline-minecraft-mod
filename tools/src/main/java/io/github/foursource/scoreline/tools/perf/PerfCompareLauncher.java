package io.github.foursource.scoreline.tools.perf;

/**
 * Starts {@link PerfCompareMain} and exits the process with its result. It holds nothing else, so the coverage gate of {@code tools} can leave it out: {@code System.exit} cannot run in a unit test.
 */
public final class PerfCompareLauncher {
    private PerfCompareLauncher() {}

    /**
     * @param args see {@link PerfCompareMain}
     */
    public static void main(String[] args) {
        System.exit(PerfCompareMain.run(args, System.getenv(), System.out, System.err));
    }
}
