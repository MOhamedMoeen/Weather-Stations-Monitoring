package org.example.entity;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;


//is a Java interface that allows an object to be closed automatically when used inside try
public class Segment implements AutoCloseable{
    private final String path;
    private final long fileId;
    private final FileChannel channel;
    private final HintFile hintFile;

    public long getFileId() {
        return fileId;
    }

    public Segment(String path, long fileId)throws IOException {
        this.path = path;
        this.fileId = fileId;
        this.channel = FileChannel.open(Path.of(path), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        String hintPath = path.replace(".data", ".hint");
        this.hintFile=new HintFile(hintPath,fileId);
    }

    @Override
    public void close() throws Exception {
        channel.close();
        hintFile.close();
    }

    public long write (String key,String value,long timestamp) throws IOException {
        long offset = channel.size();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        long keySize = keyBytes.length;
        long valueSize = valueBytes.length;
        int totalSize = 3 * Long.BYTES + keyBytes.length + valueBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putLong(keySize);

        buffer.putLong(valueSize);
        buffer.putLong(timestamp);
        buffer.put(valueBytes);
        buffer.put(keyBytes);
        buffer.flip();
        long writePos = offset;
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, writePos);
            writePos += written;
        }
        channel.force(true);
        hintFile.write(key, valueSize, timestamp, offset);
        return offset;
    }

    public String read(long offset,long valueSize) throws IOException {
        long valOffset = offset+3L*Long.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate((int) valueSize);
        channel.read(buffer, valOffset);
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    public List<RecordOffset> iterate() throws IOException {
       return iterateOffset(0);
    }
    public List<RecordOffset> iterateOffset(long start) throws IOException {
        List<RecordOffset> results =
                new ArrayList<>();
        long position = start;
        while (position < channel.size()) {
            long offset = position;
            try {
                ByteBuffer header = ByteBuffer.allocate(3 * Long.BYTES);
                channel.read(header, position);
                header.flip();
                long keySize = header.getLong();
                long valueSize = header.getLong();
                long timestamp = header.getLong();

                position += 3L * Long.BYTES;
                ByteBuffer valueBuffer = ByteBuffer.allocate((int) valueSize);
                channel.read(valueBuffer, position);
                position += valueSize;
                ByteBuffer keyBuffer = ByteBuffer.allocate((int) keySize);
                channel.read(keyBuffer, position);
                position += keySize;
                Record record = new Record();
                record.keySize = keySize;
                record.valueSize = valueSize;
                record.timestamp = timestamp;
                record.value = new String(valueBuffer.array(), StandardCharsets.UTF_8);
                record.key = new String(keyBuffer.array(), StandardCharsets.UTF_8);
                results.add(new RecordOffset(record, offset));
            } catch (Exception e) {

                break;
            }
        }

        return results;
    }
    public long size() throws IOException {
        return channel.size();
    }
    public HintFile getHintFile() {
        return hintFile;
    }
}
