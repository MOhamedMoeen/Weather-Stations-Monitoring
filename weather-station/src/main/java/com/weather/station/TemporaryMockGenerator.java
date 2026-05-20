package com.weather.station;

import java.util.Random;

public class TemporaryMockGenerator {
    private final Random random = new Random();
    private long sequenceNumber = 1;

    public String generateMockJson(long stationId) {
        long timestamp = System.currentTimeMillis() / 1000L;

        // Programmatic battery allocation metrics (30% low, 40% medium, 30% high)
        int batteryRoll = random.nextInt(100);
        String batteryStatus;
        if (batteryRoll < 30) {
            batteryStatus = "low";
        } else if (batteryRoll < 70) {
            batteryStatus = "medium";
        } else {
            batteryStatus = "high";
        }

        // Simulating broad climate ranges to cross evaluation thresholds
        int humidity = 20 + random.nextInt(71); // 20% to 90%
        int temperature = 40 + random.nextInt(61); // 40 to 100 Fahrenheit
        int windSpeed = 5 + random.nextInt(26); // 5 to 30 km/h

        return String.format(
            "{" +
            "\"station_id\": %d, " +
            "\"s_no\": %d, " +
            "\"battery_status\": \"%s\", " +
            "\"status_timestamp\": %d, " +
            "\"weather\": {" +
                "\"humidity\": %d, " +
                "\"temperature\": %d, " +
                "\"wind_speed\": %d" +
            "}" +
            "}",
            stationId, sequenceNumber++, batteryStatus, timestamp, humidity, temperature, windSpeed
        );
    }

    public boolean shouldDropMessage() {
        return random.nextInt(100) < 10; // Forced 10% structural failure
    }
}