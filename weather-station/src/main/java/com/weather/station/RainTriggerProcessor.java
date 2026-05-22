package com.weather.station;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import java.util.Properties;

public class RainTriggerProcessor {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "weather-stream-processor");
        String kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        ObjectMapper mapper = new ObjectMapper();

        KStream<String, String> rawSourceStream = builder.stream("weather_status");

        KStream<String, String> highHumidityFilter = rawSourceStream.filter((key, value) -> {
            try {
                JsonNode root = mapper.readTree(value);
                int humidityValue = root.path("weather").path("humidity").asInt();
                return humidityValue > 70; 
            } catch (Exception e) {
                return false; 
            }
        });

        KStream<String, String> alertPayloads = highHumidityFilter.mapValues(value -> {
            try {
                JsonNode root = mapper.readTree(value);
                long id = root.path("station_id").asLong();
                long seq = root.path("s_no").asLong();
                int humidity = root.path("weather").path("humidity").asInt();
                return String.format(
                    "{\"event\":\"CRITICAL_RAIN_ALERT\",\"station_id\":%d,\"s_no\":%d,\"humidity\":%d,\"status\":\"RAIN_DETECTED\"}",
                    id, seq, humidity
                );
            } catch (Exception e) {
                return "{\"event\":\"UNKNOWN_ALERT_ERROR\"}";
            }
        });

        alertPayloads.to("rain_alerts", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams engine = new KafkaStreams(builder.build(), props);
        System.out.println("Rain Trigger Engine running...");
        engine.start();

        Runtime.getRuntime().addShutdownHook(new Thread(engine::close));
    }
}