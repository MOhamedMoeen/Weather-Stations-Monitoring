package org.example.entity;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class HintFile  implements AutoCloseable {
    private final String path;
    private final long fileId;
    private final FileChannel channel;
    public long getFileId() {
        return fileId;
    }

    public HintFile(String path, long fileId)throws IOException {
        this.path = path;
        this.fileId = fileId;
        this.channel = FileChannel.open(Path.of(path), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }
    @Override
    public void close() throws Exception {
        channel.close();
    }
    public synchronized void write (String key,long valueSize,long timestamp,long offset) throws IOException {
        long position = channel.size();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        long keySize = keyBytes.length;

        int totalSize = 4 * Long.BYTES + keyBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putLong(offset);

        buffer.putLong(keySize);

        buffer.putLong(valueSize);

        buffer.putLong(timestamp);

        buffer.put(keyBytes);

        buffer.flip();


        while (buffer.hasRemaining()) {
            int written=channel.write(buffer, position);
            position+=written;
        }
    }

    public long load(Map<String,KeyDir> keyDir) throws IOException {
        long last = 0;
        long position = 0;
        while (position < channel.size()) {
            try {
                ByteBuffer header = ByteBuffer.allocate(4 * Long.BYTES);
                channel.read(header, position);
                header.flip();
                long offset = header.getLong();
                long keySize = header.getLong();
                long valueSize = header.getLong();
                long timestamp = header.getLong();

                position += 4L * Long.BYTES;

                ByteBuffer keyBuffer = ByteBuffer.allocate((int) keySize);

                channel.read(keyBuffer, position);
                position += keySize;
                String key = new String(keyBuffer.array(), StandardCharsets.UTF_8);
                keyDir.put(key, new KeyDir(fileId, offset, valueSize, timestamp));
                long recordSize = 3L * Long.BYTES + keySize + valueSize;
                last =offset + recordSize;

            } catch (Exception e) {

                break;
            }
        }

        return last;
    }


}
