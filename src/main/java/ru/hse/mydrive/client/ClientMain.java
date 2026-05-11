package ru.hse.mydrive.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class ClientMain {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java -jar client.jar <config.properties>");
            System.exit(1);
        }
        ClientConfig config = ClientConfig.load(Paths.get(args[0]));
        LOG.info("clientId={} dir={} server={}:{} maxConnections={}",
                config.clientId, config.watchDir.toAbsolutePath(),
                config.serverHost, config.serverPort, config.maxConnections);

        EventLoopGroup group = new NioEventLoopGroup();
        SyncOrchestrator orchestrator = new SyncOrchestrator(config, group);
        try {
            runRepl(orchestrator);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void runRepl(SyncOrchestrator orchestrator) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("commands: sync | quit");
        while (true) {
            System.out.print("> ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            switch (line) {
                case "" -> {
                }
                case "sync" -> {
                    try {
                        orchestrator.syncOnce();
                    } catch (Exception e) {
                        LOG.error("sync failed: {}", e.toString());
                    }
                }
                case "quit", "exit" -> {
                    return;
                }
                default -> System.out.println("unknown command: " + line);
            }
        }
    }
}
