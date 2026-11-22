package io.github.foursource.scoreline.net.payload;

import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes what a client sends the server: the main target, because anyone can connect a modified client to a public server. */
public class StatsRequestPayloadFuzzTest {
    /**
     * Bug: an unexpected exception, a hang or unbounded allocation for some input
     */
    @FuzzTest(maxDuration = "120s")
    void t111_requestDecoder(byte[] data) {
        FuzzAssertions.assertDecodesSafely(StatsRequestPayload.STREAM_CODEC, data);
    }
}
