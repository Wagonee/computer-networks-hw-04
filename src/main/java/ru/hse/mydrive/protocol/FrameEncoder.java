package ru.hse.mydrive.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class FrameEncoder extends MessageToByteEncoder<Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame msg, ByteBuf out) {
        byte[] payload = msg.payload();
        int length = 1 + payload.length;
        out.writeInt(length);
        out.writeByte(msg.type().code());
        out.writeBytes(payload);
    }
}
