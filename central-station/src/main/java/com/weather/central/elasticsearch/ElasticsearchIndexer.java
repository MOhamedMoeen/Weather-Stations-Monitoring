package com.weather.central.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import com.weather.central.archive.AlertsParquetHandler;
import com.weather.central.archive.WeatherParquetHandler;
import com.weather.central.model.WeatherStatus;

import org.apache.avro.generic.GenericRecord;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class ElasticsearchIndexer {

    private RestClient restClient;
    private ElasticsearchClient esClient;
    private ObjectMapper mapper;
    private Producer<String, String> invalidMessagesProducer;

    public ElasticsearchIndexer() {
        this.mapper = new ObjectMapper();
        this.invalidMessagesProducer = createInvalidMessagesProducer();
        String esHost = System.getenv().getOrDefault("ES_HOST", "localhost");
        int esPort = Integer.parseInt(System.getenv().getOrDefault("ES_PORT", "9200"));
        this.restClient = RestClient
                .builder(new HttpHost(esHost, esPort, "http"))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                this.restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(transport);
        createIndex();
    }

    private void createIndex() {
        try {
            boolean exists = esClient.indices()
                    .exists(e -> e.index("weather-statuses"))
                    .value();

            if (!exists) {
                esClient.indices().create(c -> c
                        .index("weather-statuses")
                        .mappings(m -> m
                                .properties("station_id", p -> p.long_(l -> l))
                                .properties("s_no", p -> p.long_(l -> l))
                                .properties("battery_status", p -> p.keyword(k -> k))
                                .properties("status_timestamp", p -> p.long_(l -> l))
                                .properties("humidity", p -> p.integer(i -> i))
                                .properties("temperature", p -> p.integer(i -> i))
                                .properties("wind_speed", p -> p.integer(i -> i))));
            }
        } catch (Exception e) {
            System.err.println("Failed to create index: " + e.getMessage());
        }
    }

    public void indexWeatherRecords(List<WeatherStatus> records) {
    try {
        if (records.isEmpty()) {
            System.out.println("No records to index.");
            return;
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (WeatherStatus status : records) {
            br.operations(op -> op
                    .index(idx -> idx
                            .index("weather-statuses")
                            .id(status.getStation_id() + "-" + status.getS_no()) // deterministic ID
                            .document(status)));
        }

        BulkResponse result = esClient.bulk(br.build());
        if (result.errors()) {
            List<BulkResponseItem> items = result.items();
            for (int i = 0; i < items.size(); i++) {
                BulkResponseItem item = items.get(i);
                if (item.error() != null) {
                    WeatherStatus failedRecord = records.get(i);
                    String errorReason = item.error().reason();
                    String logEntry = String.format("{\"error\": \"%s\", \"record\": %s}",
                            errorReason, mapper.writeValueAsString(failedRecord));
                    invalidMessagesProducer.send(new ProducerRecord<>("invalid-messages",
                            failedRecord.getStation_id() + "-" + failedRecord.getS_no(),
                            logEntry));
                }
            }
        } else {
            System.out.println("Successfully indexed " + records.size() + " records.");
        }
    } catch (Exception e) {
        System.err.println("Failed to bulk index weather records.");
        e.printStackTrace();
    }
}

public void indexAlertRecords(List<GenericRecord> records) {
    try {
        if (records.isEmpty()) {
            System.out.println("No records to index.");
            return;
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (GenericRecord record : records) {
            Map<String, Object> doc = mapper.readValue(record.toString(), Map.class);
            br.operations(op -> op
                    .index(idx -> idx
                            .index("rain-alerts")
                            .id(doc.get("station_id") + "-" + doc.get("s_no")) // deterministic ID
                            .document(doc)));
        }

        BulkResponse result = esClient.bulk(br.build());
        if (result.errors()) {
            List<BulkResponseItem> items = result.items();
            for (int i = 0; i < items.size(); i++) {
                BulkResponseItem item = items.get(i);
                if (item.error() != null) {
                    GenericRecord failedRecord = records.get(i);
                    String errorReason = item.error().reason();
                    String logEntry = String.format("{\"error\": \"%s\", \"record\": %s}",
                            errorReason, failedRecord.toString());
                    invalidMessagesProducer.send(new ProducerRecord<>("invalid-messages",
                            failedRecord.get("station_id").toString(),
                            logEntry));
                }
            }
        } else {
            System.out.println("Successfully indexed " + records.size() + " alert records.");
        }
    } catch (Exception e) {
        System.err.println("Failed to bulk index alert records.");
        e.printStackTrace();
    }
}

    public void close() {
        try {
            if (restClient != null) {
                restClient.close();
                invalidMessagesProducer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
     private Producer<String, String> createInvalidMessagesProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
