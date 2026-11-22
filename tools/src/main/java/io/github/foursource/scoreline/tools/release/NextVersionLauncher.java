package io.github.foursource.scoreline.tools.release;

/**
 * Starts {@link NextVersion} and exits the process with its result. It holds nothing else, so the coverage gate of {@code tools} can leave it out: {@code System.exit} cannot run in a unit test.
 * <p>
 * {@code scripts/next-version.sh} starts it with Java's source launcher ({@code java <this file> <bump> <tags...>}), which compiles this file and {@link NextVersion} from the source tree, so the
 * release workflow needs a JDK but no Gradle build.
 */
public final class NextVersionLauncher {
    private NextVersionLauncher() {}

    /**
     * @param args the bump, then the tags
     */
    public static void main(String[] args) {
        System.exit(NextVersion.run(args, System.out, System.err));
    }
}
