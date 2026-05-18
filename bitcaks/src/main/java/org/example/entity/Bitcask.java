package org.example.entity;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Bitcask {
    private final Map <Long,Segment> segments;
    private final Map <String,KeyDir> keyDir;
    private final String dataDirectory;
    private Segment activeSegment;

    public Bitcask(String dataDirectory) throws IOException {
        this.dataDirectory=dataDirectory;
        this.keyDir=new ConcurrentHashMap<>();
        this.segments = new ConcurrentHashMap<>();

        File file=new File(dataDirectory);
        if(!file.exists()){
            file.mkdirs();
        }

        File[] files = file.listFiles(
                (d, name) ->
                        name.endsWith(".data")
        );
        if(files==null||files.length==0){
            long fileId = 1;
            String path = dataDirectory+"/segment_"+fileId+".data";
            Segment segment= new Segment(path,fileId);
            segments.put(fileId,segment);
            this.activeSegment=segment;
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(this::getFileId));
        for(File cur : files){
            Long fileId=getFileId(cur);
            Segment segment= new Segment(cur.getPath(),fileId);
            segments.put(fileId,segment);
            for(RecordOffset ro : segment.iterate()){
                Record record = ro.record;
                keyDir.put(record.key,new KeyDir(
                        fileId,ro.offset,record.valueSize,record.timestamp
                ));
            }
        }
        long lastFileId = getFileId(files[files.length-1]);
        activeSegment=segments.get(lastFileId);

    }
    private long getFileId(File file) {
        String name = file.getName();
        return Long.parseLong( name
                .replace("segment_", "")
                .replace(".data", ""));
    }



}
