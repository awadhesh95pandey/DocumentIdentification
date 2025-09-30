package com.documentclassifier.controller;

import com.documentclassifier.dto.ClassificationResult;
import com.documentclassifier.service.DocumentClassificationService;
import com.documentclassifier.service.FileProcessingService;
import com.documentclassifier.service.OcrService;
import com.documentclassifier.service.HuggingFaceVaultGemmaService;
import com.documentclassifier.integration.VaultGemmaIntegration;
import com.documentclassifier.vault.SecureDocumentVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentClassifierController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentClassifierController.class);
    
    private final FileProcessingService fileProcessingService;
    private final OcrService ocrService;
    private final DocumentClassificationService classificationService;
    private final VaultGemmaIntegration vaultGemmaIntegration;
    private final SecureDocumentVault documentVault;
    private final HuggingFaceVaultGemmaService huggingFaceService;
    
    @Value("${vaultgemma.enabled:true}")
    private boolean vaultGemmaEnabled;
    
    @Autowired
    public DocumentClassifierController(
            FileProcessingService fileProcessingService,
            OcrService ocrService,
            DocumentClassificationService classificationService,
            VaultGemmaIntegration vaultGemmaIntegration,
            SecureDocumentVault documentVault,
            HuggingFaceVaultGemmaService huggingFaceService) {
        this.fileProcessingService = fileProcessingService;
        this.ocrService = ocrService;
        this.classificationService = classificationService;
        this.vaultGemmaIntegration = vaultGemmaIntegration;
        this.documentVault = documentVault;
        this.huggingFaceService = huggingFaceService;
    }
    
    @PostConstruct
    public void init() {
        logger.info("DocumentClassifierController initialized with VaultGemma enabled: {}", vaultGemmaEnabled);
        logger.info("VaultGemma model available: {}", vaultGemmaIntegration.isVaultGemmaAvailable());
    }
    
    @PostMapping("/classify-documents")
    public ResponseEntity<?> classifyDocuments(@RequestParam("file") MultipartFile file) {
        logger.info("Received document classification request for file: {}", file.getOriginalFilename());
        
        // Validate file type
        if (!isZipFile(file)) {
            logger.warn("Invalid file type received: {}", file.getContentType());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only ZIP files are allowed."));
        }
        
        List<File> extractedFiles = null;
        String userId = "user"; // Simplified user identification
        
        try {
            // Extract images from ZIP
            extractedFiles = fileProcessingService.extractImagesFromZip(file);
            logger.info("Extracted {} images from ZIP file", extractedFiles.size());
            
            // Process each image with VaultGemma if enabled
            Map<String, Object> results = new HashMap<>();
            
            for (File imageFile : extractedFiles) {
                String filename = imageFile.getName();
                
                try {
                    // Extract text using OCR
                    String extractedText = ocrService.extractTextFromImage(imageFile);
                    
                    if (extractedText.isEmpty()) {
                        results.put(filename, Map.of(
                            "classification", "None",
                            "reason", "No text extracted",
                            "privacyProtected", false,
                            "securelyStored", false
                        ));
                        logger.warn("No text extracted from image: {}", filename);
                        continue;
                    }
                    
                    // Use VaultGemma if enabled, otherwise use regular classification
                    boolean isVaultGemmaAvailable = vaultGemmaIntegration.isVaultGemmaAvailable();
                    logger.debug("VaultGemma status - Enabled: {}, Available: {}", vaultGemmaEnabled, isVaultGemmaAvailable);
                    
                    if (vaultGemmaEnabled && isVaultGemmaAvailable) {
                        VaultGemmaIntegration.ProcessingResult result = 
                            vaultGemmaIntegration.processDocumentSecurely(extractedText, imageFile, filename, userId);
                        results.put(filename, result.toMap());
                        logger.info("Processed {} with VaultGemma: {}", filename, result.getClassification());
                    } else {
                        // Fallback to regular classification
                        String documentType = classificationService.classifyDocumentType(extractedText);
                        results.put(filename, Map.of(
                            "classification", documentType,
                            "privacyProtected", false,
                            "securelyStored", false,
                            "vaultGemmaUsed", false
                        ));
                        logger.info("Processed {} with regular classification (VaultGemma enabled: {}, available: {}): {}", 
                                  filename, vaultGemmaEnabled, isVaultGemmaAvailable, documentType);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing image {}: {}", filename, e.getMessage());
                    results.put(filename, Map.of(
                        "classification", "Error",
                        "error", e.getMessage(),
                        "privacyProtected", false,
                        "securelyStored", false
                    ));
                }
            }
            
            // Build comprehensive response
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalProcessed", results.size());
            response.put("vaultGemmaEnabled", vaultGemmaEnabled && vaultGemmaIntegration.isVaultGemmaAvailable());
            
            // Add privacy budget status if VaultGemma is enabled
            if (vaultGemmaEnabled && vaultGemmaIntegration.isVaultGemmaAvailable()) {
                response.put("privacyBudgetStatus", vaultGemmaIntegration.getPrivacyBudgetStatus(userId));
                response.put("differentialPrivacy", true);
            } else {
                response.put("differentialPrivacy", false);
            }
            
            logger.info("Successfully processed {} images", results.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing ZIP file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
            
        } finally {
            // Clean up temporary files
            if (extractedFiles != null) {
                fileProcessingService.cleanupFiles(extractedFiles);
            }
        }
    }

    // Document Vault Management Endpoints
    
    @GetMapping("/documents")
    public ResponseEntity<?> listUserDocuments() {
        String userId = "user";
        
        try {
            List<SecureDocumentVault.DocumentMetadata> documents = documentVault.listUserDocuments(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("totalCount", documents.size());
            
            logger.info("Listed {} documents", documents.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error listing documents: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list documents"));
        }
    }
    
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<?> retrieveDocument(@PathVariable String documentId) {
        String userId = "user";
        
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
            
            logger.info("Retrieved document {}", documentId);
            return ResponseEntity.ok(response);
                    
        } catch (Exception e) {
            logger.error("Error retrieving document {}: {}", documentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve document"));
        }
    }
    
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        String userId = "user";
        
        try {
            boolean deleted = documentVault.deleteDocument(documentId, userId);
            
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("deleted", true);
            response.put("securelyDeleted", true);
            
            logger.info("Deleted document {}", documentId);
            return ResponseEntity.ok(response);
                    
        } catch (Exception e) {
            logger.error("Error deleting document {}: {}", documentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete document"));
        }
    }
    
    // Privacy Budget Management Endpoints
    
    @GetMapping("/privacy-budget")
    public ResponseEntity<?> getPrivacyBudgetStatus() {
        String userId = "user";
        
        try {
            Map<String, Object> privacyStatus = vaultGemmaIntegration.getPrivacyBudgetStatus(userId);
            
            logger.debug("Privacy budget status requested");
            return ResponseEntity.ok(privacyStatus);
            
        } catch (Exception e) {
            logger.error("Error getting privacy budget: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get privacy budget status"));
        }
    }
    
    @PostMapping("/privacy-budget/reset")
    public ResponseEntity<?> resetPrivacyBudget() {
        String userId = "user";
        
        try {
            vaultGemmaIntegration.resetPrivacyBudget(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("budgetReset", true);
            response.put("message", "Privacy budget has been reset");
            
            logger.info("Privacy budget reset");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error resetting privacy budget: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset privacy budget"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "Document Classifier API with VaultGemma");
        health.put("version", "2.0.0");
        health.put("vaultGemmaEnabled", vaultGemmaEnabled);
        health.put("differentialPrivacy", vaultGemmaEnabled && vaultGemmaIntegration.isVaultGemmaAvailable());
        health.put("encryptionEnabled", true);
        health.put("auditingEnabled", true);
        health.put("vaultGemmaModelAvailable", vaultGemmaIntegration.isVaultGemmaAvailable());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get VaultGemma service status including Hugging Face API connectivity
     */
    @GetMapping("/vaultgemma/status")
    public ResponseEntity<?> getVaultGemmaStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Basic VaultGemma configuration
            status.put("vaultGemmaEnabled", vaultGemmaEnabled);
            status.put("modelAvailable", vaultGemmaIntegration.isVaultGemmaModelAvailable());
            
            // Hugging Face service status
            Map<String, Object> huggingFaceStatus = huggingFaceService.getServiceStatus();
            status.put("huggingFaceService", huggingFaceStatus);
            
            // Overall service status
            boolean overallAvailable = vaultGemmaEnabled && 
                (vaultGemmaIntegration.isVaultGemmaModelAvailable() || 
                 (Boolean) huggingFaceStatus.get("available"));
            status.put("serviceAvailable", overallAvailable);
            
            // Configuration info
            status.put("privacyBudget", 1.0);
            status.put("epsilonPerQuery", 0.1);
            status.put("differentialPrivacy", true);
            
            logger.info("VaultGemma status check completed");
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error checking VaultGemma status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check VaultGemma status"));
        }
    }
    
    /**
     * Validate if uploaded file is a ZIP file
     */
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
