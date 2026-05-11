package ru.hse.mydrive.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class FrameDecoder extends ByteToMessageDecoder {

    private static final int LENGTH_FIELD = 4;
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < LENGTH_FIELD) {
            return;
        }
        in.markReaderIndex();
        int length = in.readInt();
        if (length < 1 || length > MAX_FRAME_LENGTH) {
            ctx.close();
            throw new IllegalStateException("invalid frame length: " + length);
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }
        int typeCode = in.readByte() & 0xFF;
        int payloadLen = length - 1;
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);
        out.add(new Frame(MessageType.fromCode(typeCode), payload));
    }
}
