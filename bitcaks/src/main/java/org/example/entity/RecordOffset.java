package org.example.entity;

public class RecordOffset {

    public Record record;
    public long offset;

    public RecordOffset(Record record, long offset) {
        this.record = record;
        this.offset = offset;
    }
}