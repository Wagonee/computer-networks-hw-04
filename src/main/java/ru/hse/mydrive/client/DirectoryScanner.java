package ru.hse.mydrive.client;

import ru.hse.mydrive.common.Checksum;
import ru.hse.mydrive.protocol.messages.FileMeta;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DirectoryScanner {

    private final Path root;

    public DirectoryScanner(Path root) {
        this.root = root;
    }

    public List<FileMeta> scan() throws IOException {
        List<FileMeta> result = new ArrayList<>();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                long size = Files.size(p);
                long crc = Checksum.crc32(p);
                result.add(new FileMeta(p.getFileName().toString(), size, crc));
            }
        }
        return result;
    }

    public Path resolve(String name) {
        return root.resolve(name);
    }
}
