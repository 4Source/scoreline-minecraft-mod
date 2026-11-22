package io.github.foursource.scoreline.net.payload;

import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes the hello, whose hand-written decoder skips the bytes it does not know. */
public class HelloPayloadFuzzTest {
    /**
     * Bug: skipping the bytes a newer hello appends fails for some lengths
     */
    @FuzzTest(maxDuration = "120s")
    void t113_helloDecoder(byte[] data) {
        FuzzAssertions.assertDecodesSafely(HelloPayload.STREAM_CODEC, data);
    }
}
