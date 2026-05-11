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
import ru.hse.mydrive.protocol.Codec;
import ru.hse.mydrive.protocol.Frame;
import ru.hse.mydrive.protocol.FrameDecoder;
import ru.hse.mydrive.protocol.FrameEncoder;
import ru.hse.mydrive.protocol.MessageType;
import ru.hse.mydrive.protocol.messages.FileListPayload;
import ru.hse.mydrive.protocol.messages.FileMeta;
import ru.hse.mydrive.protocol.messages.HelloPayload;
import ru.hse.mydrive.protocol.messages.SyncPlanPayload;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SyncOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(SyncOrchestrator.class);
    private static final long PLAN_TIMEOUT_SEC = 60;

    private final ClientConfig config;
    private final EventLoopGroup group;

    public SyncOrchestrator(ClientConfig config, EventLoopGroup group) {
        this.config = config;
        this.group = group;
    }

    public void syncOnce() throws Exception {
        DirectoryScanner scanner = new DirectoryScanner(config.watchDir);
        List<FileMeta> files = scanner.scan();
        LOG.info("scanned {} files in {}", files.size(), config.watchDir.toAbsolutePath());

        List<String> plan = exchangePlan(files);
        LOG.info("server plan: upload {} of {} files", plan.size(), files.size());
        if (plan.isEmpty()) {
            return;
        }

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(plan);
        int workers = Math.min(config.maxConnections, plan.size());
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        Future<?>[] futures = new Future<?>[workers];
        long t0 = System.nanoTime();
        for (int i = 0; i < workers; i++) {
            futures[i] = pool.submit(new UploadWorker(i, config, scanner, queue, group));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        LOG.info("sync round done in {} ms using {} connections", elapsedMs, workers);
    }

    private List<String> exchangePlan(List<FileMeta> files) throws Exception {
        CompletableFuture<List<String>> planFuture = new CompletableFuture<>();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new FrameDecoder());
                        ch.pipeline().addLast(new FrameEncoder());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<Frame>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Frame frame) {
                                if (frame.type() == MessageType.SYNC_PLAN) {
                                    SyncPlanPayload p = Codec.fromJson(frame.payload(), SyncPlanPayload.class);
                                    planFuture.complete(p.toUpload);
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                planFuture.completeExceptionally(cause);
                                ctx.close();
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                if (!planFuture.isDone()) {
                                    planFuture.completeExceptionally(new RuntimeException("control connection closed early"));
                                }
                            }
                        });
                    }
                });

        Channel ch = b.connect(config.serverHost, config.serverPort).sync().channel();
        try {
            ch.writeAndFlush(new Frame(MessageType.HELLO,
                    Codec.toJson(new HelloPayload(config.clientId)))).sync();
            ch.writeAndFlush(new Frame(MessageType.FILE_LIST,
                    Codec.toJson(new FileListPayload(files)))).sync();
            return planFuture.get(PLAN_TIMEOUT_SEC, TimeUnit.SECONDS);
        } finally {
            ch.close();
        }
    }
}
