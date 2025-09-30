package com.documentclassifier.controller;

import com.documentclassifier.service.VaultGemmaService;
import com.documentclassifier.service.FileProcessingService;
import com.documentclassifier.service.OcrService;
import com.documentclassifier.vault.SecureDocumentVault;
import com.documentclassifier.vault.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/secure")
public class SecureDocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureDocumentController.class);
    
    private final FileProcessingService fileProcessingService;
    private final OcrService ocrService;
    private final VaultGemmaService vaultGemmaService;
    private final SecureDocumentVault documentVault;
    private final AuditService auditService;
    
    @Autowired
    public SecureDocumentController(
            FileProcessingService fileProcessingService,
            OcrService ocrService,
            VaultGemmaService vaultGemmaService,
            SecureDocumentVault documentVault,
            AuditService auditService) {
        this.fileProcessingService = fileProcessingService;
        this.ocrService = ocrService;
        this.vaultGemmaService = vaultGemmaService;
        this.documentVault = documentVault;
        this.auditService = auditService;
    }
    
    @PostMapping("/classify-documents")
    public ResponseEntity<?> classifyDocumentsSecurely(@RequestParam("file") MultipartFile file,
                                                      HttpServletRequest request) {
        String userId = getCurrentUserId();
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        logger.info("Received secure document classification request from user: {} (IP: {})", userId, ipAddress);
        
        // Audit authentication
        auditService.logAuthentication(userId, "SECURE_DOCUMENT_CLASSIFICATION", true, ipAddress, userAgent);
        
        // Validate file type
        if (!isZipFile(file)) {
            logger.warn("Invalid file type received from user: {}", userId);
            auditService.logSecurityEvent(userId, "INVALID_FILE_TYPE", "MEDIUM", false, 
                                        "Invalid file type: " + file.getContentType());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only ZIP files are allowed."));
        }
        
        List<File> extractedFiles = null;
        
        try {
            // Extract images from ZIP
            extractedFiles = fileProcessingService.extractImagesFromZip(file);
            logger.info("Extracted {} images from ZIP file for user: {}", extractedFiles.size(), userId);
            
            // Process each image with VaultGemma
            Map<String, Object> results = new HashMap<>();
            Map<String, String> documentIds = new HashMap<>();
            
            for (File imageFile : extractedFiles) {
                String filename = imageFile.getName();
                
                try {
                    // Extract text using OCR
                    String extractedText = ocrService.extractTextFromImage(imageFile);
                    
                    if (extractedText.isEmpty()) {
                        results.put(filename, Map.of(
                            "classification", "None",
                            "reason", "No text extracted"
                        ));
                        logger.warn("No text extracted from image: {} for user: {}", filename, userId);
                        continue;
                    }
                    
                    // Classify document type using VaultGemma with differential privacy
                    String documentType = vaultGemmaService.classifyDocumentTypeSecurely(extractedText, userId);
                    
                    // Store document securely in vault
                    byte[] imageData = java.nio.file.Files.readAllBytes(imageFile.toPath());
                    String documentId = documentVault.storeDocument(imageData, filename, documentType, userId);
                    
                    // Prepare result
                    Map<String, Object> documentResult = new HashMap<>();
                    documentResult.put("classification", documentType);
                    documentResult.put("documentId", documentId);
                    documentResult.put("securelyStored", true);
                    documentResult.put("privacyProtected", true);
                    
                    results.put(filename, documentResult);
                    documentIds.put(filename, documentId);
                    
                    // Log successful processing
                    auditService.logDocumentProcessing(userId, filename, documentType, 
                                                     "SECURE_CLASSIFICATION", true, 
                                                     "Document classified and stored securely");
                    
                    logger.debug("Processed {} securely: {} for user: {}", filename, documentType, userId);
                    
                } catch (Exception e) {
                    logger.error("Error processing image {} for user {}: {}", filename, userId, e.getMessage());
                    auditService.logDocumentProcessing(userId, filename, "UNKNOWN", 
                                                     "SECURE_CLASSIFICATION", false, 
                                                     "Error: " + e.getMessage());
                    results.put(filename, Map.of(
                        "classification", "Error",
                        "error", e.getMessage(),
                        "securelyStored", false
                    ));
                }
            }
            
            // Add privacy budget status to response
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("privacyBudgetStatus", privacyStatus);
            response.put("totalProcessed", results.size());
            response.put("vaultGemmaEnabled", true);
            response.put("differentialPrivacy", true);
            
            logger.info("Successfully processed {} images securely for user: {}", results.size(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing ZIP file for user {}: {}", userId, e.getMessage(), e);
            auditService.logSecurityEvent(userId, "SECURE_PROCESSING_FAILED", "HIGH", false, 
                                        "Secure processing failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
            
        } finally {
            // Clean up temporary files
            if (extractedFiles != null) {
                fileProcessingService.cleanupFiles(extractedFiles);
            }
        }
    }
    
    @GetMapping("/documents")
    public ResponseEntity<?> listUserDocuments() {
        String userId = getCurrentUserId();
        
        try {
            List<SecureDocumentVault.DocumentMetadata> documents = documentVault.listUserDocuments(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("totalCount", documents.size());
            response.put("userId", userId);
            
            logger.info("Listed {} documents for user: {}", documents.size(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error listing documents for user {}: {}", userId, e.getMessage());
            auditService.logSecurityEvent(userId, "LIST_DOCUMENTS_FAILED", "MEDIUM", false, 
                                        "Failed to list documents: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list documents"));
        }
    }
    
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<?> retrieveDocument(@PathVariable String documentId) {
        String userId = getCurrentUserId();
        
        try {
            SecureDocumentVault.SecureDocument document = documentVault.retrieveDocument(documentId, userId);
            
            if (document == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", document.getDocumentId());
            response.put("metadata", document.getMetadata());
            response.put("dataSize", document.getData().length);
            response.put("retrieved", true);
            
            logger.info("Retrieved document {} for user: {}", documentId, userId);
            return ResponseEntity.ok(response);
            
        } catch (SecurityException e) {
            logger.warn("Unauthorized access attempt to document {} by user: {}", documentId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
                    
        } catch (Exception e) {
            logger.error("Error retrieving document {} for user {}: {}", documentId, userId, e.getMessage());
            auditService.logSecurityEvent(userId, "RETRIEVE_DOCUMENT_FAILED", "MEDIUM", false, 
                                        "Failed to retrieve document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve document"));
        }
    }
    
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        String userId = getCurrentUserId();
        
        try {
            boolean deleted = documentVault.deleteDocument(documentId, userId);
            
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("deleted", true);
            response.put("securelyDeleted", true);
            
            logger.info("Deleted document {} for user: {}", documentId, userId);
            return ResponseEntity.ok(response);
            
        } catch (SecurityException e) {
            logger.warn("Unauthorized delete attempt for document {} by user: {}", documentId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
                    
        } catch (Exception e) {
            logger.error("Error deleting document {} for user {}: {}", documentId, userId, e.getMessage());
            auditService.logSecurityEvent(userId, "DELETE_DOCUMENT_FAILED", "MEDIUM", false, 
                                        "Failed to delete document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete document"));
        }
    }
    
    @GetMapping("/privacy-budget")
    public ResponseEntity<?> getPrivacyBudgetStatus() {
        String userId = getCurrentUserId();
        
        try {
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            
            logger.debug("Privacy budget status requested for user: {}", userId);
            return ResponseEntity.ok(privacyStatus);
            
        } catch (Exception e) {
            logger.error("Error getting privacy budget for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get privacy budget status"));
        }
    }
    
    @PostMapping("/privacy-budget/reset")
    public ResponseEntity<?> resetPrivacyBudget() {
        String userId = getCurrentUserId();
        
        try {
            vaultGemmaService.resetPrivacyBudget(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("budgetReset", true);
            response.put("message", "Privacy budget has been reset");
            
            logger.info("Privacy budget reset for user: {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error resetting privacy budget for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset privacy budget"));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> secureHealth() {
        String userId = getCurrentUserId();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "Secure Document Classifier API with VaultGemma");
        health.put("version", "2.0.0");
        health.put("vaultGemmaEnabled", true);
        health.put("differentialPrivacy", true);
        health.put("encryptionEnabled", true);
        health.put("auditingEnabled", true);
        health.put("vaultGemmaModelAvailable", vaultGemmaService.isVaultGemmaModelAvailable());
        health.put("userId", userId);
        
        return ResponseEntity.ok(health);
    }
    
    // Helper methods
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isZipFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        return (filename != null && filename.toLowerCase().endsWith(".zip")) ||
               (contentType != null && (contentType.equals("application/zip") || 
                                       contentType.equals("application/x-zip-compressed")));
    }
}
