package org.hayden.ragloggingagent.clients;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.hayden.ragloggingagent.models.QdrantPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QdrantClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(QdrantClient.class);

    @Autowired
    private HttpClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${qdrant.url}")
    private String qdrantUrl;

    @Value("${qdrant.collection.name}")
    private String collectionName;

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 500;

    public void createCollection(String collectionName) throws IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName;
        String bodyJson = """
                {
                  "vectors": {
                    "size": 1536,
                    "distance": "Cosine"
                  },
                  "hnsw_config": {
                    "m": 16,
                    "ef_construct": 100
                  },
                  "quantization_config": {
                    "scalar": {
                      "type": "int8",
                      "always_ram": true
                    }
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void insertPoint(String collection, QdrantPoint point) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("points", List.of(point)));
        int retries = 0;
        long backoff = INITIAL_BACKOFF_MS;


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(qdrantUrl + "/collections/" + collection + "/points?wait=true"))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        while (retries < MAX_RETRIES) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                } else if (response.statusCode() == 429 || response.statusCode() == 503) {
                    LOGGER.warn("Rate limit hit or service unavailable. Retrying...");
                } else {
                    throw new IOException("Request failed with status: " + response.statusCode() + " - " + response.body());
                }
            } catch (Exception e) {
                LOGGER.error("Error inserting point into Qdrant: {}", e.getMessage());
            }

            // Wait before retrying
            Thread.sleep(backoff);
            backoff *= 2;
            retries++;
        }
        LOGGER.error("Max Retries Reached without success");

    }

    public void insertPointsInBulk(String collection, List<QdrantPoint> points) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("points", points));
        int retries = 0;
        long backoff = INITIAL_BACKOFF_MS;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(qdrantUrl + "/collections/" + collection + "/points?wait=true"))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        while (retries < MAX_RETRIES) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                } else if (response.statusCode() == 429 || response.statusCode() == 503) {
                    LOGGER.warn("Rate limit hit or service unavailable. Retrying...");
                } else {
                    throw new IOException("Request failed with status: " + response.statusCode() + " - " + response.body());
                }
            } catch (Exception e) {
                LOGGER.error("Error inserting points in bulk: {}", e.getMessage());
            }

            // Wait before retrying
            Thread.sleep(backoff);
            backoff *= 2;
            retries++;

        }
    }

    public void updatePoint(String collection, QdrantPoint point) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("points", List.of(point)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(qdrantUrl + "/collections/" + collection + "/points"))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Failed to update point: " + response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Error inserting point into Qdrant: {}", e.getMessage());
        }

    }


    public boolean collectionExists(String collectionName) throws IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            LOGGER.error("Error checking collection existence: {}", e.getMessage());
        }

        return false;

    }


    public boolean pointExists(String collectionName, int pointId) throws IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName + "/points/" + pointId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            LOGGER.error("Error checking point existence: {}", e.getMessage());
        }

        return false;
    }


    @Tool(
            name = "Qdrant_Vector_Similarity_Search",
            description = "Search for the most similar vectors in a Qdrant collection. Provide the collection name, a query vector, and the number of similar results to return (limit). Returns the closest points with their payloads."
    )
    public HttpResponse<String> search( double[] vector, int limit) throws
            IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName + "/points/search";

        // Build the JSON payload
        String vectorJson = objectMapper.writeValueAsString(vector);
        String bodyJson = """
                {
                  "vector": %s,
                  "limit": %d,
                   "with_payload": true,
                   "params": {
                     "ef": 64
                   }
                }
                """.formatted(vectorJson, limit);

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Execute the request
        return response;
    }

    public HttpResponse<String> getPoints( List<Integer> pointIds) throws
            IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName + "/points";

        // Build the JSON payload
        String bodyJson = objectMapper.writeValueAsString(Map.of(
                "ids", pointIds,
                "with_payload", true
        ));
        // Create the HTTP POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        } catch (Exception e) {
            LOGGER.error("Error fetching points: {}", e.getMessage());
        }
        return null;
    }


    @Tool(
            name = "Qdrant_Get_All_Points",
            description = "Recursively fetch all points from a Qdrant collection. Provide the collection name and an initial offset (usually 0). Returns a map of point IDs to their payloads."
    )
    public Map<Integer, String> getAllPointsRecursively(int initialOffset) throws IOException, InterruptedException {
        Map<Integer, String> allPoints = new ConcurrentHashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        AtomicInteger activeTasks = new AtomicInteger(0);
        CompletableFuture<Void> done = new CompletableFuture<>();

        // Start recursive fetching
        fetchChunkAsync( initialOffset, allPoints, executorService, activeTasks, done);

        // Wait until all recursive fetches are complete
        done.join();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.HOURS);

        return allPoints;
    }

    private void fetchChunkAsync(int offset, Map<Integer, String> allPoints,
                                 ExecutorService executorService, AtomicInteger activeTasks, CompletableFuture<Void> done) {

        // Increment task count
        activeTasks.incrementAndGet();

        executorService.submit(() -> {
            try {
                String url = qdrantUrl + "/collections/" + collectionName + "/points/scroll";
                Map<String, Object> payload = new HashMap<>();
                payload.put("with_payload", true);
                payload.put("with_vector", false);
                payload.put("limit", 10000);
                if (offset != 0) {
                    payload.put("offset", offset);
                }

                String bodyJson = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .header("Content-Type", "application/json")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode responseBody = objectMapper.readTree(response.body());
                    JsonNode points = responseBody.path("result").path("points");
                    JsonNode nextPageOffset = responseBody.path("result").path("next_page_offset");

                    for (JsonNode point : points) {
                        int id = point.path("id").asInt();
                        String word = point.path("payload").path("word").asText();
                        allPoints.put(id, word);
                    }

                    if (!nextPageOffset.isNull()) {
                        int nextOffset = nextPageOffset.asInt();
                        fetchChunkAsync( nextOffset, allPoints, executorService, activeTasks, done);
                    }
                } else {
                    LOGGER.error("Failed to fetch points at offset {}: {}", offset, response.body());
                }

            } catch (Exception e) {
                LOGGER.error("Error fetching points at offset {}: {}", offset, e.getMessage());
            } finally {
                // Decrement task count
                int remaining = activeTasks.decrementAndGet();
                if (remaining == 0) {
                    done.complete(null); // All tasks done
                }
            }
        });
    }

    @Tool(
            name = "Qdrant_Metadata_Filtered_Search",
            description = "Search Qdrant for log entries filtered by any metadata: timestamp range, status code, IP, request type, endpoint, size, referer, user agent, or response time. Provide the collection name, any combination of filters, and a result limit. Use null for any filters you don't want to apply."
    )
    public HttpResponse<String> searchByMetadata(
            String startTimestamp,
            String endTimestamp,
            String statusCode,
            String ip,
            String requestType,
            String endpoint,
            String size,
            String referer,
            String userAgent,
            String responseTime,
            int limit
    ) throws IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName + "/points/search";

        Map<String, Object> filter = new HashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();

        if (startTimestamp != null && endTimestamp != null) {
            must.add(Map.of("key", "timestamp", "range", Map.of("gte", startTimestamp, "lte", endTimestamp)));
        }
        if (statusCode != null) {
            must.add(Map.of("key", "status", "match", Map.of("value", statusCode)));
        }
        if (ip != null) {
            must.add(Map.of("key", "ip", "match", Map.of("value", ip)));
        }
        if (requestType != null) {
            must.add(Map.of("key", "request_type", "match", Map.of("value", requestType)));
        }
        if (endpoint != null) {
            must.add(Map.of("key", "endpoint", "match", Map.of("value", endpoint)));
        }
        if (size != null) {
            must.add(Map.of("key", "size", "match", Map.of("value", size)));
        }
        if (referer != null) {
            must.add(Map.of("key", "referer", "match", Map.of("value", referer)));
        }
        if (userAgent != null) {
            must.add(Map.of("key", "user_agent", "match", Map.of("value", userAgent)));
        }
        if (responseTime != null) {
            must.add(Map.of("key", "response_time", "match", Map.of("value", responseTime)));
        }
        if (!must.isEmpty()) {
            filter.put("must", must);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);
        body.put("limit", limit);
        body.put("with_payload", true);

        String bodyJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Tool(
            name = "Qdrant_Count_Logs_by_Filter",
            description = "Count the number of log entries in a Qdrant collection matching any combination of metadata filters. Provide the collection name, any filters (timestamp range, status code, IP, request type, endpoint, size, referer, user agent, response time), and use null for filters you don't want to apply."
    )
    public int countLogsByFilter(
            String startTimestamp,
            String endTimestamp,
            String statusCode,
            String ip,
            String requestType,
            String endpoint,
            String size,
            String referer,
            String userAgent,
            String responseTime
    ) throws IOException, InterruptedException {
        String url = qdrantUrl + "/collections/" + collectionName + "/points/count";
        Map<String, Object> filter = new HashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();

        if (startTimestamp != null && endTimestamp != null) {
            must.add(Map.of("key", "timestamp", "range", Map.of("gte", startTimestamp, "lte", endTimestamp)));
        }
        if (statusCode != null) must.add(Map.of("key", "status", "match", Map.of("value", statusCode)));
        if (ip != null) must.add(Map.of("key", "ip", "match", Map.of("value", ip)));
        if (requestType != null) must.add(Map.of("key", "request_type", "match", Map.of("value", requestType)));
        if (endpoint != null) must.add(Map.of("key", "endpoint", "match", Map.of("value", endpoint)));
        if (size != null) must.add(Map.of("key", "size", "match", Map.of("value", size)));
        if (referer != null) must.add(Map.of("key", "referer", "match", Map.of("value", referer)));
        if (userAgent != null) must.add(Map.of("key", "user_agent", "match", Map.of("value", userAgent)));
        if (responseTime != null) must.add(Map.of("key", "response_time", "match", Map.of("value", responseTime)));
        if (!must.isEmpty()) filter.put("must", must);

        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);

        String bodyJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        return json.path("result").path("count").asInt();
    }

    @Tool(
            name = "Qdrant_Aggregate_Logs",
            description = "Aggregate logs in a Qdrant collection by a metadata field (e.g., status code, endpoint). Returns a map of field values to their counts. Provide the collection name, the field to aggregate by, and optional filters (use null for filters you don't want to apply)."
    )
    public Map<String, Integer> aggregateLogs(
            String aggregateField,
            String startTimestamp,
            String endTimestamp,
            String statusCode,
            String ip,
            String requestType,
            String endpoint,
            String size,
            String referer,
            String userAgent,
            String responseTime
    ) throws IOException, InterruptedException {
        // Qdrant does not support server-side aggregation, so fetch and aggregate client-side
        List<Map<String, Object>> allLogs = new ArrayList<>();
        int offset = 0, limit = 10000;
        boolean hasMore = true;

        while (hasMore) {
            String url = qdrantUrl + "/collections/" + collectionName + "/points/scroll";
            Map<String, Object> filter = new HashMap<>();
            List<Map<String, Object>> must = new ArrayList<>();
            if (startTimestamp != null && endTimestamp != null) must.add(Map.of("key", "timestamp", "range", Map.of("gte", startTimestamp, "lte", endTimestamp)));
            if (statusCode != null) must.add(Map.of("key", "status", "match", Map.of("value", statusCode)));
            if (ip != null) must.add(Map.of("key", "ip", "match", Map.of("value", ip)));
            if (requestType != null) must.add(Map.of("key", "request_type", "match", Map.of("value", requestType)));
            if (endpoint != null) must.add(Map.of("key", "endpoint", "match", Map.of("value", endpoint)));
            if (size != null) must.add(Map.of("key", "size", "match", Map.of("value", size)));
            if (referer != null) must.add(Map.of("key", "referer", "match", Map.of("value", referer)));
            if (userAgent != null) must.add(Map.of("key", "user_agent", "match", Map.of("value", userAgent)));
            if (responseTime != null) must.add(Map.of("key", "response_time", "match", Map.of("value", responseTime)));
            if (!must.isEmpty()) filter.put("must", must);

            Map<String, Object> body = new HashMap<>();
            body.put("with_payload", true);
            body.put("with_vector", false);
            body.put("limit", limit);
            if (!filter.isEmpty()) body.put("filter", filter);
            if (offset != 0) body.put("offset", offset);

            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode points = json.path("result").path("points");
            for (JsonNode point : points) {
                JsonNode payload = point.path("payload");
                if (payload.has(aggregateField)) {
                    allLogs.add(objectMapper.convertValue(payload, Map.class));
                }
            }
            JsonNode nextPageOffset = json.path("result").path("next_page_offset");
            if (!nextPageOffset.isNull()) {
                offset = nextPageOffset.asInt();
            } else {
                hasMore = false;
            }
        }

        Map<String, Integer> aggregation = new HashMap<>();
        for (Map<String, Object> log : allLogs) {
            Object value = log.get(aggregateField);
            if (value != null) {
                aggregation.merge(value.toString(), 1, Integer::sum);
            }
        }
        return aggregation;
    }

    @Tool(
            name = "Qdrant_Get_Distinct_Metadata_Values",
            description = "Get all unique values for a given metadata field in a Qdrant collection. Provide the collection name and the metadata field name."
    )
    public Set<String> getDistinctMetadataValues(
            String fieldName
    ) throws IOException, InterruptedException {
        Set<String> values = new HashSet<>();
        int offset = 0, limit = 10000;
        boolean hasMore = true;

        while (hasMore) {
            String url = qdrantUrl + "/collections/" + collectionName + "/points/scroll";
            Map<String, Object> body = new HashMap<>();
            body.put("with_payload", true);
            body.put("with_vector", false);
            body.put("limit", limit);
            if (offset != 0) body.put("offset", offset);

            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode points = json.path("result").path("points");
            for (JsonNode point : points) {
                JsonNode payload = point.path("payload");
                if (payload.has(fieldName)) {
                    values.add(payload.get(fieldName).asText());
                }
            }
            JsonNode nextPageOffset = json.path("result").path("next_page_offset");
            if (!nextPageOffset.isNull()) {
                offset = nextPageOffset.asInt();
            } else {
                hasMore = false;
            }
        }
        return values;
    }

    @Tool(
            name = "Qdrant_Visualize_Log_Metadata",
            description = "Create a visualization (line or bar chart) of log metadata values. Provide the collection name, chart type (line or bar), xField (e.g., timestamp), yField (e.g., count or status), and optional filters. Returns a base64-encoded PNG image."
    )
    public String visualizeLogMetadata(
            String chartType, // "line" or "bar"
            String xField,    // e.g., "timestamp"
            String yField,    // e.g., "count" or "status"
            String startTimestamp,
            String endTimestamp,
            String statusCode,
            String ip,
            String requestType,
            String endpoint,
            String size,
            String referer,
            String userAgent,
            String responseTime
    ) throws Exception {
        // Aggregate data
        Map<String, Integer> data = aggregateLogs(
                 yField, startTimestamp, endTimestamp, statusCode, ip, requestType, endpoint, size, referer, userAgent, responseTime
        );

        // Create dataset
        org.jfree.data.category.DefaultCategoryDataset dataset = new org.jfree.data.category.DefaultCategoryDataset();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            dataset.addValue(entry.getValue(), yField, entry.getKey());
        }

        // Create chart
        org.jfree.chart.JFreeChart chart;
        if ("line".equalsIgnoreCase(chartType)) {
            chart = org.jfree.chart.ChartFactory.createLineChart(
                    yField + " over " + xField, xField, yField, dataset
            );
        } else {
            chart = org.jfree.chart.ChartFactory.createBarChart(
                    yField + " by " + xField, xField, yField, dataset
            );
        }

        // Render chart to image
        java.awt.image.BufferedImage image = chart.createBufferedImage(800, 400);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", baos);
        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }


}
