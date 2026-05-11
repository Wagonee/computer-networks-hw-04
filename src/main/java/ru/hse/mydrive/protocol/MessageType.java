package ru.hse.mydrive.protocol;

public enum MessageType {
    HELLO(1),
    FILE_LIST(2),
    SYNC_PLAN(3),
    FILE_BEGIN(4),
    FILE_CHUNK(5),
    FILE_END(6),
    FILE_ACK(7);

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("unknown message type code: " + code);
    }
}
