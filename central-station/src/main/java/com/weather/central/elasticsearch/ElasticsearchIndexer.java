package com.weather.central.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.weather.central.archive.ParquetHandler;
import com.weather.central.model.WeatherStatus;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ElasticsearchIndexer {

    private RestClient restClient;
    private ElasticsearchClient esClient;
    private ObjectMapper mapper;

    public ElasticsearchIndexer() {
        this.mapper = new ObjectMapper();

        String esHost = System.getenv().getOrDefault("ES_HOST", "localhost");
        int esPort = Integer.parseInt(System.getenv().getOrDefault("ES_PORT", "9200"));
        this.restClient = RestClient
                .builder(new HttpHost(esHost, esPort, "http"))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                this.restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(transport);
        createIndex();
    }

    private void createIndex() {
        try {
            boolean exists = esClient.indices()
                    .exists(e -> e.index("weather-statuses"))
                    .value();

            if (!exists) {
                esClient.indices().create(c -> c
                        .index("weather-statuses")
                        .mappings(m -> m
                                .properties("station_id", p -> p.long_(l -> l))
                                .properties("s_no", p -> p.long_(l -> l))
                                .properties("battery_status", p -> p.keyword(k -> k))
                                .properties("status_timestamp", p -> p.long_(l -> l))
                                .properties("humidity", p -> p.integer(i -> i))
                                .properties("temperature", p -> p.integer(i -> i))
                                .properties("wind_speed", p -> p.integer(i -> i))));
            }
        } catch (Exception e) {
            System.err.println("Failed to create index: " + e.getMessage());
        }
    }

    public void indexParquetFile(String filePath, ParquetHandler parquetHandler) {

        try {
            List<WeatherStatus> records = parquetHandler.readParquet(filePath);

            if (records.isEmpty()) {
                System.out.println("File is empty, skipping indexing.");
                return;
            }

            BulkRequest.Builder br = new BulkRequest.Builder();
            for (WeatherStatus status : records) {
                br.operations(op -> op
                        .index(idx -> idx
                                .index("weather-statuses")
                                .document(status)));
            }

            BulkResponse result = esClient.bulk(br.build());
            if (result.errors()) {
                List<BulkResponseItem> items = result.items();
                for (int i = 0; i < items.size(); i++) {
                    BulkResponseItem item = items.get(i);
                    if (item.error() != null) {
                        WeatherStatus failedRecord = records.get(i);
                        String errorReason = item.error().reason();
                        String logEntry = String.format("{\"error\": \"%s\", \"record\": %s}\n",
                                errorReason, mapper.writeValueAsString(failedRecord));
                        Files.write(Paths.get("es-invalid-messages.log"),
                                logEntry.getBytes(),
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                }
            } else {
                System.out.println("Successfully indexed records from " + filePath);
            }

        } catch (Exception e) {
            System.err.println("Failed to index Parquet file: " + filePath);
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (restClient != null) {
                restClient.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
