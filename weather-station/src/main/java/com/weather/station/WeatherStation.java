package com.weather.station;

import com.weather.station.adapter.OpenMeteoAdapter;

public class WeatherStation {
    public static void main(String[] args) throws InterruptedException {
        // long stationId = Long.parseLong(
        // System.getenv().getOrDefault("STATION_ID", "0"));
        // if (stationId == 0) {
        // try {
        try {
            new OpenMeteoAdapter().run();
        } catch (Exception e) {
            System.err.println("OpenMeteoAdapter failed: " + e.getMessage());
        }
        // } else {
        // new WeatherStationRunner(stationId).run();
        // }
    }
}