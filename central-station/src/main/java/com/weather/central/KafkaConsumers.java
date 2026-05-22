package com.weather.central;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import com.weather.central.archive.WeatherParquetHandler;
import com.weather.central.archive.AlertsParquetHandler;
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

    private static final int CACHE_SIZE = 100_000;
    private static final String BITCASK_BASE_URL = System.getenv().getOrDefault("BITCASK_URL", "http://localhost:8080");

    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    private static final Map<String, Boolean> processedMessages = new LinkedHashMap<String, Boolean>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    static ObjectMapper mapper = new ObjectMapper();

    private static final Schema rainAlertSchema = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "RainAlert",
              "fields": [
                {"name": "event",      "type": "string"},
                {"name": "station_id", "type": "long"},
                {"name": "s_no",       "type": "long"},
                {"name": "humidity",   "type": "int"},
                {"name": "status",     "type": "string"}
              ]
            }
            """);

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

    // Parse rain alert JSON into GenericRecord
    private static GenericRecord processRainAlert(String jsonMessage) {
        try {
            JsonNode root = mapper.readTree(jsonMessage);

            GenericRecord record = new GenericData.Record(rainAlertSchema);
            record.put("event",      root.path("event").asText());
            record.put("station_id", root.path("station_id").asLong());
            record.put("s_no",       root.path("s_no").asLong());
            record.put("humidity",   root.path("humidity").asInt());
            record.put("status",     root.path("status").asText());

            return record;

        } catch (Exception e) {
            System.err.println("Failed to process rain alert message: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws Exception {

        WeatherParquetHandler weatherHandler = new WeatherParquetHandler();
        AlertsParquetHandler alertsHandler = new AlertsParquetHandler();
        Producer<String, String> invalidMessagesProducer = createInvalidMessagesProducer();

        Consumer<String, String> archiveConsumer = createConsumer("archiving-group", "earliest");
        Consumer<String, String> rainAlertsConsumer = createConsumer("rain-alerts-group", "latest");

        archiveConsumer.subscribe(Collections.singletonList("weather_status"));
        rainAlertsConsumer.subscribe(Collections.singletonList("rain_alerts"));

        System.out.println("Subscribed to topics: weather_status, rain_alerts");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                archiveConsumer.close();
                rainAlertsConsumer.close();
                invalidMessagesProducer.close();
                weatherHandler.close();
                alertsHandler.close();
                System.out.println("Shutdown complete.");
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        try {
            while (true) {

                // ── weather_status consumer ──────────────────────────────────
                ConsumerRecords<String, String> weatherRecords = archiveConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : weatherRecords) {
                    try {
                        WeatherStatus status = parse(record.value());
                        String messageId = status.getStation_id() + "-" + status.getS_no();

                        if (!processedMessages.containsKey(messageId)) {
                            processedMessages.put(messageId, true);
                            weatherHandler.addRecord(status);
                            updateBitcask(status);
                        } else {
                            System.out.println("Duplicate message ignored: " + messageId);
                        }

                    } catch (Exception e) {
                        System.err.println("Failed to parse weather message. Sending to invalid-messages: " + record.value());
                        invalidMessagesProducer.send(new ProducerRecord<>("invalid-messages", record.key(), record.value()));
                    }
                }

                // ── rain_alerts consumer ─────────────────────────────────────
                ConsumerRecords<String, String> rainAlertRecords = rainAlertsConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : rainAlertRecords) {
                    try {
                        GenericRecord rainRecord = processRainAlert(record.value());
                        if (rainRecord != null) {
                            alertsHandler.addRecord(rainRecord);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse rain alert. Sending to invalid-messages: " + record.value());
                        invalidMessagesProducer.send(new ProducerRecord<>("invalid-messages", record.key(), record.value()));
                    }
                }
            }

        } finally {
            archiveConsumer.close();
            rainAlertsConsumer.close();
            invalidMessagesProducer.close();
            weatherHandler.close();
            alertsHandler.close();
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
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private static void updateBitcask(WeatherStatus status) {
        try {
            String key = String.valueOf(status.getStation_id());
            String value = mapper.writeValueAsString(status);

            java.net.URL url = new java.net.URL(BITCASK_BASE_URL + "/keys/" + key);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "text/plain");
            conn.setDoOutput(true);
            conn.getOutputStream().write(value.getBytes());
            conn.getResponseCode();
            conn.disconnect();

        } catch (Exception e) {
            System.err.println("Failed to update Bitcask for station " + status.getStation_id() + ": " + e.getMessage());
        }
    }
}