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
            long estimatedSize = 3L * Long.BYTES + key.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length;
            if (activeSegment.size()+estimatedSize > MAX_SEGMENT_SIZE&&activeSegment.size()!=0) {
                rotateSegment();
            }
            long offset = activeSegment.write(key, value, timestamp);
            keyDir.put(key, new KeyDir(activeSegment.getFileId(), offset, value.getBytes(StandardCharsets.UTF_8).length, timestamp));


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
        public synchronized void compact() throws Exception {
            List<Long> immutable = new  ArrayList<>();
            for(Long fileId : segments.keySet()) {
                if(fileId < activeSegment.getFileId()) {
                    immutable.add(fileId);
                }
            }
            if(immutable.isEmpty()) return;
            Collections.sort(immutable);
            long rebuiltId = 1;
            String tempPath = dataDirectory + "/compact_" + rebuiltId + ".data";
            Segment compacted = new Segment(tempPath, rebuiltId);
            List<Segment> compactedSegments = new  ArrayList<>();
            compactedSegments.add(compacted);
            for(Long fileId : immutable) {
                Segment segment = segments.get(fileId);
                if(segment == null) continue;
                for(RecordOffset ro : segment.iterate()) {
                    Record record = ro.record;
                    KeyDir kd = keyDir.get(record.key);
                    if(kd!=null&&kd.fileId==fileId&&kd.offset==ro.offset) {
                        long estimated = 3L * Long.BYTES + record.keySize + record.valueSize;
                        if(compacted.size()+estimated>MAX_SEGMENT_SIZE&&compacted.size()!=0) {
                            rebuiltId++;
                            tempPath = dataDirectory + "/compact_" + rebuiltId + ".data";
                            compacted = new Segment(tempPath, rebuiltId);
                            compactedSegments.add(compacted);
                        }
                        long newOffset = compacted.write(record.key,record.value,record.timestamp);

                        //for concurrency
                        KeyDir current =  keyDir.get(record.key);
                        if(current!=null&&current.fileId==fileId&&current.offset==ro.offset) {
                            keyDir.put(record.key, new KeyDir(compacted.getFileId(), newOffset, record.valueSize, record.timestamp));
                        }
                    }

                }
            }
            for(Segment segment : compactedSegments) {
                segment.close();
            }
            for(Long fileId : immutable) {
                Segment segment = segments.remove(fileId);
                if(segment == null) continue;
                segment.close();
                File dataFile = new File(dataDirectory + "/segment_" + fileId + ".data");
                File hintFile = new File(dataDirectory + "/segment_" + fileId + ".hint");
                dataFile.delete();
                hintFile.delete();
            }
            for(Segment segment : compactedSegments) {
                File oldData = new File(dataDirectory,"compact_"+segment.getFileId()+".data");
                File oldHintFile = new File(dataDirectory,"compact_"+segment.getFileId()+".hint");
                File newData = new File(dataDirectory + "/segment_"+segment.getFileId()+".data");
                File newHintFile = new File(dataDirectory + "/segment_"+segment.getFileId()+".hint");
                oldData.renameTo(newData);
                oldHintFile.renameTo(newHintFile);
                Segment newSegment = new Segment(newData.getPath(),segment.getFileId());
                segments.put(segment.getFileId(),newSegment);
            }
        }

    }
