package org.hayden.ragloggingagent.config;

import com.theokanning.openai.OpenAiService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiServiceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiServiceConfig.class);

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(openAiApiKey);
    }

    @PostConstruct
    public void init() {
        LOGGER.info("✅ Loaded OpenAI key: {}", (openAiApiKey != null && !openAiApiKey.isEmpty()) ? "Present" : "Missing");
    }
}