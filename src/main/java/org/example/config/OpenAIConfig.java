package org.example.config;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration class for OpenAI integration.
 * Handles API key management and service initialization.
 */
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.timeout:60}")
    private int timeoutSeconds;

    @Value("${openai.api.model:gpt-4-vision-preview}")
    private String model;

    @Value("${openai.api.max-tokens:1000}")
    private int maxTokens;

    @Value("${openai.classification.enabled:true}")
    private boolean classificationEnabled;

    /**
     * Creates OpenAI service bean with configured timeout.
     */
    @Bean
    public OpenAiService openAiService() {
        if (!classificationEnabled || apiKey == null || apiKey.equals("your-openai-api-key-here")) {
            return null; // Classification will be disabled
        }
        
        return new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
    }

    // Getters and Setters
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isClassificationEnabled() {
        return classificationEnabled;
    }

    public void setClassificationEnabled(boolean classificationEnabled) {
        this.classificationEnabled = classificationEnabled;
    }
}
