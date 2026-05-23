package com.weather.central.archive;

import com.weather.central.elasticsearch.ElasticsearchIndexer;

import org.apache.avro.Schema;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertsParquetHandler {

    private static int batch_size = 100;
    private static String base_dir = "rainAlerts";
    private static DateTimeFormatter date_format = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of(ZoneId.systemDefault().getId()));

    // Avro Schema
    private String SCHEMA_JSON = """
                {
                  "type": "record",
                  "name": "RainAlert",
                  "fields": [
                    {"name": "event",            "type": "string"},
                    {"name": "station_id",       "type": "long"},
                    {"name": "s_no",             "type": "long"},
                    {"name": "humidity",         "type": "int"},
                    {"name": "status",       "type": "string"}
                  ]
                }
            """;

    private Schema schema;
    private List<GenericRecord> batch;
    private Configuration conf;
    private ElasticsearchIndexer esIndexer;

    public AlertsParquetHandler() {
        this.schema = new Schema.Parser().parse(SCHEMA_JSON);
        this.batch = new ArrayList<>();
        this.conf = new Configuration();
        this.conf.set("fs.file.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());
        this.esIndexer = new ElasticsearchIndexer();
        File dir = new File(base_dir);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Created base directory: " + base_dir);
        }
    }

    // Helper function to group records by station and date
    private Map<String, List<GenericRecord>> groupByStationAndDate(List<GenericRecord> records) {
        Map<String, List<GenericRecord>> grouped = new HashMap<>();
        String date = LocalDate.now(ZoneId.systemDefault()).format(date_format);

        for (GenericRecord record : records) {
            String dirPath = String.format("%s/station_id=%d/date=%s", base_dir, (Long) record.get("station_id"), date);
            grouped.computeIfAbsent(dirPath, k -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    public void addRecord(GenericRecord record) throws IOException {

        batch.add(record);

        if (batch.size() >= batch_size) {
            flush();
        }
    }

    // Write batch to parquet file
    public synchronized void flush() throws IOException {
        if (batch.isEmpty())
            return;

        List<GenericRecord> toFlush = new ArrayList<>(batch); // snapshot first
        batch.clear();

        Map<String, List<GenericRecord>> grouped = groupByStationAndDate(toFlush);

        // Write all files first
        for (Map.Entry<String, List<GenericRecord>> entry : grouped.entrySet()) {
            String dirPath = entry.getKey();
            String fileName = "data_" + System.currentTimeMillis() + ".parquet";
            String fullPath = dirPath + "/" + fileName;
            writeBatch(entry.getValue(), fullPath);
        }

        // ONE bulk call for entire flush outside the loop
        esIndexer.indexAlertRecords(toFlush);
    }

    // Write a batch of records to its parquet file
    private void writeBatch(List<GenericRecord> batch, String filePath) throws IOException {

        new File(filePath).getParentFile().mkdirs();
        Path path = new Path(filePath);

        ParquetWriter<GenericRecord> writer = AvroParquetWriter
                .<GenericRecord>builder(path)
                .withSchema(schema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build();

        for (GenericRecord record : batch) {
            // Write to parquet file
            writer.write(record);
        }
        // closing writer once
        writer.close();
    }

    // Read all records from a parquet file (used by ElasticsearchIndexer)
    public List<GenericRecord> readParquet(String filePath) throws IOException {
        List<GenericRecord> records = new ArrayList<>();
        Path path = new Path(filePath);
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(path).withConf(conf)
                .build()) {
            GenericRecord record;
            while ((record = reader.read()) != null) {
                records.add(record);
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