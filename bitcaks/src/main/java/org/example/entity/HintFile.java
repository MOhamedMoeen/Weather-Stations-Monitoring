package org.example.entity;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
    public synchronized void write (String key,long valueSize,long timestamp,long offset) throws IOException {
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

    public long load(Map<String,KeyDir> keyDir) throws IOException {
        long last = 0;
        file.seek(0);
        while (file.getFilePointer() < file.length()) {
            try {
                long offset = file.readLong();
                long keySize = file.readLong();
                long valueSize = file.readLong();
                long timestamp = file.readLong();
                byte[] keyBytes = new byte[(int) keySize];
                file.readFully(keyBytes);

                String key = new String(keyBytes, StandardCharsets.UTF_8);
                keyDir.put(key, new KeyDir(fileId, offset, valueSize, timestamp));
                long recordSize =3L * Long.BYTES + keySize +valueSize;

                last = offset + recordSize;

            } catch (Exception e) {

                break;
            }
        }
        return last;
    }


}
