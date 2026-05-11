package ru.hse.mydrive.server;

import ru.hse.mydrive.common.Checksum;
import ru.hse.mydrive.protocol.messages.FileMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Storage {

    private final Path root;

    public Storage(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public Path userDir(String clientId) throws IOException {
        Path dir = root.resolve(sanitize(clientId));
        Files.createDirectories(dir);
        return dir;
    }

    public List<String> plan(String clientId, List<FileMeta> remote) throws IOException {
        Path dir = userDir(clientId);
        List<String> toUpload = new ArrayList<>();
        for (FileMeta meta : remote) {
            Path local = dir.resolve(sanitize(meta.name));
            if (!Files.exists(local)) {
                toUpload.add(meta.name);
                continue;
            }
            long size = Files.size(local);
            if (size != meta.size) {
                toUpload.add(meta.name);
                continue;
            }
            long crc = Checksum.crc32(local);
            if (crc != meta.crc32) {
                toUpload.add(meta.name);
            }
        }
        return toUpload;
    }

    public Path tempFile(String clientId, String name) throws IOException {
        Path dir = userDir(clientId);
        return dir.resolve(sanitize(name) + ".part");
    }

    public Path finalFile(String clientId, String name) throws IOException {
        Path dir = userDir(clientId);
        return dir.resolve(sanitize(name));
    }

    private static String sanitize(String name) {
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            throw new IllegalArgumentException("invalid name: " + name);
        }
        return n;
    }
}
