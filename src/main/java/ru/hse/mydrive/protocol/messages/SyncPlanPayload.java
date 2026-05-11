package ru.hse.mydrive.protocol.messages;

import java.util.ArrayList;
import java.util.List;

public class SyncPlanPayload {
    public List<String> toUpload = new ArrayList<>();

    public SyncPlanPayload() {}

    public SyncPlanPayload(List<String> toUpload) {
        this.toUpload = toUpload;
    }
}
