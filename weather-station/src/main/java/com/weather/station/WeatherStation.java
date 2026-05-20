package com.weather.station;

public class WeatherStation {

    public static void main(String[] args) throws InterruptedException {
        WeatherStationProducer clientPipeline = new WeatherStationProducer("127.0.0.1:9092", "weather_status");
        TemporaryMockGenerator dataEngine = new TemporaryMockGenerator();
        long trackingStationId = 1L;

        System.out.println("Starting weather station array data simulation stream...");

        for (int iterations = 0; iterations < 60; iterations++) {
            if (dataEngine.shouldDropMessage()) {
                System.out.println(">> Network Intercept: Packets dropped programmatically [10% Rule]");
                Thread.sleep(1000);
                continue;
            }

            String activePayload = dataEngine.generateMockJson(trackingStationId);
            System.out.println("Emitting Station Status -> " + activePayload);

            clientPipeline.sendData(String.valueOf(trackingStationId), activePayload);
            Thread.sleep(1000);
        }

        clientPipeline.shutdown();
        System.out.println("Simulation process concluded successfully.");
    }
}