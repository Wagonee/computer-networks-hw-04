package ru.hse.mydrive.protocol.messages;

public class FileMeta {
    public String name;
    public long size;
    public long crc32;

    public FileMeta() {}

    public FileMeta(String name, long size, long crc32) {
        this.name = name;
        this.size = size;
        this.crc32 = crc32;
    }
}
