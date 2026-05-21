package com.weather.central.archive;

import com.weather.central.model.WeatherStatus;
import com.weather.central.elasticsearch.ElasticsearchIndexer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.avro.AvroParquetReader;

import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParquetHandler {

    private static int batch_size = 10000;
    private static String base_dir = "archivedData";
    private static DateTimeFormatter date_format = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    // Avro Schema
    private String SCHEMA_JSON = """
                {
                  "type": "record",
                  "name": "WeatherStatus",
                  "fields": [
                    {"name": "station_id",       "type": "long"},
                    {"name": "s_no",             "type": "long"},
                    {"name": "battery_status",   "type": "string"},
                    {"name": "status_timestamp", "type": "long"},
                    {"name": "humidity",         "type": "int"},
                    {"name": "temperature",      "type": "int"},
                    {"name": "wind_speed",       "type": "int"}
                  ]
                }
            """;

    private Schema schema;
    private List<WeatherStatus> batch;
    private Configuration conf;
    private ElasticsearchIndexer esIndexer;

    public ParquetHandler() {
        this.schema = new Schema.Parser().parse(SCHEMA_JSON);
        this.batch = new ArrayList<>();
        this.conf = new Configuration();
        this.conf.set("fs.file.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());
        this.esIndexer = new ElasticsearchIndexer();
    }

    // Helper function to group records by station and date
    private Map<String, List<WeatherStatus>> groupByStationAndDate(List<WeatherStatus> records) {

        Map<String, List<WeatherStatus>> grouped = new HashMap<>();

        for (WeatherStatus record : records) {
            String date = date_format.format(Instant.ofEpochSecond(record.getStatus_timestamp()));
            String dirPath = String.format("%s/station_id=%d/date=%s", base_dir, record.getStation_id(), date);
            grouped.computeIfAbsent(dirPath, k -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    public void addRecord(WeatherStatus record) throws IOException {

        batch.add(record);

        if (batch.size() >= batch_size) {
            flush();
        }
    }

    // Write batch to parquet file
    public synchronized void flush() throws IOException {
        if (batch.isEmpty())
            return;

        // Grouping records
        Map<String, List<WeatherStatus>> grouped = groupByStationAndDate(batch);

        for (Map.Entry<String, List<WeatherStatus>> entry : grouped.entrySet()) {
            String dirPath = entry.getKey();
            String fileName = "data_" + System.currentTimeMillis() + ".parquet";
            String fullPath = dirPath + "/" + fileName;
            writeBatch(entry.getValue(), fullPath);
            
            // Trigger Elasticsearch indexing for the newly created file!
            esIndexer.indexParquetFile(fullPath, this);
        }

        batch.clear(); // clear after writing
    }

    // Write a batch of records to its parquet file
    private void writeBatch(List<WeatherStatus> batch, String filePath) throws IOException {

        new File(filePath).getParentFile().mkdirs();
        Path path = new Path(filePath);

        ParquetWriter<GenericRecord> writer = AvroParquetWriter
                .<GenericRecord>builder(path)
                .withSchema(schema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build();

        for (WeatherStatus status : batch) {
            // Convert WeatherStatus to Avro GenericRecord
            GenericRecord record = new GenericData.Record(schema);
            record.put("station_id", status.getStation_id());
            record.put("s_no", status.getS_no());
            record.put("battery_status", status.getBattery_status());
            record.put("status_timestamp", status.getStatus_timestamp());
            record.put("humidity", status.getHumidity());
            record.put("temperature", status.getTemperature());
            record.put("wind_speed", status.getWind_speed());

            // Write to parquet file
            writer.write(record);
        }
        // closing writer once
        writer.close();
    }

    // Read all records from a parquet file (used by ElasticsearchIndexer)
    public List<WeatherStatus> readParquet(String filePath) throws IOException {
        List<WeatherStatus> records = new ArrayList<>();
        Path path = new Path(filePath);
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(path).withConf(conf).build()) {
            GenericRecord record;
            while ((record = reader.read()) != null) {
                WeatherStatus status = new WeatherStatus();
                status.setStation_id((Long) record.get("station_id"));
                status.setS_no((Long) record.get("s_no"));
                status.setBattery_status(record.get("battery_status").toString());
                status.setStatus_timestamp((Long) record.get("status_timestamp"));
                status.setHumidity((Integer) record.get("humidity"));
                status.setTemperature((Integer) record.get("temperature"));
                status.setWind_speed((Integer) record.get("wind_speed"));
                records.add(status);
            }
        }
        return records;
    }

    // Call on shutdown to flush remaining records
    public void close() throws IOException {
        flush();
        if (esIndexer != null) {
            esIndexer.close();
        }
    }
}