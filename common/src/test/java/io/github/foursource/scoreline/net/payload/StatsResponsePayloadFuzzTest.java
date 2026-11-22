package io.github.foursource.scoreline.net.payload;

import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes what a server sends the client as a response: a broken or hostile server must not crash the client. */
public class StatsResponsePayloadFuzzTest {
    /**
     * Bug: an unexpected exception, a hang or unbounded allocation in the client, for some input from a broken or hostile server
     */
    @FuzzTest(maxDuration = "120s")
    void t112_responseDecoder(byte[] data) {
        FuzzCheck.decode(StatsResponsePayload.STREAM_CODEC, data);
    }
}
