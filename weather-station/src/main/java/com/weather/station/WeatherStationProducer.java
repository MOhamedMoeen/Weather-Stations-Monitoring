package com.weather.station;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.Properties;

public class WeatherStationProducer {
    private final KafkaProducer<String, String> producer;
    private final String topic;

    public WeatherStationProducer(String bootstrapServers, String topic) {
        this.topic = topic;

        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        this.producer = new KafkaProducer<>(properties);
    }

    public void sendData(String partitionKey, String jsonPayload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, jsonPayload);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("Ingestion transmission failed: " + exception.getMessage());
            }
        });
    }

    public void shutdown() {
        producer.flush();
        producer.close();
    }
}