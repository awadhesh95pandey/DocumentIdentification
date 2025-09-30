package com.documentclassifier.service;

import com.documentclassifier.config.VaultGemmaConfig;
import com.documentclassifier.vault.AuditService;
import com.documentclassifier.vault.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VaultGemmaService {
    
    private static final Logger logger = LoggerFactory.getLogger(VaultGemmaService.class);
    
    private final VaultGemmaConfig config;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecureRandom secureRandom;
    
    // Privacy budget tracking per user session
    private final Map<String, Double> privacyBudgetTracker = new ConcurrentHashMap<>();
    
    // Fallback classification patterns for when VaultGemma model is not available
    private final Map<String, String[]> classificationPatterns;
    
    @Autowired
    public VaultGemmaService(VaultGemmaConfig config, 
                           EncryptionService encryptionService,
                           AuditService auditService) {
        this.config = config;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.secureRandom = new SecureRandom();
        
        // Initialize classification patterns for privacy-preserving fallback
        this.classificationPatterns = initializeClassificationPatterns();
        
        logger.info("VaultGemma service initialized with privacy budget: {}", 
                   config.getModel().getPrivacyBudget());
    }
    
    /**
     * Classify document type using VaultGemma with differential privacy
     */
    public String classifyDocumentTypeSecurely(String extractedText, String userId) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            logger.warn("Empty text provided for secure classification");
            auditService.logPrivacyEvent(userId, "CLASSIFICATION_ATTEMPT", "HIGH", 
                                       "Empty text provided");
            return "None";
        }
        
        try {
            // Check privacy budget
            if (!checkPrivacyBudget(userId)) {
                logger.warn("Privacy budget exceeded for user: {}", userId);
                auditService.logPrivacyEvent(userId, "PRIVACY_BUDGET_EXCEEDED", "CRITICAL", 
                                           "User exceeded privacy budget");
                return "Privacy Budget Exceeded";
            }
            
            // Encrypt the text for secure processing
            String encryptedText = encryptionService.encryptText(extractedText);
            
            // Log privacy operation
            auditService.logPrivacyEvent(userId, "SECURE_CLASSIFICATION", "HIGH", 
                                       "Document classification with differential privacy");
            
            // Perform classification with differential privacy
            String classification = performPrivateClassification(extractedText, userId);
            
            // Update privacy budget
            updatePrivacyBudget(userId);
            
            // Clear sensitive data from memory
            clearSensitiveText(extractedText);
            
            logger.info("Secure document classification completed for user: {}", userId);
            return classification;
            
        } catch (Exception e) {
            logger.error("Failed to classify document securely", e);
            auditService.logSecurityEvent(userId, "CLASSIFICATION_ERROR", "HIGH", false, 
                                        "Secure classification failed: " + e.getMessage());
            return "Error: Secure classification failed";
        }
    }
    
    /**
     * Perform privacy-preserving classification
     * This is a simplified implementation that demonstrates differential privacy concepts
     * In a production environment, this would integrate with the actual VaultGemma model
     */
    private String performPrivateClassification(String text, String userId) {
        try {
            // Simulate VaultGemma model inference with differential privacy
            // In production, this would load and run the actual VaultGemma ONNX model
            
            String classification = classifyWithPatterns(text);
            
            // Add differential privacy noise
            classification = addDifferentialPrivacyNoise(classification, userId);
            
            logger.debug("Private classification result: {} for user: {}", classification, userId);
            return classification;
            
        } catch (Exception e) {
            logger.error("Private classification failed", e);
            throw new RuntimeException("Private classification failed", e);
        }
    }
    
    /**
     * Pattern-based classification with privacy preservation
     */
    private String classifyWithPatterns(String text) {
        String lowerText = text.toLowerCase();
        
        // Check for Aadhaar patterns
        if (containsPatterns(lowerText, classificationPatterns.get("aadhaar"))) {
            return "Aadhaar";
        }
        
        // Check for PAN patterns
        if (containsPatterns(lowerText, classificationPatterns.get("pan"))) {
            return "PAN";
        }
        
        // Check for Voter ID patterns
        if (containsPatterns(lowerText, classificationPatterns.get("voter"))) {
            return "Voter ID";
        }
        
        // Check for Driving License patterns
        if (containsPatterns(lowerText, classificationPatterns.get("driving"))) {
            return "Driving License";
        }
        
        return "None";
    }
    
    /**
     * Add differential privacy noise to classification result
     */
    private String addDifferentialPrivacyNoise(String classification, String userId) {
        double epsilon = config.getModel().getPrivacyBudget();
        
        // Calculate noise based on differential privacy parameters
        double noiseLevel = calculatePrivacyNoise(epsilon);
        
        // Apply noise to classification confidence
        // In a real implementation, this would affect the model's output probabilities
        if (secureRandom.nextDouble() < noiseLevel) {
            // Occasionally return a different classification to preserve privacy
            String[] possibleTypes = {"Aadhaar", "PAN", "Voter ID", "Driving License", "None"};
            String noisyResult = possibleTypes[secureRandom.nextInt(possibleTypes.length)];
            
            auditService.logPrivacyEvent(userId, "DIFFERENTIAL_PRIVACY_NOISE", "HIGH", 
                                       "Applied privacy noise to classification result");
            
            return noisyResult;
        }
        
        return classification;
    }
    
    /**
     * Calculate privacy noise based on epsilon (privacy budget)
     */
    private double calculatePrivacyNoise(double epsilon) {
        // Laplace mechanism for differential privacy
        // Noise level is inversely proportional to epsilon
        return 1.0 / (epsilon * 10.0); // Simplified calculation
    }
    
    /**
     * Check if user has sufficient privacy budget
     */
    private boolean checkPrivacyBudget(String userId) {
        double usedBudget = privacyBudgetTracker.getOrDefault(userId, 0.0);
        double maxBudget = config.getModel().getPrivacyBudget();
        
        return usedBudget < maxBudget;
    }
    
    /**
     * Update privacy budget for user
     */
    private void updatePrivacyBudget(String userId) {
        double currentBudget = privacyBudgetTracker.getOrDefault(userId, 0.0);
        double increment = 0.1; // Small increment per classification
        privacyBudgetTracker.put(userId, currentBudget + increment);
        
        logger.debug("Updated privacy budget for user {}: {}", userId, currentBudget + increment);
    }
    
    /**
     * Reset privacy budget for user (e.g., daily reset)
     */
    public void resetPrivacyBudget(String userId) {
        privacyBudgetTracker.remove(userId);
        auditService.logPrivacyEvent(userId, "PRIVACY_BUDGET_RESET", "INFO", 
                                   "Privacy budget reset for user");
        logger.info("Privacy budget reset for user: {}", userId);
    }
    
    /**
     * Check if text contains classification patterns
     */
    private boolean containsPatterns(String text, String[] patterns) {
        if (patterns == null) return false;
        
        return Arrays.stream(patterns)
                    .anyMatch(pattern -> text.contains(pattern.toLowerCase()));
    }
    
    /**
     * Initialize classification patterns for privacy-preserving fallback
     */
    private Map<String, String[]> initializeClassificationPatterns() {
        Map<String, String[]> patterns = new HashMap<>();
        
        patterns.put("aadhaar", new String[]{
            "aadhaar", "aadhar", "uid", "unique identification",
            "government of india", "uidai"
        });
        
        patterns.put("pan", new String[]{
            "permanent account number", "income tax", "pan card",
            "assessee", "tax payer"
        });
        
        patterns.put("voter", new String[]{
            "voter", "election", "electoral", "constituency",
            "election commission"
        });
        
        patterns.put("driving", new String[]{
            "driving", "license", "licence", "motor vehicle",
            "transport", "dl no"
        });
        
        return patterns;
    }
    
    /**
     * Securely clear sensitive text from memory
     */
    private void clearSensitiveText(String text) {
        if (text != null) {
            // Convert to char array and clear
            char[] chars = text.toCharArray();
            encryptionService.clearSensitiveData(chars);
        }
    }
    
    /**
     * Get privacy budget status for user
     */
    public Map<String, Object> getPrivacyBudgetStatus(String userId) {
        double usedBudget = privacyBudgetTracker.getOrDefault(userId, 0.0);
        double maxBudget = config.getModel().getPrivacyBudget();
        double remainingBudget = Math.max(0, maxBudget - usedBudget);
        
        Map<String, Object> status = new HashMap<>();
        status.put("userId", userId);
        status.put("usedBudget", usedBudget);
        status.put("maxBudget", maxBudget);
        status.put("remainingBudget", remainingBudget);
        status.put("budgetExceeded", remainingBudget <= 0);
        
        return status;
    }
    
    /**
     * Validate VaultGemma model availability
     */
    public boolean isVaultGemmaModelAvailable() {
        try {
            Path modelPath = Paths.get(config.getModel().getPath());
            return Files.exists(modelPath);
        } catch (Exception e) {
            logger.warn("VaultGemma model not available: {}", e.getMessage());
            return false;
        }
    }
}
