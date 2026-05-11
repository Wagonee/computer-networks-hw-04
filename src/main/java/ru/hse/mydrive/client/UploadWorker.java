package ru.hse.mydrive.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.mydrive.common.Checksum;
import ru.hse.mydrive.protocol.Codec;
import ru.hse.mydrive.protocol.Frame;
import ru.hse.mydrive.protocol.FrameDecoder;
import ru.hse.mydrive.protocol.FrameEncoder;
import ru.hse.mydrive.protocol.MessageType;
import ru.hse.mydrive.protocol.messages.FileAckPayload;
import ru.hse.mydrive.protocol.messages.FileBeginPayload;
import ru.hse.mydrive.protocol.messages.HelloPayload;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class UploadWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(UploadWorker.class);
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final long ACK_TIMEOUT_SEC = 600;

    private final int workerId;
    private final ClientConfig config;
    private final DirectoryScanner scanner;
    private final BlockingQueue<String> queue;
    private final EventLoopGroup group;

    public UploadWorker(int workerId,
                        ClientConfig config,
                        DirectoryScanner scanner,
                        BlockingQueue<String> queue,
                        EventLoopGroup group) {
        this.workerId = workerId;
        this.config = config;
        this.scanner = scanner;
        this.queue = queue;
        this.group = group;
    }

    @Override
    public void run() {
        AckCollector collector = new AckCollector();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new FrameDecoder());
                        ch.pipeline().addLast(new FrameEncoder());
                        ch.pipeline().addLast(collector);
                    }
                });

        Channel ch = null;
        try {
            ch = b.connect(config.serverHost, config.serverPort).sync().channel();
            ch.writeAndFlush(new Frame(MessageType.HELLO,
                    Codec.toJson(new HelloPayload(config.clientId)))).sync();

            String name;
            while ((name = queue.poll()) != null) {
                uploadOne(ch, collector, name);
            }
        } catch (Exception e) {
            LOG.error("worker {} failed: {}", workerId, e.toString());
        } finally {
            if (ch != null) {
                ch.close();
            }
        }
    }

    private void uploadOne(Channel ch, AckCollector collector, String name) throws Exception {
        Path file = scanner.resolve(name);
        long size = Files.size(file);
        long crc = Checksum.crc32(file);

        CompletableFuture<FileAckPayload> ackFuture = new CompletableFuture<>();
        collector.expect(name, ackFuture);

        long t0 = System.nanoTime();
        ch.writeAndFlush(new Frame(MessageType.FILE_BEGIN,
                Codec.toJson(new FileBeginPayload(name, size, crc)))).sync();

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[CHUNK_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                byte[] chunk = (n == buf.length) ? buf.clone() : java.util.Arrays.copyOf(buf, n);
                while (!ch.isWritable()) {
                    Thread.sleep(1);
                }
                ch.writeAndFlush(new Frame(MessageType.FILE_CHUNK, chunk));
            }
        }
        ch.writeAndFlush(Frame.empty(MessageType.FILE_END)).sync();

        FileAckPayload ack = ackFuture.get(ACK_TIMEOUT_SEC, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        if (ack.ok) {
            double mbps = size / 1024.0 / 1024.0 / Math.max(elapsedMs, 1) * 1000.0;
            LOG.info("worker {} uploaded {} ({} bytes) in {} ms ({} MB/s)",
                    workerId, name, size, elapsedMs, String.format("%.2f", mbps));
        } else {
            LOG.error("worker {} upload of {} REJECTED: {}", workerId, name, ack.reason);
        }
    }

    private static class AckCollector extends SimpleChannelInboundHandler<Frame> {
        private final LinkedBlockingQueue<CompletableFuture<FileAckPayload>> pending = new LinkedBlockingQueue<>();

        void expect(String name, CompletableFuture<FileAckPayload> future) {
            pending.add(future);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Frame frame) {
            if (frame.type() != MessageType.FILE_ACK) {
                return;
            }
            FileAckPayload ack = Codec.fromJson(frame.payload(), FileAckPayload.class);
            CompletableFuture<FileAckPayload> future = pending.poll();
            if (future != null) {
                future.complete(ack);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            CompletableFuture<FileAckPayload> future;
            while ((future = pending.poll()) != null) {
                future.completeExceptionally(cause);
            }
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            CompletableFuture<FileAckPayload> future;
            while ((future = pending.poll()) != null) {
                future.completeExceptionally(new RuntimeException("connection closed before ACK"));
            }
        }
    }
}
