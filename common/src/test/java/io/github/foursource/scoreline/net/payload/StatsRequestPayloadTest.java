package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public class StatsRequestPayloadTest {
    /** The request's wire format without its limits, see {@link PayloadBytes#UNLIMITED_IDS}. */
    private static final StreamCodec<ByteBuf, List<String>> UNLIMITED = PayloadBytes.UNLIMITED_IDS;

    /** Minecraft accepts at most this many bytes per payload sent to the server (the type's identifier included). */
    private static final int MAX_SERVERBOUND_BYTES = 32_767;

    /**
     * Bug: IDs lost or reordered; a type ID that matches neither the handler nor the golden file's name
     */
    @Test
    void t29_requestMatchesGoldenV1() throws IOException {
        StatsRequestPayload request = new StatsRequestPayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS));
        byte[] golden = PayloadBytes.readGolden(1, "stats_request");

        assertArrayEquals(golden, PayloadBytes.encode(StatsRequestPayload.STREAM_CODEC, request));
        assertEquals(request, PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, golden));
        assertEquals("scoreline:stats_request", request.type().id().toString());
    }

    /**
     * Bug: unbounded list lets one packet make the server allocate without limit; off-by-one in the limit
     */
    @Test
    void t30_requestLimitsIdCount() {
        List<String> atLimit = PayloadBytes.distinctIds(Protocol.MAX_STAT_IDS);
        List<String> overLimit = PayloadBytes.distinctIds(Protocol.MAX_STAT_IDS + 1);

        assertEquals(new StatsRequestPayload(atLimit), PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, PayloadBytes.encode(UNLIMITED, atLimit)));

        byte[] tooMany = PayloadBytes.encode(UNLIMITED, overLimit);
        // Minecraft's message names the count that broke the limit
        ExceptionAssertions.assertThrowsWithMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_IDS + 1), () -> PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, tooMany));
    }

    /**
     * Bug: unbounded string length; off-by-one
     */
    @Test
    void t31_requestLimitsIdLength() {
        List<String> atLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH));
        List<String> overLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH + 1));

        assertEquals(new StatsRequestPayload(atLimit), PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, PayloadBytes.encode(UNLIMITED, atLimit)));

        byte[] tooLong = PayloadBytes.encode(UNLIMITED, overLimit);
        // Minecraft's message names the length that broke the limit
        ExceptionAssertions.assertThrowsWithMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_ID_LENGTH + 1), () -> PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, tooLong));
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

        ExceptionAssertions.assertThrowsWithMessage(IllegalArgumentException.class, Protocol.DEATHS, () -> new StatsRequestPayload(duplicates));

        // The decoder builds the payload through the constructor, so duplicates on the wire fail too
        byte[] onTheWire = PayloadBytes.encode(UNLIMITED, duplicates);
        ExceptionAssertions.assertThrowsWithMessage(IllegalArgumentException.class, Protocol.DEATHS, () -> PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, onTheWire));
    }

    /**
     * Bug: a later raise of a limit makes legal requests larger than Minecraft accepts, so the server drops clients that follow the protocol
     */
    @Test
    void t150_largestLegalRequestFitsTheServerLimit() {
        // The most IDs, each as long as allowed, every character 3 bytes in UTF-8 ('€'), the last one making them distinct
        List<String> largest = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_STAT_IDS; i++) {
            largest.add("€".repeat(Protocol.MAX_STAT_ID_LENGTH - 1) + (char) ('一' + i));
        }
        StatsRequestPayload request = new StatsRequestPayload(largest);

        int bytes = PayloadBytes.encode(Identifier.STREAM_CODEC, request.type().id()).length + PayloadBytes.encode(StatsRequestPayload.STREAM_CODEC, request).length;

        assertTrue(bytes < MAX_SERVERBOUND_BYTES, bytes + " bytes");
        // The decoder accepts it: it is legal
        assertEquals(request, PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, PayloadBytes.encode(StatsRequestPayload.STREAM_CODEC, request)));
    }

    /**
     * Bug: the decoder allocates a list of the announced size before checking the limit, so one packet takes the server's memory
     */
    @Test
    void t110_hugeAnnouncedCountFailsFast() {
        // VarInt Integer.MAX_VALUE: FF FF FF FF 07, then nothing
        byte[] bytes = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07 };

        assertTimeoutPreemptively(Duration.ofMillis(100),
                () -> ExceptionAssertions.assertThrowsWithMessage(DecoderException.class, String.valueOf(Integer.MAX_VALUE), () -> PayloadBytes.decode(StatsRequestPayload.STREAM_CODEC, bytes)));
    }
}
