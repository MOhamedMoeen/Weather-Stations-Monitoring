package org.example.entity;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


//is a Java interface that allows an object to be closed automatically when used inside try
public class Segment implements AutoCloseable{
    private final String path;
    private final long fileId;
    private final RandomAccessFile file;

    public Segment(String path, long fileId)throws IOException {
        this.path = path;
        this.fileId = fileId;
        this.file = new RandomAccessFile(path,"rw");
    }

    @Override
    public void close() throws Exception {
        file.close();
    }

    public long write (String key,String value,long timestamp) throws IOException {
        file.seek(file.length());
        long offset = file.getFilePointer();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int keySize = keyBytes.length;
        int valueSize = valueBytes.length;
        file.writeLong(keySize);
        file.writeLong(valueSize);
        file.writeLong(timestamp);
        file.write(valueBytes);
        file.write(keyBytes);
        file.getFD().sync();
        return offset;
    }

    public String read(long offset,long valueSize) throws IOException {
        long valOffset = offset+3L*Long.BYTES;
        file.seek(valOffset);
        byte[] valueBytes = new byte[(int)valueSize];
        file.readFully(valueBytes); // read fully is never partial , read may get partial read
        return new String(valueBytes,StandardCharsets.UTF_8);
    }

    public List<RecordOffset> iterate() throws IOException {
        List<RecordOffset> results = new ArrayList<>();
        file.seek(0);
        while (file.getFilePointer()<file.length()){
            long offset = file.getFilePointer();
            try {
                Record record = new Record();
                record.keySize=file.readLong();
                record.valueSize=file.readLong();
                record.timestamp=file.readLong();
                byte[] valueBytes = new byte[(int)record.valueSize];
                file.readFully(valueBytes);
                byte[] keyBytes = new byte[(int)record.keySize];
                file.readFully(keyBytes);
                record.value=new String(valueBytes,StandardCharsets.UTF_8);
                record.key=new String(keyBytes,StandardCharsets.UTF_8);
                results.add(new RecordOffset(record,offset));

            }
            catch (Exception e){
                break;
            }
        }
        return results;
    }
    public long size() throws IOException {
        return file.length();
    }
}
