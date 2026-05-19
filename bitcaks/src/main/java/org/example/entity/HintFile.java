package org.example.entity;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HintFile  implements AutoCloseable {
    private final String path;
    private final long fileId;
    private final RandomAccessFile file;
    public long getFileId() {
        return fileId;
    }

    public HintFile(String path, long fileId)throws IOException {
        this.path = path;
        this.fileId = fileId;
        this.file = new RandomAccessFile(path,"rw");
    }
    @Override
    public void close() throws Exception {
        file.close();
    }
    public void write (String key,long valueSize,long timestamp,long offset) throws IOException {
        file.seek(file.length());
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        long keySize = keyBytes.length;
        int totalSize = 4 * Long.BYTES + keyBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        buffer.putLong(offset);
        buffer.putLong(keySize);
        buffer.putLong(valueSize);
        buffer.putLong(timestamp);
        buffer.put(keyBytes);
        file.write(buffer.array());
    }


}
