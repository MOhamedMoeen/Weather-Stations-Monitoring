package com.weather.central;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.central.archive.ParquetHandler;
import com.weather.central.model.WeatherStatus;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaConsumers {

    private static final int cache_size = 100_000;
    private static final String BITCASK_BASE_URL =
            System.getenv().getOrDefault("BITCASK_URL", "http://localhost:8080");

    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    private static final Map<String, Boolean> processedMessages = new LinkedHashMap<String, Boolean>(cache_size, 0.75f,
            true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > cache_size;
        }
    };

    static ObjectMapper mapper = new ObjectMapper();

    // Parse the nested JSON into WeatherStatus
    public static WeatherStatus parse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        return new WeatherStatus(
                root.get("station_id").asLong(),
                root.get("s_no").asLong(),
                root.get("battery_status").asText(),
                root.get("status_timestamp").asLong(),
                root.get("weather").get("humidity").asInt(),
                root.get("weather").get("temperature").asInt(),
                root.get("weather").get("wind_speed").asInt());
    }

    public static void main(String[] args) throws Exception {

        ParquetHandler handler = new ParquetHandler();
        Producer<String, String> invalidMessagesProducer = createInvalidMessagesProducer();

        // archive consumer
        Consumer<String, String> archiveConsumer = createConsumer("archiving-group", "earliest");

        // Subscribe to the topic
        archiveConsumer.subscribe(Collections.singletonList("weather_status"));
        System.out.println("Subscribed to topic: " + "weather_status");

        // Poll for records
        try {
            while (true) {
                ConsumerRecords<String, String> records = archiveConsumer.poll(Duration.ofMillis(100));
                // sending records to parquet handler
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        WeatherStatus status = parse(record.value());
                        String messageId = status.getStation_id() + "-" + status.getS_no();

                        // Process only new messages
                        if (!processedMessages.containsKey(messageId)) {
                            processedMessages.put(messageId, true);
                            handler.addRecord(status);
                            updateBitcask(status);
                        } else {
                            System.out.println("Duplicate message ignored: " + messageId);
                        }
                    } catch (Exception e) {
                        System.err.println(
                                "Failed to parse message. Sending to Invalid Messages queue: " + record.value());
                        invalidMessagesProducer
                                .send(new ProducerRecord<>("invalid-messages", record.key(), record.value()));
                    }
                }
            }
        } finally {
            archiveConsumer.close();
            invalidMessagesProducer.close();
            handler.close();
        }
    }

    private static Consumer<String, String> createConsumer(String groupId, String offset) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offset);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");

        return new KafkaConsumer<>(props);
    }

    private static Producer<String, String> createInvalidMessagesProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
    private static void updateBitcask(WeatherStatus status) {
        try {
            String key = String.valueOf(status.getStation_id());
            String value = mapper.writeValueAsString(status); // store full JSON as value

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(BITCASK_BASE_URL + "/keys/" + key))
                    .header("Content-Type", "text/plain")
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(value))
                    .build();

            httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Failed to update Bitcask for station "
                    + status.getStation_id() + ": " + e.getMessage());
        }
    }
}
