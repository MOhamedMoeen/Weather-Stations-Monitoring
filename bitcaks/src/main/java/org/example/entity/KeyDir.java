package org.example.entity;

public class KeyDir {
    public long fileId;
    public long offset;
    public long valueSize;
    public long timestamp;

    public KeyDir(long fileId, long offset, long valueSize, long timestamp) {
        this.fileId = fileId;
        this.offset = offset;
        this.valueSize = valueSize;
        this.timestamp = timestamp;
    }
}
