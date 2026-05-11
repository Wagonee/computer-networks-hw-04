package ru.hse.mydrive.protocol.messages;

public class HelloPayload {
    public String clientId;

    public HelloPayload() {}

    public HelloPayload(String clientId) {
        this.clientId = clientId;
    }
}
