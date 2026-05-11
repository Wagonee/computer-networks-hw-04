package ru.hse.mydrive.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public final class Checksum {

    private static final int BUFFER_SIZE = 64 * 1024;

    private Checksum() {}

    public static long crc32(Path path) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                crc.update(buf, 0, n);
            }
        }
        return crc.getValue();
    }
}
