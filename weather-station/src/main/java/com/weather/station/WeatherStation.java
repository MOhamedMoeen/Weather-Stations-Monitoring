package com.weather.station;

import com.weather.station.adapter.OpenMeteoAdapter;

public class WeatherStation {

    public static void main(String[] args) throws InterruptedException {

        long stationId = Long.parseLong(
                System.getenv().getOrDefault("STATION_ID", "1"));

        String kafkaBootstrap = System.getenv().getOrDefault(
                "KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092");

        // STATION_ID=0 → Open-Meteo adapter (real weather data)
        // STATION_ID=1-10 → Mock weather station
        if (stationId == 0) {
            try {
                new OpenMeteoAdapter().run();
            } catch (Exception e) {
                System.err.println("OpenMeteoAdapter failed: " + e.getMessage());
            }
        } else {
            WeatherStationProducer producer = new WeatherStationProducer(
                    kafkaBootstrap, "weather_status");
            new WeatherStationRunner(stationId, producer).run();
        }
    }
}