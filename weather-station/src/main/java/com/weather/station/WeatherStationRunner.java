package com.weather.station;

import com.google.gson.Gson;
import com.weather.station.model.WeatherMessage;
import com.weather.station.util.MessageGenerator;

public class WeatherStationRunner {

    private final long stationId;
    private final MessageGenerator generator = new MessageGenerator();
    private final Gson gson = new Gson();
    private long sNo = 0;

    private final WeatherStationProducer producer;

    public WeatherStationRunner(long stationId, WeatherStationProducer producer) {
        this.stationId = stationId;
        this.producer = producer;
    }

    public void run() throws InterruptedException {
        System.out.println("Weather Station " + stationId + " started.");
        while (true) {
            sNo++;
            if (generator.shouldDrop()) {
                System.out.println("[Station " + stationId + "] Message #" + sNo + " dropped.");
                Thread.sleep(1000);
                continue;
            }
            WeatherMessage message = new WeatherMessage(
                    stationId,
                    sNo,
                    generator.randomBattery(),
                    System.currentTimeMillis() / 1000,
                    generator.randomWeather());

            String json = gson.toJson(message);
            producer.sendData(String.valueOf(stationId), json);
            System.out.println(json);
            Thread.sleep(1000);
        }
    }
}