package ru.hse.mydrive.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import ru.hse.mydrive.protocol.FrameDecoder;
import ru.hse.mydrive.protocol.FrameEncoder;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Storage storage;

    public ServerInitializer(Storage storage) {
        this.storage = storage;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new FrameDecoder());
        ch.pipeline().addLast(new FrameEncoder());
        ch.pipeline().addLast(new ServerSession(storage));
    }
}
