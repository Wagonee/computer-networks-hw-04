package ru.hse.mydrive.protocol;

public record Frame(MessageType type, byte[] payload) {
    public static Frame empty(MessageType type) {
        return new Frame(type, new byte[0]);
    }
}
