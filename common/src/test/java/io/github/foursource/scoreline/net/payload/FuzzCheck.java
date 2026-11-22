package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;

/**
 * The rule every fuzz test checks for its decoder: any bytes give either a payload or one of the exceptions that end in a clean disconnect; never another exception, a hang or an allocation without
 * bound (which Jazzer reports by itself). A decoded payload must survive a round trip: encoding it and decoding the bytes gives an equal payload.
 */
final class FuzzCheck {
    private FuzzCheck() {}

    /**
     * Decodes the bytes and checks the outcome.
     *
     * @param codec the decoder under test
     * @param data the fuzzer's bytes
     * @param <T> the payload type
     */
    static <T> void decode(StreamCodec<ByteBuf, T> codec, byte[] data) {
        T decoded;
        try {
            decoded = codec.decode(Unpooled.wrappedBuffer(data));
        }
        // Minecraft wraps whatever a payload decoder throws and disconnects the client; these are the exceptions malformed input should cause: a limit or a broken encoding (DecoderException),
        // a rule of the payload (IllegalArgumentException) and too few bytes (IndexOutOfBoundsException). Anything else, such as a NullPointerException, points at a bug.
        catch (DecoderException | IllegalArgumentException | IndexOutOfBoundsException e) {
            return;
        }
        // Vanilla's VarInt and VarLong readers throw a plain RuntimeException for more than 5 or 10 bytes; Netty wraps it like the others, so it ends in a clean disconnect too
        catch (RuntimeException e) {
            if (e.getClass() == RuntimeException.class && ("VarInt too big".equals(e.getMessage()) || "VarLong too big".equals(e.getMessage()))) {
                return;
            }
            throw e;
        }
        assertEquals(decoded, Wire.decode(codec, Wire.encode(codec, decoded)), "the decoded payload does not survive a round trip");
    }
}
