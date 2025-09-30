package com.documentclassifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Service for integrating with Hugging Face VaultGemma API
 * Provides privacy-preserving document classification using online API calls
 */
@Service
public class HuggingFaceVaultGemmaService {
    
    private static final Logger log = LoggerFactory.getLogger(HuggingFaceVaultGemmaService.class);
    
    @Value("${vaultgemma.huggingface.api-url}")
    private String apiUrl;
    
    @Value("${vaultgemma.huggingface.token}")
    private String apiToken;
    
    @Value("${vaultgemma.huggingface.timeout:30000}")
    private int timeout;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public HuggingFaceVaultGemmaService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
        this.objectMapper = objectMapper;
    }
    
    /**
     * Classify document using VaultGemma with differential privacy
     */
    public String classifyDocumentWithPrivacy(String extractedText, String userId) {
        try {
            log.debug("Classifying document with VaultGemma for user: {}", userId);
            
            // Create privacy-preserving prompt
            String prompt = createClassificationPrompt(extractedText);
            
            // Call Hugging Face API
            String response = callHuggingFaceAPI(prompt);
            
            // Extract classification from response
            String classification = extractClassification(response);
            
            log.info("VaultGemma classification successful: {} for user: {}", classification, userId);
            return classification;
            
        } catch (Exception e) {
            log.error("Error calling VaultGemma API for user {}: {}", userId, e.getMessage());
            // Fallback to rule-based classification
            return fallbackClassification(extractedText);
        }
    }
    
    /**
     * Create a privacy-preserving classification prompt
     */
    private String createClassificationPrompt(String text) {
        // Limit text length to prevent token overflow and protect privacy
        String limitedText = text.substring(0, Math.min(text.length(), 500));
        
        return String.format(
            "Classify this Indian document text into one of these categories: PAN, Aadhaar, Voter ID, Passport, Driving License, or Other.\n\n" +
            "Text: %s\n\n" +
            "Classification:",
            limitedText
        );
    }
    
    /**
     * Call Hugging Face Inference API
     */
    private String callHuggingFaceAPI(String prompt) {
        Map<String, Object> requestBody = Map.of(
            "inputs", prompt,
            "parameters", Map.of(
                "max_new_tokens", 10,
                "temperature", 0.1,
                "do_sample", false,
                "return_full_text", false
            )
        );
        
        try {
            return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .block();
        } catch (WebClientResponseException e) {
            log.error("Hugging Face API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("VaultGemma API call failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract classification from VaultGemma response
     */
    private String extractClassification(String response) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(response);
            
            if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                String generatedText = jsonResponse.get(0).get("generated_text").asText();
                
                // Extract classification from generated text
                String[] lines = generatedText.split("\n");
                for (String line : lines) {
                    if (line.toLowerCase().contains("classification:")) {
                        String classification = line.substring(line.indexOf(":") + 1).trim();
                        return normalizeClassification(classification);
                    }
                }
                
                // If no explicit classification line, analyze the full generated text
                return normalizeClassification(generatedText);
            }
            
            // Fallback: analyze the full response
            return analyzeResponseForClassification(response);
            
        } catch (Exception e) {
            log.warn("Error parsing VaultGemma response: {}", e.getMessage());
            return analyzeResponseForClassification(response);
        }
    }
    
    /**
     * Normalize classification to standard format
     */
    private String normalizeClassification(String classification) {
        String normalized = classification.toLowerCase().trim();
        
        if (normalized.contains("pan")) return "PAN";
        if (normalized.contains("aadhaar") || normalized.contains("aadhar")) return "Aadhaar";
        if (normalized.contains("voter")) return "Voter ID";
        if (normalized.contains("passport")) return "Passport";
        if (normalized.contains("driving") || normalized.contains("license")) return "Driving License";
        
        return "Other";
    }
    
    /**
     * Analyze response text for classification keywords
     */
    private String analyzeResponseForClassification(String response) {
        String lowerResponse = response.toLowerCase();
        
        if (lowerResponse.contains("pan")) return "PAN";
        if (lowerResponse.contains("aadhaar") || lowerResponse.contains("aadhar")) return "Aadhaar";
        if (lowerResponse.contains("voter")) return "Voter ID";
        if (lowerResponse.contains("passport")) return "Passport";
        if (lowerResponse.contains("driving") || lowerResponse.contains("license")) return "Driving License";
        
        return "Other";
    }
    
    /**
     * Fallback classification using rule-based approach
     */
    private String fallbackClassification(String text) {
        log.info("Using fallback classification due to VaultGemma API unavailability");
        
        String lowerText = text.toLowerCase();
        
        if (lowerText.contains("permanent account number") || lowerText.contains("income tax")) {
            return "PAN";
        }
        if (lowerText.contains("aadhaar") || lowerText.contains("unique identification")) {
            return "Aadhaar";
        }
        if (lowerText.contains("election commission") || lowerText.contains("voter")) {
            return "Voter ID";
        }
        if (lowerText.contains("passport") || lowerText.contains("republic of india")) {
            return "Passport";
        }
        if (lowerText.contains("driving licence") || lowerText.contains("transport")) {
            return "Driving License";
        }
        
        return "Other";
    }
    
    /**
     * Check if VaultGemma service is available
     */
    public boolean isServiceAvailable() {
        try {
            // Test API connectivity with a simple prompt
            String testResponse = callHuggingFaceAPI("Test classification");
            return testResponse != null && !testResponse.contains("error");
        } catch (Exception e) {
            log.warn("VaultGemma service not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get service status information
     */
    public Map<String, Object> getServiceStatus() {
        boolean available = isServiceAvailable();
        return Map.of(
            "service", "HuggingFace VaultGemma",
            "available", available,
            "apiUrl", apiUrl,
            "hasToken", apiToken != null && !apiToken.equals("your_token_here"),
            "timeout", timeout
        );
    }
}
