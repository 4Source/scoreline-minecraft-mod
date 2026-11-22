package io.github.foursource.scoreline.net.payload;

import io.github.foursource.scoreline.Constants;
import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The first message of the protocol: the server sends it to a client that has Scoreline when the player joins.
 * <p>
 * It tells the client which protocol version the server speaks and how often to ask for stats. A client that never gets a hello assumes the server lacks Scoreline and sends nothing.
 * <p>
 * Wire format of protocol version 1 (fixed by {@code protocol/v1/hello.bin}): VarInt protocol version, VarInt refresh seconds. Later versions may append fields; this decoder skips them, so older
 * clients keep working.
 *
 * @param protocolVersion the protocol version the server speaks, see {@link Protocol#VERSION}
 * @param refreshSeconds how many seconds the client waits between two requests while TAB is held
 */
public record HelloPayload(int protocolVersion, int refreshSeconds) implements CustomPacketPayload {
    /**
     * The payload type {@code scoreline:hello}; the receiver uses it to pick the handler.
     */
    public static final CustomPacketPayload.Type<HelloPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "hello"));

    /**
     * Turns a hello into bytes and back. Written by hand rather than with {@code StreamCodec.composite}, because the decoder must skip fields it does not know.
     */
    public static final StreamCodec<ByteBuf, HelloPayload> STREAM_CODEC = StreamCodec.of(HelloPayload::encode, HelloPayload::decode);

    /**
     * The hello this server sends: the current protocol version and refresh interval.
     *
     * @return The payload created with the vales {@link Protocol#VERSION} and {@link Protocol#REFRESH_SECONDS}
     */
    public static HelloPayload current() {
        return new HelloPayload(Protocol.VERSION, Protocol.REFRESH_SECONDS);
    }

    private static void encode(ByteBuf buffer, HelloPayload value) {
        ByteBufCodecs.VAR_INT.encode(buffer, value.protocolVersion());
        ByteBufCodecs.VAR_INT.encode(buffer, value.refreshSeconds());
    }

    private static HelloPayload decode(ByteBuf buffer) {
        int protocolVersion = ByteBufCodecs.VAR_INT.decode(buffer);
        int refreshSeconds = ByteBufCodecs.VAR_INT.decode(buffer);

        // A newer server may append fields after these two. Minecraft drops the connection if a payload is not read to the end, so skip the rest instead of failing on it.
        buffer.skipBytes(buffer.readableBytes());

        return new HelloPayload(protocolVersion, refreshSeconds);
    }

    /**
     * Returns the payload type {@code scoreline:hello}, which tells the receiver which handler gets this payload.
     *
     * @return {@link #TYPE}
     */
    @Override
    public CustomPacketPayload.Type<HelloPayload> type() {
        return TYPE;
    }
}
