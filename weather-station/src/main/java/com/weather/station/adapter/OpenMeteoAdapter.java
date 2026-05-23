package com.weather.station.adapter;

import com.google.gson.Gson;
import com.weather.station.model.WeatherData;
import com.weather.station.model.WeatherMessage;
import org.apache.kafka.clients.producer.Producer;
import com.weather.station.WeatherStationProducer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMeteoAdapter {

    // Alexandria coordinates
    private static final String API_URL =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=31.2&longitude=29.9" +
                    "&current=temperature_2m,relative_humidity_2m,wind_speed_10m";

    private static final long STATION_ID = 0L; // external source, not a mock station

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private long sNo = 0;

    public void run() throws Exception {
        System.out.println("Open-Meteo Adapter started (Station ID: " + STATION_ID + ")");

        while (true) {
            sNo++;
            try {
                // 1. Call the Open-Meteo API
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                // 2. Parse the response
                OpenMeteoResponse meteoResponse = gson.fromJson(
                        response.body(), OpenMeteoResponse.class);

                OpenMeteoResponse.Current current = meteoResponse.getCurrent();

                // 3. Map to WeatherMessage schema
                int humidity    = current.getRelativeHumidity2m();
                int temperature = celsiusToFahrenheit(current.getTemperature2m());
                int windSpeed   = (int) Math.round(current.getWindSpeed10m());

                WeatherData weather = new WeatherData(humidity, temperature, windSpeed);

                WeatherMessage message = new WeatherMessage(
                        STATION_ID,
                        sNo,
                        "high", // real station — battery status not applicable
                        System.currentTimeMillis() / 1000,
                        weather
                );
                WeatherStationProducer producer = new WeatherStationProducer(
                        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092"),
                        "weather_status"
                );
                String json = gson.toJson(message);
                producer.sendData(String.valueOf(STATION_ID), json);
                System.out.println(json);

            } catch (Exception e) {
                System.err.println("[OpenMeteoAdapter] Error fetching data: " + e.getMessage());
            }

            Thread.sleep(1000);
        }
    }

    private int celsiusToFahrenheit(double celsius) {
        return (int) Math.round((celsius * 9.0 / 5.0) + 32);
    }
}