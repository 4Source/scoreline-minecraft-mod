package io.github.foursource.scoreline.net.payload;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Test helper for the payload tests: turns messages into bytes and back, and reads the golden files.
 * <p>
 * A codec works on a Netty {@code ByteBuf}, while a test wants plain {@code byte[]} to compare with {@code assertArrayEquals}. These methods do that conversion in one place, so each test reads as
 * "encode, compare with the golden, decode".
 */
final class PayloadBytes {
    private PayloadBytes() {}

    /**
     * Writes an ID list in the wire format of the request and the response, but without their limits. The payloads' own codecs check the limits when encoding too, so they cannot produce the oversized
     * bytes that the decoders must reject.
     */
    static final StreamCodec<ByteBuf, List<String>> UNLIMITED_IDS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /**
     * Builds {@code count} different stat IDs ({@code scoreline:stat_0}, {@code scoreline:stat_1}, …). The payloads reject duplicate IDs, so a list at a limit needs distinct ones.
     *
     * @param count how many IDs
     * @return the IDs, all different
     */
    static List<String> distinctIds(int count) {
        return IntStream.range(0, count).mapToObj(i -> "scoreline:stat_" + i).toList();
    }

    /**
     * Reads a golden file from the test resources.
     * <p>
     * Tests pass the version as a literal, never {@code Protocol.VERSION}: a version's tests check that version's files forever, also after the version is raised.
     *
     * @param version the protocol version, for example {@code 1} for the folder {@code protocol/v1/}
     * @param name the file name without extension, for example {@code "hello"} for {@code hello.bin}
     * @return the file's bytes
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if the file does not exist
     */
    static byte[] readGolden(int version, String name) throws IOException {
        // Gradle puts src/test/resources on the test classpath; the leading "/" starts at its root
        String path = "/protocol/v" + version + "/" + name + ".bin";
        try (InputStream in = PayloadBytes.class.getResourceAsStream(path)) {
            return Objects.requireNonNull(in, "golden file " + path + " not found").readAllBytes();
        }
    }

    /**
     * Encodes a value with a codec.
     *
     * @param codec the codec, for example {@code HelloPayload.STREAM_CODEC}
     * @param value the value to encode
     * @param <T> the value's type
     * @return exactly the bytes the codec wrote
     */
    static <T> byte[] encode(StreamCodec<? super ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, value);
        // Only the written bytes; buffer.array() would return the whole backing array, padded with zeros
        return ByteBufUtil.getBytes(buffer);
    }

    /**
     * Decodes bytes with a codec. To check what is left unread afterwards, wrap the bytes yourself with {@code Unpooled.wrappedBuffer} and call the codec directly.
     *
     * @param codec the codec, for example {@code HelloPayload.STREAM_CODEC}
     * @param bytes the bytes to decode
     * @param <T> the decoded value's type
     * @return the decoded value
     */
    static <T> T decode(StreamCodec<? super ByteBuf, T> codec, byte[] bytes) {
        return codec.decode(Unpooled.wrappedBuffer(bytes));
    }
}
