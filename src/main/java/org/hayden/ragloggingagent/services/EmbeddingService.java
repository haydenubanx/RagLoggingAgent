package org.hayden.ragloggingagent.services;

import org.hayden.ragloggingagent.clients.OpenAIClient;
import org.hayden.ragloggingagent.clients.QdrantClient;
import org.hayden.ragloggingagent.initializers.QdrantInitializer;
import org.hayden.ragloggingagent.models.QdrantPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private LogParserService logParserService;

    public void processAndPublishLog(List<String> logLines, String collection) throws Exception {
        List<QdrantPoint> points = new ArrayList<>();
        List<String> validLogLines = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();

        for (String logLine : logLines) {
            Map<String, Object> metadata = logParserService.parseLogLine(logLine);
            if (metadata != null) {
                validLogLines.add(logLine);
                metadatas.add(metadata);
            }
        }
        if (validLogLines.isEmpty()) return;

        List<double[]> vectors = openAIClient.embedLogMessages(validLogLines);

        for (int i = 0; i < validLogLines.size(); i++) {
            String logLine = validLogLines.get(i);
            Map<String, Object> metadata = metadatas.get(i);

            // Generate unique hash-based ID
            String uniqueHash = sha256Hex(logLine);
            int id = hexToInt(uniqueHash);



            // Deduplication
            if (qdrantClient.pointExists(collection, id)) {
                LOGGER.info("Point already exists in Qdrant: {}", id);
                continue;
            } else {
                LOGGER.info("Adding QdrantPoint: id={}, logLine={}, metadata={}", id, logLine, metadata);
            }

            QdrantPoint point = new QdrantPoint();
            point.id = id;
            point.vector = vectors.get(i);
            point.payload = metadata;

            points.add(point);
        }

        if (!points.isEmpty()) {
            qdrantClient.insertPointsInBulk(collection, points);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating hash", e);
        }
    }

    private static int hexToInt(String hex) {
        return Math.abs(new BigInteger(hex, 16).intValue());
    }
}