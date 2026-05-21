package com.weather.station.model;

public class WeatherMessage {
    private long station_id;
    private long s_no;
    private String battery_status;
    private long status_timestamp;
    private WeatherData weather;

    public WeatherMessage(long station_id, long s_no, String battery_status,
            long status_timestamp, WeatherData weather) {
        this.station_id = station_id;
        this.s_no = s_no;
        this.battery_status = battery_status;
        this.status_timestamp = status_timestamp;
        this.weather = weather;
    }

    public void setStation_id(long station_id) {
        this.station_id = station_id;
    }

    public void setS_no(long s_no) {
        this.s_no = s_no;
    }

    public void setBattery_status(String battery_status) {
        this.battery_status = battery_status;
    }

    public void setStatus_timestamp(long status_timestamp) {
        this.status_timestamp = status_timestamp;
    }

    public void setWeather(WeatherData weather) {
        this.weather = weather;
    }

    public long getStation_id() {
        return station_id;
    }

    public long getS_no() {
        return s_no;
    }

    public String getBattery_status() {
        return battery_status;
    }

    public long getStatus_timestamp() {
        return status_timestamp;
    }

    public WeatherData getWeather() {
        return weather;
    }
}