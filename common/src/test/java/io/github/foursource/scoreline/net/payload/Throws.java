package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.function.Executable;

/**
 * Test helper: checks that code throws an exception of a type <em>and</em> that its message contains a fragment.
 * <p>
 * Several checks throw the same type, so the type alone could let a test pass on the wrong check. A fragment (the player or ID the message names, or the number that broke a limit) pins the check
 * without tying the test to the exact wording.
 */
final class Throws {
    private Throws() {}

    /**
     * Asserts that {@code executable} throws {@code type} with a message that contains {@code fragment}.
     *
     * @param type the expected exception type
     * @param fragment text the message must contain
     * @param executable the code that should throw
     * @param <T> the exception type
     * @return the exception, for further checks
     */
    static <T extends Throwable> T withMessage(Class<T> type, String fragment, Executable executable) {
        T thrown = assertThrows(type, executable);
        assertTrue(thrown.getMessage() != null && thrown.getMessage().contains(fragment), () -> "expected a message containing \"" + fragment + "\" but was: " + thrown.getMessage());
        return thrown;
    }
}
