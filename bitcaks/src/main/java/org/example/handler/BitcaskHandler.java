package org.example.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.entity.Bitcask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public class BitcaskHandler implements HttpHandler {

    private final Bitcask bitcask;

    public BitcaskHandler(Bitcask bitcask) {
        this.bitcask = bitcask;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        URI uri = exchange.getRequestURI();
        String path = uri.getPath().replaceFirst("^/keys", "").replaceAll("^/+|/+$", "");
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if (path.isEmpty()) {
                if ("GET".equals(method)) {
                    handleGetAll(exchange);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } else {
                String key = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
                switch (method) {
                    case "GET"    -> handleGet(exchange, key);
                    case "PUT"    -> handlePut(exchange, key);
                    default       -> sendError(exchange, 405, "Method not allowed");
                }
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }


    private void handleGetAll(HttpExchange exchange) throws Exception {
        Map<String, String> all = bitcask.getAll();
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (!first) sb.append(",");
            sb.append(jsonString(e.getKey()))
                    .append(":")
                    .append(jsonString(e.getValue()));
            first = false;
        }
        sb.append("}");
        send(exchange, 200, "application/json", sb.toString());
    }


    private void handleGet(HttpExchange exchange, String key) throws Exception {
        String value = bitcask.get(key);
        if (value == null) {
            sendError(exchange, 404, "Key not found: " + key);
            return;
        }
        send(exchange, 200, "text/plain", value);
    }

    private void handlePut(HttpExchange exchange, String key) throws Exception {
        String value;
        try (InputStream in = exchange.getRequestBody()) {
            value = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        bitcask.put(key, value);
        send(exchange, 204, "text/plain", "");
    }



    private void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        String body = "{\"error\":" + jsonString(message) + "}";
        send(ex, status, "application/json", body);
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }
}