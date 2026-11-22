package io.github.foursource.scoreline.net.payload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.foursource.scoreline.Constants;
import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A client's request for stat values: the client sends it to the server while TAB is held, at most every {@link Protocol#REFRESH_SECONDS} seconds.
 * <p>
 * Wire format of protocol version 1 (fixed by {@code protocol/v1/stats_request.bin}): VarInt count, then each ID as a UTF-8 string (VarInt byte length, bytes). Decoding throws a
 * {@code DecoderException} for more than {@link Protocol#MAX_STAT_IDS} IDs or an ID longer than {@link Protocol#MAX_STAT_ID_LENGTH} characters; Minecraft then drops the connection, as for any
 * malformed packet.
 *
 * @param statIds the vanilla stat names the client wants, for example {@link Protocol#PLAY_TIME}
 */
public record StatsRequestPayload(List<String> statIds) implements CustomPacketPayload {
    /**
     * The payload type {@code scoreline:stats_request}; the receiver uses it to pick the handler.
     */
    public static final CustomPacketPayload.Type<StatsRequestPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stats_request"));

    /**
     * Turns a stats request into bytes and back.
     */
    public static final StreamCodec<ByteBuf, StatsRequestPayload> STREAM_CODEC = StreamCodec.composite(
            // The ID list: a VarInt count (at most MAX_STAT_IDS), then each ID as a UTF-8 string (at most MAX_STAT_ID_LENGTH characters). Both limits throw a DecoderException.
            ByteBufCodecs.stringUtf8(Protocol.MAX_STAT_ID_LENGTH).apply(ByteBufCodecs.list(Protocol.MAX_STAT_IDS)), StatsRequestPayload::statIds,
            // Decoding builds the payload from the decoded list.
            StatsRequestPayload::new);

    /** TEMPORARY for T-104: a field the JIT cannot prove unused, so the allocation stays. */
    private static volatile byte[] t104Sink;

    /**
     * Copies the list, so the payload cannot change after it is built.
     *
     * @param statIds the requested stat names; must not contain {@code null}
     * @throws NullPointerException if the list or one of its IDs is {@code null}
     * @throws IllegalArgumentException if an ID appears twice
     */
    public StatsRequestPayload {
        t104Sink = new byte[1024]; // TEMPORARY for T-104
        statIds = List.copyOf(statIds);

        // Check there are no duplicated stat IDs in the List
        Set<String> uniqueIds = new HashSet<>();
        for (String id : statIds) {
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("duplicate stat ID " + id);
            }
        }
    }

    /**
     * Returns the payload type {@code scoreline:stats_request}, which tells the receiver which handler gets this payload.
     *
     * @return {@link #TYPE}
     */
    @Override
    public CustomPacketPayload.Type<StatsRequestPayload> type() {
        return TYPE;
    }
}
