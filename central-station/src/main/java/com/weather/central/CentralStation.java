package com.weather.central;

import org.example.BitcaskServer;

import com.weather.central.elasticsearch.ElasticsearchIndexer;

public class CentralStation {

    public static void main(String[] args) throws Exception {
        System.out.println("Central Station running!");

        // Start BitCask server in a separate thread
        Thread bitcaskThread = new Thread(() -> {
            try {
                BitcaskServer.main(new String[] { "8080", "./bitcask-data" });
            } catch (Exception e) {
                System.err.println("BitcaskServer failed: " + e.getMessage());
            }
        });

        // Start Kafka consumer in a separate thread
        Thread kafkaThread = new Thread(() -> {
            try {
                KafkaConsumers.main(new String[] {});
            } catch (Exception e) {
                System.err.println("KafkaConsumers failed: " + e.getMessage());
            }
        });

        bitcaskThread.start();
        kafkaThread.start();

        bitcaskThread.join();
        kafkaThread.join();
    }
}
