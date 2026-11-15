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
 * The server's answer to a {@link StatsRequestPayload}: the requested stat values of every online player. The server sends it only to the client that asked, and only with the IDs it admitted.
 * <p>
 * Wire format of protocol version 1 (fixed by {@code protocol/v1/stats_response.bin}): the ID list as in the request, VarInt player count, then each {@link PlayerStats}. The player count has no
 * limit: the client trusts the server, while the server limits what a client may send.
 *
 * @param statIds the stat names the values belong to, in order
 * @param players one entry per online player, each with one value per stat ID
 */
public record StatsResponsePayload(List<String> statIds, List<PlayerStats> players) implements CustomPacketPayload {
    /**
     * The payload type {@code scoreline:stats_response}; the receiver uses it to pick the handler.
     */
    public static final CustomPacketPayload.Type<StatsResponsePayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stats_response"));

    /**
     * Turns a stats response into bytes and back.
     */
    public static final StreamCodec<ByteBuf, StatsResponsePayload> STREAM_CODEC = StreamCodec.composite(
            // The stat IDs, encoded exactly as in the request.
            ByteBufCodecs.stringUtf8(Protocol.MAX_STAT_ID_LENGTH).apply(ByteBufCodecs.list(Protocol.MAX_STAT_IDS)), StatsResponsePayload::statIds,
            // The players: a VarInt count without a limit, then each entry as PlayerStats.STREAM_CODEC writes it.
            PlayerStats.STREAM_CODEC.apply(ByteBufCodecs.list()), StatsResponsePayload::players,
            // Decoding builds the payload from both lists; the constructor checks that they line up.
            StatsResponsePayload::new);

    /**
     * Copies both lists and checks that every entry has one value per stat ID, so a misaligned response can never be encoded.
     *
     * @param statIds the stat names the values belong to
     * @param players one entry per online player
     * @throws IllegalArgumentException if an entry's value count differs from the number of stat IDs
     * @throws IllegalArgumentException if an ID appears twice
     * @throws NullPointerException if a list or one of its elements is {@code null}
     */
    public StatsResponsePayload {
        statIds = List.copyOf(statIds);
        players = List.copyOf(players);

        // Check there are no duplicated stat IDs in the List
        Set<String> uniqueIds = new HashSet<>();
        for (String id : statIds) {
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("duplicate stat ID " + id);
            }
        }

        // Check the players provide the same amount of stats values as there are stat IDs
        final int statIdsSize = statIds.size();
        for (PlayerStats playerStats : players) {
            if (playerStats.values().size() != statIdsSize) {
                throw new IllegalArgumentException("player " + playerStats.player() + " has " + playerStats.values().size() + " values for " + statIdsSize + " stat IDs");
            }
        }
    }

    /**
     * Returns the payload type {@code scoreline:stats_response}, which tells the receiver which handler gets this payload.
     *
     * @return {@link #TYPE}
     */
    @Override
    public CustomPacketPayload.Type<StatsResponsePayload> type() {
        return TYPE;
    }
}
