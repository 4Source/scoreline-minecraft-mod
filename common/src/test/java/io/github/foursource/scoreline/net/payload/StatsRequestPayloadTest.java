package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;

public class StatsRequestPayloadTest {
    /** The request's wire format without its limits, see {@link Wire#UNLIMITED_IDS}. */
    private static final StreamCodec<ByteBuf, List<String>> UNLIMITED = Wire.UNLIMITED_IDS;

    /**
     * Bug: IDs lost or reordered; a type ID that matches neither the handler nor the golden file's name
     */
    @Test
    void t29_requestMatchesGoldenV1() throws IOException {
        StatsRequestPayload request = new StatsRequestPayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS));
        byte[] golden = Wire.golden(1, "stats_request");

        assertArrayEquals(golden, Wire.encode(StatsRequestPayload.STREAM_CODEC, request));
        assertEquals(request, Wire.decode(StatsRequestPayload.STREAM_CODEC, golden));
        assertEquals("scoreline:stats_request", request.type().id().toString());
    }

    /**
     * Bug: unbounded list lets one packet make the server allocate without limit; off-by-one in the limit
     */
    @Test
    void t30_requestLimitsIdCount() {
        List<String> atLimit = Wire.distinctIds(Protocol.MAX_STAT_IDS);
        List<String> overLimit = Wire.distinctIds(Protocol.MAX_STAT_IDS + 1);

        assertEquals(new StatsRequestPayload(atLimit), Wire.decode(StatsRequestPayload.STREAM_CODEC, Wire.encode(UNLIMITED, atLimit)));

        byte[] tooMany = Wire.encode(UNLIMITED, overLimit);
        // Minecraft's message names the count that broke the limit
        Throws.withMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_IDS + 1), () -> Wire.decode(StatsRequestPayload.STREAM_CODEC, tooMany));
    }

    /**
     * Bug: unbounded string length; off-by-one
     */
    @Test
    void t31_requestLimitsIdLength() {
        List<String> atLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH));
        List<String> overLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH + 1));

        assertEquals(new StatsRequestPayload(atLimit), Wire.decode(StatsRequestPayload.STREAM_CODEC, Wire.encode(UNLIMITED, atLimit)));

        byte[] tooLong = Wire.encode(UNLIMITED, overLimit);
        // Minecraft's message names the length that broke the limit
        Throws.withMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_ID_LENGTH + 1), () -> Wire.decode(StatsRequestPayload.STREAM_CODEC, tooLong));
    }

    /**
     * Bug: List.copyOf dropped, so the request changes when the caller's list changes
     */
    @Test
    void t57_requestCopiesStatIds() {
        List<String> statIds = new ArrayList<>(List.of(Protocol.PLAY_TIME, Protocol.DEATHS));
        StatsRequestPayload request = new StatsRequestPayload(statIds);

        statIds.set(0, "questmod:quests_completed");
        statIds.add(Protocol.PLAY_TIME);

        assertEquals(List.of(Protocol.PLAY_TIME, Protocol.DEATHS), request.statIds());
    }

    /**
     * Bug: a client makes the server read and send the same stat twice
     */
    @Test
    void t63_requestRejectsDuplicateIds() {
        List<String> duplicates = List.of(Protocol.DEATHS, Protocol.DEATHS);

        Throws.withMessage(IllegalArgumentException.class, Protocol.DEATHS, () -> new StatsRequestPayload(duplicates));

        // The decoder builds the payload through the constructor, so duplicates on the wire fail too
        byte[] onTheWire = Wire.encode(UNLIMITED, duplicates);
        Throws.withMessage(IllegalArgumentException.class, Protocol.DEATHS, () -> Wire.decode(StatsRequestPayload.STREAM_CODEC, onTheWire));
    }
}
