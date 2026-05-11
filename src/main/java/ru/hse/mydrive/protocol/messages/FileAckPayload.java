package ru.hse.mydrive.protocol.messages;

public class FileAckPayload {
    public String name;
    public boolean ok;
    public String reason;

    public FileAckPayload() {}

    public FileAckPayload(String name, boolean ok, String reason) {
        this.name = name;
        this.ok = ok;
        this.reason = reason;
    }
}
