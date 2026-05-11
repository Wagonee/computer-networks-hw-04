package ru.hse.mydrive.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ServerMain {

    private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java -jar server.jar <config.properties>");
            System.exit(1);
        }
        Properties props = loadProperties(Paths.get(args[0]));
        int port = Integer.parseInt(props.getProperty("port", "9090").trim());
        Path storageDir = Paths.get(props.getProperty("storageDir", "server-storage").trim());

        Storage storage = new Storage(storageDir);
        LOG.info("storage dir: {}", storageDir.toAbsolutePath());

        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ServerInitializer(storage));
            Channel ch = b.bind(port).sync().channel();
            LOG.info("listening on {}", port);
            ch.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return props;
    }
}
