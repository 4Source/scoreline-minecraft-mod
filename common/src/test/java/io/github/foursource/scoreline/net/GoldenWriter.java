package io.github.foursource.scoreline.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import io.github.foursource.scoreline.net.payload.HelloPayload;
import io.github.foursource.scoreline.net.payload.PlayerStats;
import io.github.foursource.scoreline.net.payload.StatsRequestPayload;
import io.github.foursource.scoreline.net.payload.StatsResponsePayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Writes the golden files of the current protocol version: one example message per payload, encoded with the payload's own codec.
 * <p>
 * Not run by hand: {@code scripts/generate-goldens.sh} starts it through the Gradle task {@code :common:generateGoldens}, which writes to {@code common/build/goldens/}. The script then copies the
 * files to {@code src/test/resources/protocol/} and asks before adding or changing any of them.
 * <p>
 * The messages are built from the current {@link Protocol} constants plus fixed example data, so after {@link Protocol#VERSION} is raised the same writer produces the new version's folder. The tests
 * keep their own literals, because a version's tests must keep checking that version's files.
 */
public final class GoldenWriter {

    private GoldenWriter() {}

    /**
     * Encodes one payload and writes its bytes as a golden file.
     * <p>
     * The file is {@code <outFolder>/v<Protocol.VERSION>/<type path>.bin}, for example {@code v1/hello.bin} for {@code scoreline:hello}, so the folder follows the current protocol version and the
     * file name follows the payload's type. Missing folders are created and an existing file is overwritten; only the readable bytes are written, not the buffer's whole backing array.
     *
     * @param payload the message to store
     * @param codec the payload's codec, for example {@link HelloPayload#STREAM_CODEC}
     * @param outFolder the folder that receives the version folders
     * @param <T> the payload's type
     * @throws IOException if a folder or the file cannot be written
     */
    private static <T extends CustomPacketPayload> void generate(T payload, StreamCodec<ByteBuf, T> codec, String outFolder) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, payload);
        Path path = Path.of(outFolder, "v" + Protocol.VERSION, payload.type().id().getPath() + ".bin");
        Files.createDirectories(path.getParent());
        Files.write(path, ByteBufUtil.getBytes(buffer));
    }

    /**
     * Writes the golden files of the current protocol version.
     * <p>
     * Errors are not caught: an uncaught exception ends the JVM with a non-zero exit code, so the Gradle task {@code generateGoldens} fails and shows the stack trace.
     *
     * @param args exactly one argument, the output folder
     * @throws IllegalArgumentException if not exactly one argument is passed
     * @throws IOException if a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected 1 argument (the output folder), got " + args.length);
        }
        String outFolder = args[0];

        generate(HelloPayload.current(), HelloPayload.STREAM_CODEC, outFolder);
        generate(new StatsRequestPayload(Arrays.asList(Protocol.PLAY_TIME, Protocol.DEATHS)), StatsRequestPayload.STREAM_CODEC, outFolder);
        generate(new StatsResponsePayload(Arrays.asList(Protocol.PLAY_TIME, Protocol.DEATHS),
                Arrays.asList(new PlayerStats(new UUID(0xA123456789ABCDEFL, 0xAEDCBA9876543210L), Arrays.asList(72_000L, 3L)),
                        new PlayerStats(new UUID(0xB123456789ABCDEFL, 0xBEDCBA9876543210L), Arrays.asList(4_000_000_000L, 0L)))),
                StatsResponsePayload.STREAM_CODEC, outFolder);
    }
}
