package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;

/**
 * Reads every stored example message of every protocol version this code finds, without naming any version or file, so a later release can run this very test against newer folders: the code players
 * have then reads the new messages.
 * <p>
 * A file whose name is a payload type this code knows must decode with no byte left unread. A request or response must encode back to the same bytes, because their formats never change. A hello must
 * read its folder's version: a newer hello may carry more fields, which this decoder skips. Files of payload types this code does not know belong to newer versions and are skipped.
 */
public class GoldenFilesTest {
    /** The payloads this code knows, by the file name their golden has: the path of their type's identifier. */
    private static final Map<String, StreamCodec<ByteBuf, ?>> CODECS = Map.of(
            // Entry for the scoreline:hello
            HelloPayload.TYPE.id().getPath(), HelloPayload.STREAM_CODEC,
            // Entry for the scoreline:stats_request
            StatsRequestPayload.TYPE.id().getPath(), StatsRequestPayload.STREAM_CODEC,
            // Entry for the scoreline:stats_response
            StatsResponsePayload.TYPE.id().getPath(), StatsResponsePayload.STREAM_CODEC);

    /**
     * Bug: the golden test reads only v1, so an old release cannot check new folders; a newer hello or a new payload type makes the old test fail instead of being read or skipped
     */
    @Test
    void t148_everyKnownGoldenOfEveryVersionDecodes() throws IOException, URISyntaxException {
        URL root = Objects.requireNonNull(GoldenFilesTest.class.getResource("/protocol"), "no protocol folder in the test resources");
        List<Path> versions;
        try (Stream<Path> folders = Files.list(Path.of(root.toURI()))) {
            versions = folders.filter(folder -> folder.getFileName().toString().matches("v\\d+")).sorted().toList();
        }
        assertFalse(versions.isEmpty(), "no version folder found");

        int read = 0;
        for (Path version : versions) {
            int number = Integer.parseInt(version.getFileName().toString().substring(1));
            List<Path> files;
            try (Stream<Path> entries = Files.list(version)) {
                files = entries.filter(file -> file.getFileName().toString().endsWith(".bin")).sorted().toList();
            }
            for (Path file : files) {
                String type = file.getFileName().toString().replaceFirst("\\.bin$", "");
                if (CODECS.containsKey(type)) {
                    check(file, number, Files.readAllBytes(file), CODECS.get(type));
                    read++;
                }
            }
        }
        assertFalse(read == 0, "no known golden file found");
    }

    @SuppressWarnings("unchecked")
    private static <T> void check(Path file, int version, byte[] bytes, StreamCodec<ByteBuf, T> codec) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        T payload = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), file + ": bytes left unread");
        if (payload instanceof HelloPayload hello) {
            assertEquals(version, hello.protocolVersion(), file + ": the hello's protocol version differs from its folder");
        }
        else {
            assertArrayEquals(bytes, Wire.encode((StreamCodec<? super ByteBuf, T>) codec, payload), file + ": encodes to other bytes");
        }
    }
}
