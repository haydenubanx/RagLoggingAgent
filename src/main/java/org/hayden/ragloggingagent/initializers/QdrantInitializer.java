package org.hayden.ragloggingagent.initializers;

import org.hayden.ragloggingagent.clients.QdrantClient;
import org.hayden.ragloggingagent.services.EmbeddingService;
import org.hayden.ragloggingagent.services.LogParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QdrantInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(QdrantInitializer.class);

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private LogParserService logParserService;

    @Value("${qdrant.collection.name}")
    private String collectionName;

    @Value("${qdrant.insert.log.messages:false}")
    private boolean insertLogMessages;

    @Value("${qdrant.processing.chunk.size:1000}")
    private int processingChunkSize;



    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            if (!qdrantClient.collectionExists(collectionName)) {
                LOGGER.info("Qdrant collection '{}' does not exist. Creating...", collectionName);
                qdrantClient.createCollection(collectionName);
                LOGGER.info("Qdrant collection '{}' created.", collectionName);
            } else {
                LOGGER.info("Qdrant collection '{}' already exists.", collectionName);
            }

            if (insertLogMessages) {
                List<String> allLogLines = logParserService.readAllLogLines();
                for (int i = 0; i < allLogLines.size(); i += processingChunkSize) {
                    List<String> chunk = allLogLines.subList(i, Math.min(i + processingChunkSize, allLogLines.size()));
                    embeddingService.processAndPublishLog(chunk, collectionName);
                }
                LOGGER.info("Qdrant Initializer completed. {} log messages inserted into collection '{}'.", allLogLines.size(), collectionName);
            } else {
                LOGGER.info("Log message insertion is disabled by configuration.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Qdrant collection: {}", e.getMessage());
        }
    }



}