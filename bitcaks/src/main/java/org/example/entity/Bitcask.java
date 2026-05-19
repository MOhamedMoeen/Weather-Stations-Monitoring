    package org.example.entity;

    import java.io.File;
    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.util.*;
    import java.util.concurrent.ConcurrentHashMap;

    public class Bitcask implements AutoCloseable {
        private final Map<Long, Segment> segments;
        private final Map<String, KeyDir> keyDir;
        private final String dataDirectory;
        private Segment activeSegment;
        private static final long MAX_SEGMENT_SIZE = 1024 * 1024;

        public Bitcask(String dataDirectory) throws IOException {
            this.dataDirectory = dataDirectory;
            this.keyDir = new ConcurrentHashMap<>();
            this.segments = new ConcurrentHashMap<>();

            File file = new File(dataDirectory);
            if (!file.exists()) {
                file.mkdirs();
            }

            File[] files = file.listFiles((d, name) -> name.endsWith(".data"));
            if (files == null || files.length == 0) {
                long fileId = 1;
                String path = dataDirectory + "/segment_" + fileId + ".data";
                Segment segment = new Segment(path, fileId);
                segments.put(fileId, segment);
                this.activeSegment = segment;
                return;
            }
            Arrays.sort(files, Comparator.comparingLong(this::getFileId));
            for (File cur : files) {
                Long fileId = getFileId(cur);
                Segment segment = new Segment(cur.getPath(), fileId);
                segments.put(fileId, segment);
                HintFile hintFile = segment.getHintFile();
                long last = hintFile.load(keyDir);
                for (RecordOffset ro : segment.iterateOffset(last)) {
                    Record record = ro.record;
                    keyDir.put(record.key, new KeyDir(
                            fileId, ro.offset, record.valueSize, record.timestamp
                    ));
                }
            }
            long lastFileId = getFileId(files[files.length - 1]);
            activeSegment = segments.get(lastFileId);

        }

        private long getFileId(File file) {
            String name = file.getName();
            return Long.parseLong(name
                    .replace("segment_", "")
                    .replace(".data", ""));
        }

        @Override
        public void close() throws Exception {

            for (Segment segment :
                    segments.values()) {

                segment.close();
            }
        }

        private void rotateSegment() throws IOException {

            long newFileId = Collections.max(segments.keySet()) + 1;
            String path = dataDirectory + "/segment_" + newFileId + ".data";
            Segment segment = new Segment(path, newFileId);
            segments.put(newFileId,segment);
            activeSegment = segment;
        }

        public synchronized void put(String key, String value) throws IOException {
            long timestamp = System.currentTimeMillis();
            long offset = activeSegment.write(key, value, timestamp);
            keyDir.put(key, new KeyDir(activeSegment.getFileId(), offset, value.getBytes(StandardCharsets.UTF_8).length, timestamp));
            if (activeSegment.size() >= MAX_SEGMENT_SIZE) {
                rotateSegment();
            }

        }

        public String get(String key) throws IOException {
            KeyDir cur = keyDir.get(key);
            if (cur == null) {
                return null;
            }
            Segment segment = segments.get(cur.fileId);
            if (segment == null) {
                return null;
            }
            return segment.read(cur.offset, cur.valueSize);
        }
        public Map<String, String> getAll() throws IOException {
            Map<String, String> result = new HashMap<>();
            for(String key : keyDir.keySet()) {
                result.put(key,get(key));
            }
            return result;
        }

    }
