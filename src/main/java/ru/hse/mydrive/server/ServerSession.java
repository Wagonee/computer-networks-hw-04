package ru.hse.mydrive.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.mydrive.protocol.Codec;
import ru.hse.mydrive.protocol.Frame;
import ru.hse.mydrive.protocol.MessageType;
import ru.hse.mydrive.protocol.messages.FileAckPayload;
import ru.hse.mydrive.protocol.messages.FileBeginPayload;
import ru.hse.mydrive.protocol.messages.FileListPayload;
import ru.hse.mydrive.protocol.messages.HelloPayload;
import ru.hse.mydrive.protocol.messages.SyncPlanPayload;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.CRC32;

public class ServerSession extends SimpleChannelInboundHandler<Frame> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerSession.class);

    private final Storage storage;
    private String clientId;
    private FileBeginPayload currentMeta;
    private Path currentTemp;
    private OutputStream currentOut;
    private long currentReceived;
    private CRC32 currentCrc;

    public ServerSession(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOG.info("connection opened {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.info("connection closed {}", ctx.channel().remoteAddress());
        closeCurrentSilently();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Frame frame) throws Exception {
        switch (frame.type()) {
            case HELLO -> handleHello(frame);
            case FILE_LIST -> handleFileList(ctx, frame);
            case FILE_BEGIN -> handleFileBegin(frame);
            case FILE_CHUNK -> handleFileChunk(frame);
            case FILE_END -> handleFileEnd(ctx);
            default -> {
                LOG.warn("unexpected frame {} from {}", frame.type(), ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

    private void handleHello(Frame frame) {
        HelloPayload hello = Codec.fromJson(frame.payload(), HelloPayload.class);
        this.clientId = hello.clientId;
        LOG.info("HELLO clientId={}", clientId);
    }

    private void handleFileList(ChannelHandlerContext ctx, Frame frame) throws IOException {
        requireClient();
        FileListPayload list = Codec.fromJson(frame.payload(), FileListPayload.class);
        List<String> plan = storage.plan(clientId, list.files);
        LOG.info("FILE_LIST clientId={} files={} toUpload={}", clientId, list.files.size(), plan.size());
        SyncPlanPayload response = new SyncPlanPayload(plan);
        ctx.writeAndFlush(new Frame(MessageType.SYNC_PLAN, Codec.toJson(response)));
    }

    private void handleFileBegin(Frame frame) throws IOException {
        requireClient();
        currentMeta = Codec.fromJson(frame.payload(), FileBeginPayload.class);
        currentTemp = storage.tempFile(clientId, currentMeta.name);
        currentOut = Files.newOutputStream(currentTemp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        currentReceived = 0;
        currentCrc = new CRC32();
        LOG.info("FILE_BEGIN clientId={} name={} size={}", clientId, currentMeta.name, currentMeta.size);
    }

    private void handleFileChunk(Frame frame) throws IOException {
        if (currentOut == null) {
            throw new IllegalStateException("FILE_CHUNK without FILE_BEGIN");
        }
        byte[] data = frame.payload();
        currentOut.write(data);
        currentCrc.update(data, 0, data.length);
        currentReceived += data.length;
    }

    private void handleFileEnd(ChannelHandlerContext ctx) throws IOException {
        if (currentOut == null) {
            throw new IllegalStateException("FILE_END without FILE_BEGIN");
        }
        currentOut.close();
        currentOut = null;

        boolean sizeOk = currentReceived == currentMeta.size;
        boolean crcOk = currentCrc.getValue() == currentMeta.crc32;
        FileAckPayload ack;
        if (sizeOk && crcOk) {
            Path target = storage.finalFile(clientId, currentMeta.name);
            Files.move(currentTemp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("FILE_END ok clientId={} name={} size={}", clientId, currentMeta.name, currentReceived);
            ack = new FileAckPayload(currentMeta.name, true, null);
        } else {
            Files.deleteIfExists(currentTemp);
            String reason = !sizeOk
                    ? "size mismatch: got " + currentReceived + " expected " + currentMeta.size
                    : "crc mismatch";
            LOG.warn("FILE_END fail clientId={} name={} reason={}", clientId, currentMeta.name, reason);
            ack = new FileAckPayload(currentMeta.name, false, reason);
        }
        currentMeta = null;
        currentTemp = null;
        currentCrc = null;
        currentReceived = 0;
        ctx.writeAndFlush(new Frame(MessageType.FILE_ACK, Codec.toJson(ack)));
    }

    private void requireClient() {
        if (clientId == null) {
            throw new IllegalStateException("missing HELLO");
        }
    }

    private void closeCurrentSilently() {
        if (currentOut != null) {
            try {
                currentOut.close();
            } catch (IOException ignored) {
            }
            try {
                if (currentTemp != null) {
                    Files.deleteIfExists(currentTemp);
                }
            } catch (IOException ignored) {
            }
            currentOut = null;
            currentTemp = null;
            currentMeta = null;
            currentCrc = null;
            currentReceived = 0;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("session error from {}: {}", ctx.channel().remoteAddress(), cause.toString());
        closeCurrentSilently();
        ctx.close();
    }
}
