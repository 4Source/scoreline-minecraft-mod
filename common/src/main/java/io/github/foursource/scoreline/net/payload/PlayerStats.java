package io.github.foursource.scoreline.net.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One player's entry in a {@link StatsResponsePayload}: {@code values().get(i)} is the value of the response's {@code statIds().get(i)}.
 * <p>
 * Not a payload of its own; the response encodes it with {@link #STREAM_CODEC}. Wire format: the UUID as two longs (most significant first), then a VarInt count and one VarLong per value.
 *
 * @param player the player's UUID
 * @param values the player's stat values, in the order of the response's stat IDs
 */
public record PlayerStats(UUID player, List<Long> values) {

    /**
     * Turns one player's entry into bytes and back.
     */
    public static final StreamCodec<ByteBuf, PlayerStats> STREAM_CODEC = StreamCodec.composite(
            // The player's UUID: two longs, most significant first.
            UUIDUtil.STREAM_CODEC, PlayerStats::player,
            // The values: a VarInt count (at most MAX_STAT_IDS), then one VarLong per value. Long, because play time in ticks exceeds Integer.MAX_VALUE after about three years of playing.
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(Protocol.MAX_STAT_IDS)), PlayerStats::values,
            // Decoding builds the entry from the UUID and the values.
            PlayerStats::new);

    /**
     * Copies the values, so the entry cannot change after it is built.
     *
     * @param player the player's UUID
     * @param values the player's stat values
     * @throws NullPointerException if the list or one of its values is {@code null}
     */
    public PlayerStats {
        Objects.requireNonNull(player, "player");
        values = List.copyOf(values);
    }
}
