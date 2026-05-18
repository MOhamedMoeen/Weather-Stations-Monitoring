package com.weather.station;

public class WeatherStation {
    public static void main(String[] args) throws InterruptedException {
        long stationId = Long.parseLong(
                System.getenv().getOrDefault("STATION_ID", "1"));
        new WeatherStationRunner(stationId).run();
    }
}