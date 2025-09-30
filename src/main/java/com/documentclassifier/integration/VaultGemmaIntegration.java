package com.documentclassifier.integration;

import com.documentclassifier.service.VaultGemmaService;
import com.documentclassifier.vault.SecureDocumentVault;
import com.documentclassifier.vault.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * VaultGemma Integration Service
 * 
 * This service handles the integration of VaultGemma differential privacy
 * with document classification, providing secure document processing
 * with privacy guarantees.
 */
@Component
public class VaultGemmaIntegration {
    
    private static final Logger logger = LoggerFactory.getLogger(VaultGemmaIntegration.class);
    
    private final VaultGemmaService vaultGemmaService;
    private final SecureDocumentVault documentVault;
    private final AuditService auditService;
    
    @Autowired
    public VaultGemmaIntegration(
            VaultGemmaService vaultGemmaService,
            SecureDocumentVault documentVault,
            AuditService auditService) {
        this.vaultGemmaService = vaultGemmaService;
        this.documentVault = documentVault;
        this.auditService = auditService;
    }
    
    /**
     * Process document with VaultGemma privacy protection
     * 
     * @param extractedText The text extracted from the document
     * @param originalFile The original image file
     * @param filename The filename for identification
     * @param userId User identifier for privacy budget tracking
     * @return ProcessingResult containing classification and security metadata
     */
    public ProcessingResult processDocumentSecurely(String extractedText, File originalFile, 
                                                   String filename, String userId) {
        try {
            // Check privacy budget before processing
            if (!vaultGemmaService.hasPrivacyBudget(userId)) {
                logger.warn("Privacy budget exceeded for user: {}", userId);
                return ProcessingResult.budgetExceeded(filename);
            }
            
            // Classify document with differential privacy
            String documentType = vaultGemmaService.classifyWithPrivacy(extractedText, userId);
            
            // Convert File to byte array for secure storage
            byte[] documentData;
            try {
                documentData = Files.readAllBytes(originalFile.toPath());
            } catch (IOException e) {
                logger.error("Failed to read file {} for secure storage: {}", filename, e.getMessage());
                return ProcessingResult.error(filename, "Failed to read file for secure storage");
            }
            
            // Store document securely in vault
            String documentId = documentVault.storeDocument(
                documentData, 
                filename, 
                documentType, 
                userId
            );
            
            // Log successful processing
            auditService.logDocumentProcessing(userId, filename, documentType, 
                                             "VAULTGEMMA_CLASSIFICATION", true, 
                                             "Document classified and stored securely");
            
            logger.debug("Successfully processed {} with VaultGemma: {}", filename, documentType);
            
            return ProcessingResult.success(filename, documentType, documentId);
            
        } catch (Exception e) {
            logger.error("Error processing document {} with VaultGemma: {}", filename, e.getMessage());
            
            // Log processing failure
            auditService.logDocumentProcessing(userId, filename, "UNKNOWN", 
                                             "VAULTGEMMA_CLASSIFICATION", false, 
                                             "Error: " + e.getMessage());
            
            return ProcessingResult.error(filename, e.getMessage());
        }
    }
    
    /**
     * Get current privacy budget status for a user
     */
    public Map<String, Object> getPrivacyBudgetStatus(String userId) {
        return vaultGemmaService.getPrivacyBudgetStatus(userId);
    }
    
    /**
     * Reset privacy budget for a user
     */
    public void resetPrivacyBudget(String userId) {
        vaultGemmaService.resetPrivacyBudget(userId);
        logger.info("Privacy budget reset for user: {}", userId);
    }
    
    /**
     * Check if VaultGemma model is available
     */
    public boolean isVaultGemmaAvailable() {
        return vaultGemmaService.isVaultGemmaModelAvailable();
    }
    
    /**
     * Result class for document processing
     */
    public static class ProcessingResult {
        private final String filename;
        private final String classification;
        private final String documentId;
        private final boolean success;
        private final boolean privacyProtected;
        private final boolean securelyStored;
        private final String error;
        private final boolean budgetExceeded;
        
        private ProcessingResult(String filename, String classification, String documentId, 
                               boolean success, boolean privacyProtected, boolean securelyStored, 
                               String error, boolean budgetExceeded) {
            this.filename = filename;
            this.classification = classification;
            this.documentId = documentId;
            this.success = success;
            this.privacyProtected = privacyProtected;
            this.securelyStored = securelyStored;
            this.error = error;
            this.budgetExceeded = budgetExceeded;
        }
        
        public static ProcessingResult success(String filename, String classification, String documentId) {
            return new ProcessingResult(filename, classification, documentId, true, true, true, null, false);
        }
        
        public static ProcessingResult error(String filename, String error) {
            return new ProcessingResult(filename, "Error", null, false, false, false, error, false);
        }
        
        public static ProcessingResult budgetExceeded(String filename) {
            return new ProcessingResult(filename, "Budget Exceeded", null, false, false, false, 
                                      "Privacy budget exceeded", true);
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("classification", classification);
            result.put("success", success);
            result.put("privacyProtected", privacyProtected);
            result.put("securelyStored", securelyStored);
            result.put("budgetExceeded", budgetExceeded);
            
            if (documentId != null) {
                result.put("documentId", documentId);
            }
            
            if (error != null) {
                result.put("error", error);
            }
            
            return result;
        }
        
        // Getters
        public String getFilename() { return filename; }
        public String getClassification() { return classification; }
        public String getDocumentId() { return documentId; }
        public boolean isSuccess() { return success; }
        public boolean isPrivacyProtected() { return privacyProtected; }
        public boolean isSecurelyStored() { return securelyStored; }
        public String getError() { return error; }
        public boolean isBudgetExceeded() { return budgetExceeded; }
    }
}
