package io.github.foursource.scoreline.tools.release;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NextVersionTest {
    /**
     * Bug: the move into Java changes a release's version
     */
    @ParameterizedTest
    @CsvSource({ "v0.1.0, patch, 0.1.1", "v0.1.0, minor, 0.2.0", "v0.1.0, major, 1.0.0", "v0.9.0, minor, 0.10.0", "v0.10.0, minor, 0.11.0" })
    void t26_raisesTheHighestTag(String tag, String bump, String expected) {
        assertEquals(expected, NextVersion.next(List.of(tag), bump));
    }

    /**
     * Bug: the tags compared as text, so v0.9.0 ranks above v0.10.0, or the first tag is taken instead of the highest
     */
    @Test
    void t179_highestTagByNumber() {
        assertEquals("0.11.0", NextVersion.next(List.of("v0.9.0", "v0.10.0", "v0.2.0-rc1", "vnext", "v1.0"), "minor"));
        assertEquals("1.1.0", NextVersion.next(List.of("v0.10.3", "v1.0.0", "v0.99.0"), "minor"));
        assertEquals("1.0.1", NextVersion.next(List.of("v1.0.0", "v0.0.9"), "patch"));
    }

    /**
     * Bug: without a release tag a version is printed anyway
     */
    @Test
    void t24_noReleaseTagIsAnError() {
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class, () -> NextVersion.next(List.of(), "minor"));
        assertEquals(NextVersion.NO_TAG, none.getMessage());
    }

    /**
     * Bug: a malformed tag is parsed, and the arithmetic fails or yields garbage
     */
    @Test
    void t25_onlyMalformedTagsIsAnError() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class, () -> NextVersion.next(List.of("v0.2.0-rc1", "vnext"), "minor"));
        assertEquals(NextVersion.NO_TAG, malformed.getMessage());
    }

    /**
     * Bug: an unknown bump raises some part anyway
     */
    @Test
    void t192_unknownBumpIsUsage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> NextVersion.next(List.of("v0.1.0"), "big"));
        assertEquals(NextVersion.USAGE, e.getMessage());
    }

    /**
     * Bug: a \r from Windows' line end reaches the script's tag check; an error prints a version too, or exits 0
     */
    @Test
    void t180_commandLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8);

        assertEquals(0, NextVersion.run(new String[] { "minor", "v0.1.0" }, o, e));
        assertEquals("0.2.0\n", out.toString(StandardCharsets.UTF_8), "a \\r from Windows' line end would reach the script");
        assertEquals("", err.toString(StandardCharsets.UTF_8));

        assertEquals(1, NextVersion.run(new String[] { "minor" }, o, e));
        assertEquals(1, NextVersion.run(new String[0], o, e));
        assertEquals(NextVersion.NO_TAG + System.lineSeparator() + NextVersion.USAGE, err.toString(StandardCharsets.UTF_8).strip());
        assertEquals("0.2.0", out.toString(StandardCharsets.UTF_8).strip());
    }
}
