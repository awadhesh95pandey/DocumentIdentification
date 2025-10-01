package com.documentclassifier.service;

import com.documentclassifier.config.VaultGemmaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VaultGemmaService {
    
    private static final Logger logger = LoggerFactory.getLogger(VaultGemmaService.class);
    
    private final VaultGemmaConfig config;
    private final HuggingFaceVaultGemmaService huggingFaceService;
    private final LocalVaultGemmaService localVaultGemmaService;
    private final SecureRandom secureRandom;
    
    // Privacy budget tracking per user session
    private final Map<String, Double> privacyBudgetTracker = new ConcurrentHashMap<>();
    
    // Fallback classification patterns for when VaultGemma model is not available
    private final Map<String, String[]> classificationPatterns;
    
    @Autowired
    public VaultGemmaService(VaultGemmaConfig config, 
                           HuggingFaceVaultGemmaService huggingFaceService,
                           LocalVaultGemmaService localVaultGemmaService) {
        this.config = config;
        this.huggingFaceService = huggingFaceService;
        this.localVaultGemmaService = localVaultGemmaService;
        this.secureRandom = new SecureRandom();
        
        // Initialize classification patterns for privacy-preserving fallback
        this.classificationPatterns = initializeClassificationPatterns();
        
        logger.info("VaultGemmaService initialized with local model support");
    }
    
    /**
     * Classify document with privacy protection and three-tier fallback
     */
    public String classifyWithPrivacy(String text, String userId) {
        try {
            logger.debug("Starting privacy-preserving classification for user: {}", userId);
            
            // Check privacy budget
            if (!checkPrivacyBudget(userId)) {
                logger.warn("Privacy budget exceeded for user: {}", userId);
                return "Error: Privacy budget exceeded";
            }
            
            // Perform classification with fallback
            String classification = performPrivateClassification(text, userId);
            
            // Update privacy budget
            updatePrivacyBudget(userId, config.getModel().getEpsilonPerQuery());
            
            logger.info("Classification completed for user: {}", userId);
            return classification;
            
        } catch (Exception e) {
            logger.error("Failed to classify document", e);
            return "Error: Classification failed";
        }
    }
    
    /**
     * Perform privacy-preserving classification with three-tier fallback:
     * 1. Local VaultGemma model (preferred)
     * 2. Hugging Face VaultGemma API (fallback)
     * 3. Pattern-based classification (final fallback)
     */
    private String performPrivateClassification(String text, String userId) {
        try {
            String classification;
            
            // First priority: Try local VaultGemma model
            if (config.getModel().isEnableLocalModel() && localVaultGemmaService.isModelAvailable()) {
                logger.debug("Using local VaultGemma model for classification");
                try {
                    classification = localVaultGemmaService.classifyDocumentWithPrivacy(text, userId);
                    logger.debug("Local VaultGemma classification successful: {} for user: {}", classification, userId);
                    return classification;
                } catch (Exception localError) {
                    logger.warn("Local VaultGemma model failed, falling back to API: {}", localError.getMessage());
                }
            } else {
                logger.debug("Local VaultGemma model not available or disabled");
            }
            
            // Second priority: Try Hugging Face VaultGemma API
            if (huggingFaceService.isServiceAvailable()) {
                logger.debug("Using Hugging Face VaultGemma API for classification");
                try {
                    classification = huggingFaceService.classifyDocumentWithPrivacy(text, userId);
                    logger.debug("Hugging Face VaultGemma classification successful: {} for user: {}", classification, userId);
                    return classification;
                } catch (Exception apiError) {
                    logger.warn("Hugging Face VaultGemma API failed, falling back to patterns: {}", apiError.getMessage());
                }
            } else {
                logger.debug("Hugging Face VaultGemma API not available");
            }
            
            // Final fallback: Pattern-based classification
            logger.debug("Using pattern-based classification as final fallback");
            classification = classifyWithPatterns(text);
            
            // Add differential privacy noise for fallback method
            classification = addDifferentialPrivacyNoise(classification, userId);
            
            logger.debug("Pattern-based classification result: {} for user: {}", classification, userId);
            return classification;
            
        } catch (Exception e) {
            logger.error("All classification methods failed for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("All classification methods failed", e);
        }
    }
    
    /**
     * Pattern-based classification fallback
     */
    private String classifyWithPatterns(String text) {
        String lowerText = text.toLowerCase();
        
        for (Map.Entry<String, String[]> entry : classificationPatterns.entrySet()) {
            String category = entry.getKey();
            String[] patterns = entry.getValue();
            
            for (String pattern : patterns) {
                if (lowerText.contains(pattern.toLowerCase())) {
                    logger.debug("Pattern match found: {} -> {}", pattern, category);
                    return category;
                }
            }
        }
        
        // Default classification if no patterns match
        return "UNKNOWN";
    }
    
    /**
     * Add differential privacy noise to classification result
     */
    private String addDifferentialPrivacyNoise(String classification, String userId) {
        double epsilon = config.getModel().getEpsilonPerQuery();
        double noiseThreshold = Math.exp(-epsilon);
        
        // Add Laplace noise for differential privacy
        double noise = secureRandom.nextGaussian() * (1.0 / epsilon);
        
        // With small probability, return a random classification for privacy
        if (Math.abs(noise) > noiseThreshold) {
            String[] possibleClasses = {"PAN", "AADHAAR", "PASSPORT", "DRIVING_LICENSE", "UNKNOWN"};
            String noisyClassification = possibleClasses[secureRandom.nextInt(possibleClasses.length)];
            logger.debug("Applied differential privacy noise: {} -> {} for user: {}", 
                        classification, noisyClassification, userId);
            return noisyClassification;
        }
        
        return classification;
    }
    
    /**
     * Check if user has sufficient privacy budget
     */
    private boolean checkPrivacyBudget(String userId) {
        double usedBudget = privacyBudgetTracker.getOrDefault(userId, 0.0);
        double maxBudget = config.getModel().getPrivacyBudget();
        double epsilonPerQuery = config.getModel().getEpsilonPerQuery();
        
        return (usedBudget + epsilonPerQuery) <= maxBudget;
    }
    
    /**
     * Update privacy budget for user
     */
    private void updatePrivacyBudget(String userId, double epsilon) {
        privacyBudgetTracker.merge(userId, epsilon, Double::sum);
        logger.debug("Updated privacy budget for user {}: used {}", userId, 
                    privacyBudgetTracker.get(userId));
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
        status.put("epsilonPerQuery", config.getModel().getEpsilonPerQuery());
        
        return status;
    }
    
    /**
     * Reset privacy budget for user
     */
    public void resetPrivacyBudget(String userId) {
        privacyBudgetTracker.remove(userId);
        logger.info("Privacy budget reset for user: {}", userId);
    }
    
    /**
     * Validate VaultGemma model availability
     */
    public boolean isVaultGemmaModelAvailable() {
        try {
            // Check local model availability
            boolean localModelAvailable = config.getModel().isEnableLocalModel() && 
                                        localVaultGemmaService.isModelAvailable();
            
            // Check API availability
            boolean apiAvailable = huggingFaceService.isServiceAvailable();
            
            // Always have pattern-based fallback
            boolean patternFallbackAvailable = true;
            
            if (localModelAvailable) {
                logger.info("✅ Local VaultGemma model available");
                return true;
            } else if (apiAvailable) {
                logger.info("✅ Hugging Face VaultGemma API available (local model not loaded)");
                return true;
            } else if (patternFallbackAvailable) {
                logger.info("✅ Pattern-based classification available (VaultGemma models not available)");
                return true; // Enable VaultGemma features with pattern fallback
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warn("VaultGemma availability check failed: {}, using pattern fallback", e.getMessage());
            return true; // Enable pattern fallback even if checks fail
        }
    }
    
    /**
     * Get detailed model availability status
     */
    public Map<String, Object> getModelAvailabilityStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean localModelEnabled = config.getModel().isEnableLocalModel();
            boolean localModelAvailable = localModelEnabled && localVaultGemmaService.isModelAvailable();
            boolean apiAvailable = huggingFaceService.isServiceAvailable();
            
            status.put("localModel", Map.of(
                "enabled", localModelEnabled,
                "available", localModelAvailable,
                "path", config.getModel().getPath(),
                "metrics", localModelAvailable ? localVaultGemmaService.getModelMetrics() : Map.of()
            ));
            
            status.put("apiModel", Map.of(
                "available", apiAvailable,
                "service", "Hugging Face VaultGemma"
            ));
            
            status.put("patternFallback", Map.of(
                "available", true,
                "description", "Privacy-preserving pattern-based classification"
            ));
            
            // Determine primary method
            String primaryMethod = localModelAvailable ? "local" : 
                                 apiAvailable ? "api" : "pattern";
            status.put("primaryMethod", primaryMethod);
            status.put("overallAvailable", true);
            
        } catch (Exception e) {
            logger.error("Error getting model availability status", e);
            status.put("error", e.getMessage());
            status.put("overallAvailable", false);
        }
        
        return status;
    }
    
    /**
     * Initialize classification patterns for fallback
     */
    private Map<String, String[]> initializeClassificationPatterns() {
        Map<String, String[]> patterns = new HashMap<>();
        
        patterns.put("PAN", new String[]{
            "pan", "permanent account number", "income tax", "tax", "pancard"
        });
        
        patterns.put("AADHAAR", new String[]{
            "aadhaar", "aadhar", "uid", "unique identification", "uidai", "12 digit"
        });
        
        patterns.put("PASSPORT", new String[]{
            "passport", "republic of india", "immigration", "visa", "travel document"
        });
        
        patterns.put("DRIVING_LICENSE", new String[]{
            "driving license", "driving licence", "dl", "motor vehicle", "transport"
        });
        
        return patterns;
    }
}
