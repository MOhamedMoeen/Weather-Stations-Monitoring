package org.example;

import com.sun.net.httpserver.HttpServer;
import org.example.entity.Bitcask;
import org.example.handler.BitcaskHandler;
import org.example.handler.CompactHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;


public class BitcaskServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String dataDir = args.length > 1 ? args[1] : "./bitcask-data";

        Bitcask bitcask = new Bitcask(dataDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/keys", new BitcaskHandler(bitcask));
        server.createContext("/compact", new CompactHandler(bitcask));

        // Thread pool: handles concurrent --perf clients properly
        server.setExecutor(Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2
        ));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.stop(1);
            try { bitcask.close(); } catch (Exception ignored) {}
        }));

        server.start();
        System.out.printf("Bitcask server running on http://localhost:%d%n", port);
        System.out.printf("Data directory: %s%n", dataDir);
    }
}