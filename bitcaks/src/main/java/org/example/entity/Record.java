package org.example.entity;

public class Record {

    public long keySize;
    public long valueSize;
    public long timestamp;

    public String key;
    public String value;

    public Record() {}

    public Record(String key, String value, long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;

        this.keySize = key.getBytes().length;
        this.valueSize = value.getBytes().length;
    }
}