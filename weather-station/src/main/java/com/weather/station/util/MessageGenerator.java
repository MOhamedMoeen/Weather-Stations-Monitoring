package com.weather.station.util;

import com.weather.station.model.WeatherData;

import java.util.Random;

public class MessageGenerator {

    // 30% low, 40% medium, 30% high
    private static final String[] BATTERY_POOL = {
            "low", "low", "low",
            "medium", "medium", "medium", "medium",
            "high", "high", "high"
    };

    private final Random random = new Random();

    public String randomBattery() {
        return BATTERY_POOL[random.nextInt(BATTERY_POOL.length)];
    }

    public boolean shouldDrop() {
        return random.nextDouble() < 0.10;
    }

    public WeatherData randomWeather() {
        int humidity = random.nextInt(101); // 0 – 100 %
        int temperature = random.nextInt(60) + 60; // 60 – 120 °F
        int windSpeed = random.nextInt(50) + 1; // 1 – 50 km/h
        return new WeatherData(humidity, temperature, windSpeed);
    }
}