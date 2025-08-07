package org.hayden.ragloggingagent.clients;

import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class OpenAIClient {

    private final OpenAiService service;

    private static final int MAX_CALLS = 299000;
    private static final AtomicInteger callCounter = new AtomicInteger(0);
    private static final Logger LOGGER = Logger.getLogger(OpenAIClient.class.getName());
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public OpenAIClient(OpenAiService service) {
        this.service = service;
    }

    // Initialize the scheduler to reset the counter every minute
    static {
        scheduler.scheduleAtFixedRate(() -> {
            callCounter.set(0);
            LOGGER.info("API call counter reset.");
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Tool(name="embedMessages",
            description = "Embed any list of messages into vector representations using OpenAI's text-embedding-3-small model. Use this to prepare query or document vectors for searching or inserting into the vector database.")
    public List<double[]> embedLogMessages(List<String> messages) throws IOException, InterruptedException {

        EmbeddingResult response = null;

        if (callCounter.incrementAndGet() > MAX_CALLS) {
            LOGGER.warning("Max API call limit reached. Waiting for reset.");
            while (callCounter.get() > MAX_CALLS) {
                Thread.sleep(100);
            }
        }

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input(messages)
                .build();

        try {
             response = service.createEmbeddings(request);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating embeddings: ", e);
            throw e;
        }

        return response.getData().stream()
                .map(data -> data.getEmbedding().stream().mapToDouble(Double::doubleValue).toArray())
                .toList();
    }


}