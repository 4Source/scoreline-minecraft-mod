package io.github.foursource.scoreline.net.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.github.foursource.scoreline.net.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class HelloPayloadTest {
    /**
     * Bug: field order or width differs from the documented format; a type ID that matches neither the handler nor the golden file's name
     */
    @Test
    void t27_helloMatchesGoldenV1() throws IOException {
        HelloPayload hello = new HelloPayload(1, 2);
        byte[] golden = PayloadBytes.readGolden(1, "hello");

        // The encoder writes exactly the stored bytes ...
        assertArrayEquals(golden, PayloadBytes.encode(HelloPayload.STREAM_CODEC, hello));
        // ... and the stored bytes decode back to the same message
        assertEquals(hello, PayloadBytes.decode(HelloPayload.STREAM_CODEC, golden));
        // Compared with a literal, so a typo in the TYPE constant is caught too
        assertEquals("scoreline:hello", hello.type().id().toString());
    }

    /**
     * Bug: a newer server's longer hello makes older clients fail
     */
    @Test
    void t28_helloSkipsUnknownTrailingBytes() throws IOException {
        // A v1 hello followed by fields a newer server might append
        ByteBuf buffer = Unpooled.wrappedBuffer(PayloadBytes.readGolden(1, "hello"), new byte[] { 7, 8, 9 });

        assertEquals(new HelloPayload(1, 2), HelloPayload.STREAM_CODEC.decode(buffer));
        // Minecraft drops the connection if a payload is not read to the end
        assertEquals(0, buffer.readableBytes());
    }

    /**
     * Bug: the hand-written codec writes one byte per field; T-27's 01 02 reads the same as a byte or a VarInt
     */
    @Test
    void t59_helloWritesFieldsAsVarInts() {
        HelloPayload hello = new HelloPayload(300, 200);
        // VarInt: 7 bits per byte, low bits first, top bit set while more bytes follow.
        // 300 = 0b10_0101100 → 0x2C | 0x80, then 0x02. 200 = 0b1_1001000 → 0x48 | 0x80, then 0x01.
        byte[] expected = { (byte) (0x2C | 0x80), 0x02, (byte) (0x48 | 0x80), 0x01 };

        assertArrayEquals(expected, PayloadBytes.encode(HelloPayload.STREAM_CODEC, hello));
        assertEquals(hello, PayloadBytes.decode(HelloPayload.STREAM_CODEC, expected));
    }

    /**
     * Bug: the server's hello swaps or drops the protocol constants
     */
    @Test
    void t52_currentHelloUsesProtocolConstants() {
        // Constants, not literals: this checks the version the code speaks now, so it keeps passing when Protocol.VERSION is raised
        assertEquals(new HelloPayload(Protocol.VERSION, Protocol.REFRESH_SECONDS), HelloPayload.current());
    }
}
