package io.github.foursource.scoreline.net.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The three payloads on the wire, and the most a client can make the server decode.
 * <p>
 * Encode benchmarks write into a buffer from {@link ByteBufAllocator#DEFAULT}, as the game's network pipeline does: Minecraft's string writer takes a temporary buffer from the output buffer's
 * allocator for every string, and with Netty's unpooled allocator that is a slow native allocation the game never makes. Decode benchmarks read bytes written once in setup, into a buffer from the
 * same allocator because the game decodes from its pipeline's pooled buffers, and rewind them on every call.
 */
@State(Scope.Thread)
public class ProtocolBenchmark {
    private StatsRequestPayload request;
    private ByteBuf requestOut;
    private ByteBuf requestEncoded;
    private ByteBuf largestRequestEncoded;
    private HelloPayload hello;
    private ByteBuf helloOut;
    private ByteBuf helloEncoded;

    @Setup
    public void setUp() {
        request = new StatsRequestPayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS));
        requestOut = ByteBufAllocator.DEFAULT.buffer();
        requestEncoded = encoded(StatsRequestPayload.STREAM_CODEC, request);

        // The largest request the decoder accepts: the most IDs, each as long as allowed, all different. The limit counts characters, so each one takes 3 bytes in UTF-8 (CJK ideographs from
        // U+4E00), the most bytes a client can send; ASCII digits would be the cheapest case
        List<String> largest = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_STAT_IDS; i++) {
            largest.add(String.valueOf((char) (0x4E00 + i)).repeat(Protocol.MAX_STAT_ID_LENGTH));
        }
        largestRequestEncoded = encoded(StatsRequestPayload.STREAM_CODEC, new StatsRequestPayload(largest));

        hello = HelloPayload.current();
        helloOut = ByteBufAllocator.DEFAULT.buffer();
        helloEncoded = encoded(HelloPayload.STREAM_CODEC, hello);
    }

    /** The response's input depends on the player count; only the response benchmarks take this state. */
    @State(Scope.Thread)
    public static class StatsResponseInput {
        @Param({ "1", "10", "50" })
        public int players;

        StatsResponsePayload payload;
        ByteBuf out;
        ByteBuf encoded;

        @Setup
        public void setUp() {
            List<PlayerStats> rows = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                // Fixed UUIDs and values: the same input in every run and in both versions of a comparison
                rows.add(new PlayerStats(new UUID(0, i), List.of(72_000L + i, (long) i)));
            }
            payload = new StatsResponsePayload(List.of(Protocol.PLAY_TIME, Protocol.DEATHS), rows);
            out = ByteBufAllocator.DEFAULT.buffer();
            encoded = encoded(StatsResponsePayload.STREAM_CODEC, payload);
        }
    }

    @Benchmark
    public int encodeStatsResponse(StatsResponseInput input) {
        return send(input.out, StatsResponsePayload.STREAM_CODEC, input.payload);
    }

    @Benchmark
    public StatsResponsePayload decodeStatsResponse(StatsResponseInput input) {
        return StatsResponsePayload.STREAM_CODEC.decode(input.encoded.readerIndex(0));
    }

    @Benchmark
    public int encodeStatsRequest() {
        return send(requestOut, StatsRequestPayload.STREAM_CODEC, request);
    }

    @Benchmark
    public StatsRequestPayload decodeStatsRequest() {
        return StatsRequestPayload.STREAM_CODEC.decode(requestEncoded.readerIndex(0));
    }

    @Benchmark
    public StatsRequestPayload decodeLargestStatsRequest() {
        return StatsRequestPayload.STREAM_CODEC.decode(largestRequestEncoded.readerIndex(0));
    }

    @Benchmark
    public int encodeHello() {
        return send(helloOut, HelloPayload.STREAM_CODEC, hello);
    }

    @Benchmark
    public HelloPayload decodeHello() {
        return HelloPayload.STREAM_CODEC.decode(helloEncoded.readerIndex(0));
    }

    /**
     * Writes the payload as Minecraft sends it: the type's identifier, then the payload's own bytes. Returns the byte count, so the JIT cannot drop the work.
     */
    private static <T extends CustomPacketPayload> int send(ByteBuf out, StreamCodec<ByteBuf, T> codec, T payload) {
        out.clear();
        Identifier.STREAM_CODEC.encode(out, payload.type().id());
        codec.encode(out, payload);
        return out.writerIndex();
    }

    /** The payload's bytes, written once; decoding only moves the reader index, so a benchmark rewinds them. */
    private static <T> ByteBuf encoded(StreamCodec<ByteBuf, T> codec, T payload) {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        codec.encode(buffer, payload);
        return buffer;
    }
}
