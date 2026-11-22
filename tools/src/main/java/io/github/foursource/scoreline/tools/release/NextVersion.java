package io.github.foursource.scoreline.tools.release;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes the next release version from the existing tags, for {@code scripts/next-version.sh}.
 * <p>
 * Only tags of the form {@code vX.Y.Z} count; the highest, compared as numbers ({@code v0.10.0} ranks above {@code v0.9.0}), is raised by the chosen part. Without such a tag there is no version to
 * raise, which is an error: the release workflow must check out all tags. Whether the result is already in use is the script's check, because it needs git.
 */
public final class NextVersion {
    /** Printed when no {@code vX.Y.Z} tag exists. */
    static final String NO_TAG = "No release tag found; check out with all tags (fetch-depth: 0)";
    /** Printed for a wrong command line. */
    static final String USAGE = "Usage: scripts/next-version.sh <patch|minor|major>";

    private static final Pattern RELEASE_TAG = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");

    /** A released version. */
    private record Version(int major, int minor, int patch) {
        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    private NextVersion() {}

    /**
     * Computes the next version.
     *
     * @param tags the repository's tags; any that are not {@code vX.Y.Z} are ignored
     * @param bump {@code patch}, {@code minor} or {@code major}
     * @return the next version, without a leading {@code v}
     * @throws IllegalArgumentException with {@link #USAGE} for another bump, or {@link #NO_TAG} if no tag is a release tag
     */
    public static String next(Collection<String> tags, String bump) {
        if (!List.of("patch", "minor", "major").contains(bump)) {
            throw new IllegalArgumentException(USAGE);
        }
        Version latest = latest(tags).orElseThrow(() -> new IllegalArgumentException(NO_TAG));
        Version next = switch (bump) {
        case "major" -> new Version(latest.major() + 1, 0, 0);
        case "minor" -> new Version(latest.major(), latest.minor() + 1, 0);
        default -> new Version(latest.major(), latest.minor(), latest.patch() + 1);
        };
        return next.toString();
    }

    private static Optional<Version> latest(Collection<String> tags) {
        return tags.stream().map(RELEASE_TAG::matcher).filter(Matcher::matches).map(m -> new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))))
                .max(Comparator.comparingInt(Version::major).thenComparingInt(Version::minor).thenComparingInt(Version::patch));
    }

    /**
     * Runs the computation for the command line.
     *
     * @param args the bump, then the tags
     * @param out where the version goes
     * @param err where errors go
     * @return 0, or 1 on an error
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println(USAGE);
            return 1;
        }
        try {
            // \n on every system: the script takes the version with $(...), which would keep a \r from Windows' line end
            out.print(next(List.of(Arrays.copyOfRange(args, 1, args.length)), args[0]) + "\n");
            return 0;
        }
        catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
    }
}
