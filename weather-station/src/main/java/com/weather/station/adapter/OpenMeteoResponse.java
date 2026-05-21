package com.weather.station.adapter;

import com.google.gson.annotations.SerializedName;

public class OpenMeteoResponse {

    private Current current;

    public Current getCurrent() {
        return current;
    }

    public static class Current {

        @SerializedName("temperature_2m")
        private double temperature2m;

        @SerializedName("relative_humidity_2m")
        private int relativeHumidity2m;

        @SerializedName("wind_speed_10m")
        private double windSpeed10m;

        public double getTemperature2m() {
            return temperature2m;
        }

        public int getRelativeHumidity2m() {
            return relativeHumidity2m;
        }

        public double getWindSpeed10m() {
            return windSpeed10m;
        }
    }
}