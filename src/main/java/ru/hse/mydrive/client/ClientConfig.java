package ru.hse.mydrive.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

public class ClientConfig {

    public final Path configPath;
    public final String clientId;
    public final Path watchDir;
    public final String serverHost;
    public final int serverPort;
    public final int maxConnections;

    private ClientConfig(Path configPath, String clientId, Path watchDir, String serverHost, int serverPort, int maxConnections) {
        this.configPath = configPath;
        this.clientId = clientId;
        this.watchDir = watchDir;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.maxConnections = maxConnections;
    }

    public static ClientConfig load(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        String id = props.getProperty("clientId", "").trim();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            props.setProperty("clientId", id);
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "auto-generated clientId persisted on first run");
            }
        }
        Path dir = Path.of(props.getProperty("watchDir", "client-data").trim());
        String host = props.getProperty("serverHost", "127.0.0.1").trim();
        int port = Integer.parseInt(props.getProperty("serverPort", "9090").trim());
        int conns = Integer.parseInt(props.getProperty("maxConnections", "4").trim());
        if (conns < 1 || conns > 32) {
            throw new IllegalArgumentException("maxConnections must be in [1, 32], got " + conns);
        }
        return new ClientConfig(path, id, dir, host, port, conns);
    }
}
