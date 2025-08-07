package org.hayden.ragloggingagent;

import org.hayden.ragloggingagent.clients.QdrantClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class RagLoggingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagLoggingAgentApplication.class, args);
    }

    @Bean
    public List<ToolCallback> qdrantClientTools(QdrantClient qdrantClient) {
        return List.of(ToolCallbacks.from(qdrantClient));
    }
}
