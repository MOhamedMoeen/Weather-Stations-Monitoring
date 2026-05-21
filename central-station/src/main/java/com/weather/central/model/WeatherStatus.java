package com.weather.central.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// annotations that generates all getters, setters and constructor 
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherStatus {
    private long station_id;
    private long s_no;
    private String battery_status;
    private long status_timestamp;
    private int humidity;
    private int temperature;
    private int wind_speed;
}