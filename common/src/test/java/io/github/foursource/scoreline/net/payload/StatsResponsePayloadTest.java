package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;

public class StatsResponsePayloadTest {
    /** Player A of T-32. The two halves differ, so swapped halves change the bytes. Must match the golden file. */
    private static final UUID A = new UUID(0xA123456789ABCDEFL, 0xAEDCBA9876543210L);

    /** Player B of T-32. Must match the golden file. */
    private static final UUID B = new UUID(0xB123456789ABCDEFL, 0xBEDCBA9876543210L);

    /** The two IDs most tests use; each entry needs exactly two values. */
    private static final List<String> TWO_IDS = List.of(Protocol.PLAY_TIME, Protocol.DEATHS);

    /**
     * Bug: values written as int (the second row's playtime exceeds Integer.MAX_VALUE); rows reordered; UUID halves swapped; a type ID that matches neither the handler nor the golden file's name
     */
    @Test
    void t32_responseMatchesGoldenV1() throws IOException {
        StatsResponsePayload response = new StatsResponsePayload(TWO_IDS, List.of(new PlayerStats(A, List.of(72_000L, 3L)), new PlayerStats(B, List.of(4_000_000_000L, 0L))));
        byte[] golden = PayloadBytes.readGolden(1, "stats_response");

        assertArrayEquals(golden, PayloadBytes.encode(StatsResponsePayload.STREAM_CODEC, response));
        // Records compare their lists element by element, so this also checks the row order
        assertEquals(response, PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, golden));
        assertEquals("scoreline:stats_response", response.type().id().toString());
    }

    /** The misaligned rows of T-33, T-53 and T-54, each for a response with {@link #TWO_IDS}. */
    static Stream<Arguments> misalignedRows() {
        return Stream.of(
                // T-33: misaligned entries encoded silently, so the client reads the next player's UUID as a value
                Arguments.argumentSet("T-33 one value too few", List.of(new PlayerStats(A, List.of(72_000L))), A),
                // T-53: the check compares with < instead of !=, so surplus values are encoded
                Arguments.argumentSet("T-53 one value too many", List.of(new PlayerStats(A, List.of(72_000L, 3L, 7L))), A),
                // T-54: only the first row is checked; the error names the wrong row
                Arguments.argumentSet("T-54 second row misaligned", List.of(new PlayerStats(A, List.of(72_000L, 3L)), new PlayerStats(B, List.of(4_000_000_000L))), B));
    }

    /**
     * Bug: see each case in {@link #misalignedRows()}
     */
    @ParameterizedTest
    @MethodSource("misalignedRows")
    void t33_t53_t54_responseRejectsMisalignedRows(List<PlayerStats> rows, UUID misaligned) {
        ExceptionAssertions.assertThrowsWithMessage(IllegalArgumentException.class, misaligned.toString(), () -> new StatsResponsePayload(TWO_IDS, rows));
    }

    /**
     * Bug: the decoder bypasses the constructor check, so the client shows values under the wrong columns
     */
    @Test
    void t55_responseDecoderRejectsMisalignedRow() {
        // Written field by field, because the payload's own codec cannot encode a misaligned response
        ByteBuf buffer = Unpooled.buffer();
        PayloadBytes.UNLIMITED_IDS.encode(buffer, TWO_IDS);
        ByteBufCodecs.VAR_INT.encode(buffer, 1);
        UUIDUtil.STREAM_CODEC.encode(buffer, A);
        ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()).encode(buffer, List.of(72_000L));
        byte[] oneValueForTwoIds = ByteBufUtil.getBytes(buffer);

        ExceptionAssertions.assertThrowsWithMessage(IllegalArgumentException.class, A.toString(), () -> PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, oneValueForTwoIds));
    }

    /**
     * Bug: List.copyOf dropped, so the response changes when the caller's lists change
     */
    @Test
    void t58_responseCopiesLists() {
        List<String> statIds = new ArrayList<>(TWO_IDS);
        List<PlayerStats> players = new ArrayList<>(List.of(new PlayerStats(A, List.of(72_000L, 3L))));
        StatsResponsePayload response = new StatsResponsePayload(statIds, players);

        statIds.set(0, "questmod:quests_completed");
        players.add(new PlayerStats(B, List.of(4_000_000_000L, 0L)));

        assertEquals(TWO_IDS, response.statIds());
        assertEquals(List.of(new PlayerStats(A, List.of(72_000L, 3L))), response.players());
    }

    /**
     * Bug: the response's own ID codec drifts from the request's and loses its count limit; off-by-one
     */
    @Test
    void t60_responseLimitsIdCount() {
        List<String> atLimit = PayloadBytes.distinctIds(Protocol.MAX_STAT_IDS);
        List<String> overLimit = PayloadBytes.distinctIds(Protocol.MAX_STAT_IDS + 1);

        assertEquals(new StatsResponsePayload(atLimit, List.of()), PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, idsWithoutRows(atLimit)));

        byte[] tooMany = idsWithoutRows(overLimit);
        // Minecraft's message names the count that broke the limit
        ExceptionAssertions.assertThrowsWithMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_IDS + 1), () -> PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, tooMany));
    }

    /**
     * Bug: the response's ID codec loses its length limit; off-by-one
     */
    @Test
    void t61_responseLimitsIdLength() {
        List<String> atLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH));
        List<String> overLimit = List.of("a".repeat(Protocol.MAX_STAT_ID_LENGTH + 1));

        assertEquals(new StatsResponsePayload(atLimit, List.of()), PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, idsWithoutRows(atLimit)));

        byte[] tooLong = idsWithoutRows(overLimit);
        // Minecraft's message names the length that broke the limit
        ExceptionAssertions.assertThrowsWithMessage(DecoderException.class, String.valueOf(Protocol.MAX_STAT_ID_LENGTH + 1), () -> PayloadBytes.decode(StatsResponsePayload.STREAM_CODEC, tooLong));
    }

    /**
     * Bug: the client cannot tell which column a value belongs to
     */
    @Test
    void t64_responseRejectsDuplicateIds() {
        ExceptionAssertions.assertThrowsWithMessage(IllegalArgumentException.class, Protocol.DEATHS, () -> new StatsResponsePayload(List.of(Protocol.DEATHS, Protocol.DEATHS), List.of()));
    }

    /**
     * Writes a response in the wire format without its limits: the IDs, then a player count of 0. With no rows, the values cannot fail before the ID limits are checked.
     */
    private static byte[] idsWithoutRows(List<String> statIds) {
        ByteBuf buffer = Unpooled.buffer();
        PayloadBytes.UNLIMITED_IDS.encode(buffer, statIds);
        ByteBufCodecs.VAR_INT.encode(buffer, 0);
        return ByteBufUtil.getBytes(buffer);
    }
}
