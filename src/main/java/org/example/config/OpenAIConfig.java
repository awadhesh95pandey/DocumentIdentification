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
        System.out.println("OpenAI Configuration Check:");
        System.out.println("- Classification Enabled: " + classificationEnabled);
        System.out.println("- API Key Present: " + (apiKey != null && !apiKey.trim().isEmpty()));
        System.out.println("- API Key Length: " + (apiKey != null ? apiKey.length() : 0));
        System.out.println("- API Key Starts with 'sk-': " + (apiKey != null && apiKey.startsWith("sk-")));
        
        if (!classificationEnabled) {
            System.out.println("OpenAI Classification is disabled in configuration");
            return null;
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("OpenAI API key is null or empty");
            return null;
        }
        
        if (apiKey.equals("your-openai-api-key-here") || apiKey.equals("your-actual-openai-api-key-here")) {
            System.out.println("OpenAI API key is still set to placeholder value");
            return null;
        }
        
        if (!apiKey.startsWith("sk-")) {
            System.out.println("OpenAI API key does not start with 'sk-' - invalid format");
            return null;
        }
        
        try {
            System.out.println("Creating OpenAI service with timeout: " + timeoutSeconds + " seconds");
            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
            System.out.println("OpenAI service created successfully!");
            return service;
        } catch (Exception e) {
            System.out.println("Failed to create OpenAI service: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
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
