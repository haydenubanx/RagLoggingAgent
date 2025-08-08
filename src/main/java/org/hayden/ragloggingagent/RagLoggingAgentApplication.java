package org.hayden.ragloggingagent;

import org.hayden.ragloggingagent.clients.QdrantClient;
import org.hayden.ragloggingagent.utils.DateFormatUtil;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@SpringBootApplication
public class RagLoggingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagLoggingAgentApplication.class, args);
    }

    @Bean
    public List<ToolCallback> mcpTools(QdrantClient qdrantClient, DateFormatUtil dateFormatter) {
        return Stream.of(
                        ToolCallbacks.from(qdrantClient),
                        ToolCallbacks.from(dateFormatter)
                )
                .flatMap(Arrays::stream)
                .toList();
    }
}
