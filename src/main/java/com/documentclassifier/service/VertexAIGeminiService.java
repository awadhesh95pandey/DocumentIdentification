package com.documentclassifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for integrating with Vertex AI Gemini-2.0-flash for document classification
 * Uses VaultGemma-1b to protect sensitive data before sending to Gemini
 */
@Service
public class VertexAIGeminiService {
    
    private static final Logger logger = LoggerFactory.getLogger(VertexAIGeminiService.class);
    
    @Value("${vertex.ai.project.id:}")
    private String projectId;
    
    @Value("${vertex.ai.location:us-central1}")
    private String location;
    
    @Value("${vertex.ai.model:gemini-2.0-flash}")
    private String modelName;
    
    @Value("${vertex.ai.enabled:false}")
    private boolean vertexAIEnabled;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GoogleVaultGemmaService vaultGemmaService;
    
    public VertexAIGeminiService(RestTemplate restTemplate, 
                                ObjectMapper objectMapper,
                                GoogleVaultGemmaService vaultGemmaService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.vaultGemmaService = vaultGemmaService;
    }
    
    /**
     * Classify document using Vertex AI Gemini with VaultGemma protection
     */
    public String classifyDocumentSecurely(String extractedText, String userId) {
        try {
            if (!vertexAIEnabled || projectId.isEmpty()) {
                logger.warn("Vertex AI not configured, falling back to pattern-based classification");
                return fallbackToPatternClassification(extractedText);
            }
            
            logger.info("Starting secure document classification for user: {}", userId);
            
            // Step 1: Protect sensitive data using VaultGemma-1b
            String protectedText = vaultGemmaService.protectSensitiveData(extractedText, userId);
            logger.debug("Applied VaultGemma protection, text length: {} -> {}", 
                        extractedText.length(), protectedText.length());
            
            // Step 2: Send protected text to Gemini for classification
            String classification = classifyWithGemini(protectedText);
            
            logger.info("Document classified as: {} for user: {}", classification, userId);
            return classification;
            
        } catch (Exception e) {
            logger.error("Vertex AI classification failed for user {}: {}", userId, e.getMessage());
            return fallbackToPatternClassification(extractedText);
        }
    }
    
    /**
     * Send protected text to Vertex AI Gemini for classification
     */
    private String classifyWithGemini(String protectedText) throws Exception {
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
            location, projectId, location, modelName
        );
        
        // Create the request payload for Gemini
        Map<String, Object> request = createGeminiRequest(protectedText);
        
        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        // Make the API call
        ResponseEntity<String> response = restTemplate.exchange(
            endpoint, HttpMethod.POST, entity, String.class
        );
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return parseGeminiResponse(response.getBody());
        } else {
            throw new RuntimeException("Vertex AI API call failed with status: " + response.getStatusCode());
        }
    }
    
    /**
     * Create request payload for Gemini API
     */
    private Map<String, Object> createGeminiRequest(String protectedText) {
        Map<String, Object> request = new HashMap<>();
        
        // Create the prompt for document classification
        String prompt = createClassificationPrompt(protectedText);
        
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        
        request.put("contents", List.of(content));
        
        // Generation configuration
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("topP", 0.8);
        generationConfig.put("topK", 10);
        generationConfig.put("maxOutputTokens", 100);
        
        request.put("generationConfig", generationConfig);
        
        // Safety settings
        Map<String, Object> safetySettings = new HashMap<>();
        safetySettings.put("category", "HARM_CATEGORY_DANGEROUS_CONTENT");
        safetySettings.put("threshold", "BLOCK_MEDIUM_AND_ABOVE");
        
        request.put("safetySettings", List.of(safetySettings));
        
        return request;
    }
    
    /**
     * Create classification prompt for Gemini
     */
    private String createClassificationPrompt(String protectedText) {
        return String.format("""
            You are an expert document classifier for Indian government documents.
            
            Analyze the following protected document text and classify it into one of these categories:
            - PAN: Permanent Account Number card from Income Tax Department
            - AADHAAR: Aadhaar card from UIDAI with biometric information
            - PASSPORT: Indian passport from Ministry of External Affairs
            - DRIVING_LICENSE: Driving license from Transport Department/RTO
            - VOTER_ID: Voter ID card from Election Commission of India
            - UNKNOWN: If the document type cannot be determined
            
            Document text (sensitive data protected):
            %s
            
            Instructions:
            1. Look for government department names, document titles, and official phrases
            2. Consider both Hindi and English text patterns
            3. The text may have some sensitive information protected/masked
            4. Return ONLY the classification category name (e.g., "PAN", "AADHAAR", etc.)
            5. Do not include any explanation or additional text
            
            Classification:
            """, protectedText);
    }
    
    /**
     * Parse Gemini API response to extract classification
     */
    private String parseGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if (root.has("candidates") && root.get("candidates").isArray() && root.get("candidates").size() > 0) {
            JsonNode candidate = root.get("candidates").get(0);
            if (candidate.has("content") && candidate.get("content").has("parts")) {
                JsonNode parts = candidate.get("content").get("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).get("text").asText().trim().toUpperCase();
                    
                    // Validate the classification result
                    String[] validClassifications = {"PAN", "AADHAAR", "PASSPORT", "DRIVING_LICENSE", "VOTER_ID", "UNKNOWN"};
                    for (String valid : validClassifications) {
                        if (text.contains(valid)) {
                            return valid;
                        }
                    }
                }
            }
        }
        
        logger.warn("Could not parse Gemini response, falling back to UNKNOWN");
        return "UNKNOWN";
    }
    
    /**
     * Get access token for Vertex AI (placeholder - implement based on your auth method)
     */
    private String getAccessToken() {
        // TODO: Implement proper authentication
        // This could be:
        // 1. Service Account Key authentication
        // 2. Application Default Credentials
        // 3. OAuth 2.0 flow
        // For now, return placeholder
        return "YOUR_ACCESS_TOKEN";
    }
    
    /**
     * Fallback to pattern-based classification when Vertex AI is unavailable
     */
    private String fallbackToPatternClassification(String text) {
        logger.info("Using pattern-based fallback classification");
        return vaultGemmaService.classifyWithPatterns(text);
    }
    
    /**
     * Check if Vertex AI service is available and configured
     */
    public boolean isAvailable() {
        return vertexAIEnabled && !projectId.isEmpty();
    }
    
    /**
     * Get service status information
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", vertexAIEnabled);
        status.put("configured", !projectId.isEmpty());
        status.put("projectId", projectId.isEmpty() ? "not-configured" : projectId);
        status.put("location", location);
        status.put("model", modelName);
        status.put("available", isAvailable());
        return status;
    }
}
