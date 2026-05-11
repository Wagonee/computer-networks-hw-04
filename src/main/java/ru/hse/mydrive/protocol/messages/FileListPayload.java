package ru.hse.mydrive.protocol.messages;

import java.util.ArrayList;
import java.util.List;

public class FileListPayload {
    public List<FileMeta> files = new ArrayList<>();

    public FileListPayload() {}

    public FileListPayload(List<FileMeta> files) {
        this.files = files;
    }
}
