package com.documentclassifier.controller;

import com.documentclassifier.integration.VaultGemmaIntegration;
import com.documentclassifier.service.GoogleVaultGemmaService;
import com.documentclassifier.service.LocalVaultGemmaService;
import com.documentclassifier.service.VaultGemmaService;
import com.documentclassifier.service.FileProcessingService;
import com.documentclassifier.service.OcrService;
import com.documentclassifier.service.VertexAIGeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentClassifierController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentClassifierController.class);
    
    private final LocalVaultGemmaService localVaultGemmaService;
    private final VaultGemmaService vaultGemmaService;
    private final VaultGemmaIntegration vaultGemmaIntegration;
    private final GoogleVaultGemmaService googleVaultGemmaService;
    private final FileProcessingService fileProcessingService;
    private final OcrService ocrService;
    private final VertexAIGeminiService vertexAIGeminiService;
    
    @Value("${vaultgemma.enabled:true}")
    private boolean vaultGemmaEnabled;
    
    @Autowired
    public DocumentClassifierController(
            LocalVaultGemmaService localVaultGemmaService,
            VaultGemmaService vaultGemmaService,
            VaultGemmaIntegration vaultGemmaIntegration,
            GoogleVaultGemmaService googleVaultGemmaService,
            FileProcessingService fileProcessingService,
            OcrService ocrService,
            VertexAIGeminiService vertexAIGeminiService) {
        this.localVaultGemmaService = localVaultGemmaService;
        this.vaultGemmaService = vaultGemmaService;
        this.vaultGemmaIntegration = vaultGemmaIntegration;
        this.googleVaultGemmaService = googleVaultGemmaService;
        this.fileProcessingService = fileProcessingService;
        this.ocrService = ocrService;
        this.vertexAIGeminiService = vertexAIGeminiService;
    }
    
    /**
     * Simple document classification endpoint using local VaultGemma
     */
    @PostMapping("/classify")
    public ResponseEntity<?> classifyDocument(@RequestParam("text") String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Text parameter is required"));
            }
            
            String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Use VaultGemma service with three-tier fallback
            String classification = vaultGemmaService.classifyWithPrivacy(text, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("classification", classification);
            response.put("vaultGemmaEnabled", vaultGemmaEnabled);
            response.put("localModelUsed", localVaultGemmaService.isModelAvailable());
            response.put("userId", userId);
            response.put("timestamp", System.currentTimeMillis());
            
            // Add privacy budget status
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            response.put("privacyBudgetStatus", privacyStatus);
            
            logger.info("Document classified: {} for user: {}", classification, userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Classification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Classification failed: " + e.getMessage()));
        }
    }
    
    /**
     * File upload classification endpoint - Enhanced to handle ZIP files with multiple documents
     */
    @PostMapping("/classify-file")
    public ResponseEntity<?> classifyFile(@RequestParam("file") MultipartFile file) {
        List<File> extractedFiles = null;
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is required"));
            }
            
            String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            String filename = file.getOriginalFilename();
            
            logger.info("Processing file: {} (size: {} bytes)", filename, file.getSize());
            
            // Check if it's a ZIP file
            if (filename != null && filename.toLowerCase().endsWith(".zip")) {
                return processZipFile(file, userId);
            } else {
                // Handle single image file
                return processSingleFile(file, userId);
            }
            
        } catch (Exception e) {
            logger.error("File classification failed: {}", e.getMessage());
            
            // Clean up any extracted files on error
            if (extractedFiles != null) {
                fileProcessingService.cleanupFiles(extractedFiles);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "File classification failed: " + e.getMessage()));
        }
    }
    
    /**
     * Process ZIP file containing multiple documents
     */
    private ResponseEntity<?> processZipFile(MultipartFile zipFile, String userId) {
        List<File> extractedFiles = null;
        try {
            logger.info("Processing ZIP file: {}", zipFile.getOriginalFilename());
            
            // Extract images from ZIP file
            extractedFiles = fileProcessingService.extractImagesFromZip(zipFile);
            logger.info("Extracted {} images from ZIP file", extractedFiles.size());
            
            List<Map<String, Object>> documentResults = new ArrayList<>();
            
            // Process each extracted image
            for (File imageFile : extractedFiles) {
                try {
                    logger.debug("Processing image: {}", imageFile.getName());
                    
                    // Extract text using OCR
                    String extractedText = ocrService.extractTextFromImage(imageFile);
                    
                    if (extractedText.trim().isEmpty()) {
                        logger.warn("No text extracted from image: {}", imageFile.getName());
                        extractedText = "Document image: " + imageFile.getName();
                    }
                    
                    // Classify the document using Vertex AI Gemini with VaultGemma protection
                    String classification = vertexAIGeminiService.classifyDocumentSecurely(extractedText, userId);
                    
                    // Create result for this document
                    Map<String, Object> documentResult = new HashMap<>();
                    documentResult.put("filename", imageFile.getName());
                    documentResult.put("classification", classification);
                    documentResult.put("textLength", extractedText.length());
                    documentResult.put("extractedText", extractedText.length() > 200 ? 
                        extractedText.substring(0, 200) + "..." : extractedText);
                    
                    documentResults.add(documentResult);
                    
                    logger.info("Classified document: {} -> {}", imageFile.getName(), classification);
                    
                } catch (Exception e) {
                    logger.error("Failed to process image {}: {}", imageFile.getName(), e.getMessage());
                    
                    // Add error result for this document
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("filename", imageFile.getName());
                    errorResult.put("classification", "ERROR");
                    errorResult.put("error", e.getMessage());
                    documentResults.add(errorResult);
                }
            }
            
            // Build comprehensive response
            Map<String, Object> response = new HashMap<>();
            response.put("filename", zipFile.getOriginalFilename());
            response.put("fileType", "ZIP");
            response.put("documentsProcessed", documentResults.size());
            response.put("documents", documentResults);
            response.put("vaultGemmaEnabled", vaultGemmaEnabled);
            response.put("localModelUsed", localVaultGemmaService.isModelAvailable());
            response.put("timestamp", System.currentTimeMillis());
            
            // Add privacy budget status (remove userId from response)
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            privacyStatus.remove("userId");
            response.put("privacyBudgetStatus", privacyStatus);
            
            logger.info("ZIP file processing completed: {} documents processed from {}", 
                       documentResults.size(), zipFile.getOriginalFilename());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("ZIP file processing failed: {}", e.getMessage());
            throw new RuntimeException("ZIP file processing failed", e);
        } finally {
            // Always clean up extracted files
            if (extractedFiles != null) {
                fileProcessingService.cleanupFiles(extractedFiles);
            }
        }
    }
    
    /**
     * Process single image file
     */
    private ResponseEntity<?> processSingleFile(MultipartFile file, String userId) {
        try {
            logger.info("Processing single file: {}", file.getOriginalFilename());
            
            // For single files, we'll use a simple approach for now
            // In a real implementation, you'd save the file temporarily and use OCR
            String extractedText = "Document: " + file.getOriginalFilename();
            
            // Use Vertex AI Gemini with VaultGemma protection
            String classification = vertexAIGeminiService.classifyDocumentSecurely(extractedText, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("filename", file.getOriginalFilename());
            response.put("fileType", "SINGLE");
            response.put("classification", classification);
            response.put("vaultGemmaEnabled", vaultGemmaEnabled);
            response.put("localModelUsed", localVaultGemmaService.isModelAvailable());
            response.put("timestamp", System.currentTimeMillis());
            
            // Add privacy budget status (remove userId from response)
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            privacyStatus.remove("userId");
            response.put("privacyBudgetStatus", privacyStatus);
            
            logger.info("Single file classified: {} -> {}", file.getOriginalFilename(), classification);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Single file processing failed: {}", e.getMessage());
            throw new RuntimeException("Single file processing failed", e);
        }
    }
    
    /**
     * Get VaultGemma service status
     */
    @GetMapping("/vaultgemma/status")
    public ResponseEntity<?> getVaultGemmaStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Basic VaultGemma configuration
            status.put("vaultGemmaEnabled", vaultGemmaEnabled);
            status.put("modelAvailable", vaultGemmaService.isVaultGemmaModelAvailable());
            
            // Get detailed model availability status
            Map<String, Object> modelStatus = vaultGemmaService.getModelAvailabilityStatus();
            status.putAll(modelStatus);
            
            // Configuration info
            status.put("differentialPrivacy", true);
            status.put("vertexAI", vertexAIGeminiService.getServiceStatus());
            status.put("timestamp", System.currentTimeMillis());
            
            logger.info("VaultGemma status check completed - Primary method: {}", 
                       modelStatus.get("primaryMethod"));
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error checking VaultGemma status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check VaultGemma status"));
        }
    }
    
    /**
     * Get VaultGemma model health status
     */
    @GetMapping("/vaultgemma/health")
    public ResponseEntity<?> getVaultGemmaHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Check overall VaultGemma availability
            boolean vaultGemmaAvailable = vaultGemmaService.isVaultGemmaModelAvailable();
            health.put("vaultGemmaAvailable", vaultGemmaAvailable);
            
            // Get detailed availability status
            Map<String, Object> availabilityStatus = vaultGemmaService.getModelAvailabilityStatus();
            health.put("modelStatus", availabilityStatus);
            
            // Determine health status
            String healthStatus = vaultGemmaAvailable ? "UP" : "DOWN";
            health.put("status", healthStatus);
            health.put("timestamp", System.currentTimeMillis());
            
            HttpStatus responseStatus = vaultGemmaAvailable ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            logger.debug("VaultGemma health check - Status: {}, Primary: {}", 
                        healthStatus, availabilityStatus.get("primaryMethod"));
            
            return ResponseEntity.status(responseStatus).body(health);
            
        } catch (Exception e) {
            logger.error("Error checking VaultGemma health: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "status", "ERROR",
                        "error", "Health check failed: " + e.getMessage(),
                        "timestamp", System.currentTimeMillis()
                    ));
        }
    }
    
    /**
     * Secure document classification endpoint using VaultGemma integration
     * This endpoint provides enhanced security with differential privacy and secure storage
     */
    @PostMapping("/classifyDocuments")
    public ResponseEntity<?> classifyDocuments(@RequestParam("text") String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Text parameter is required"));
            }
            
            // Generate userId automatically for internal privacy tracking
            String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            
            logger.info("Processing secure document classification");
            
            // Check if VaultGemma is available
            if (!vaultGemmaIntegration.isVaultGemmaAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "VaultGemma service is currently unavailable"));
            }
            
            // Check privacy budget before processing using Google VaultGemma service
            if (!googleVaultGemmaService.hasPrivacyBudget(userId)) {
                Map<String, Object> privacyStatus = googleVaultGemmaService.getPrivacyBudgetStatus(userId);
                logger.warn("Privacy budget exceeded");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                        "error", "Privacy budget exceeded",
                        "message", "You have exceeded your privacy budget. Please try again later or contact support."
                    ));
            }
            
            // Perform secure classification using Google VaultGemma-1b
            String classification = googleVaultGemmaService.classifyDocumentSecurely(text, userId);
            
            // Get updated privacy budget status from Google VaultGemma service (but don't expose userId)
            Map<String, Object> updatedPrivacyStatus = googleVaultGemmaService.getPrivacyBudgetStatus(userId);
            // Remove userId from privacy status for response
            updatedPrivacyStatus.remove("userId");
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("classification", classification);
            response.put("timestamp", System.currentTimeMillis());
            response.put("vaultGemmaEnabled", vaultGemmaEnabled);
            response.put("privacyProtected", true);
            response.put("differentialPrivacy", true);
            response.put("privacyBudgetStatus", updatedPrivacyStatus);
            
            // Add model availability status
            Map<String, Object> modelStatus = vaultGemmaService.getModelAvailabilityStatus();
            response.put("modelStatus", modelStatus);
            response.put("primaryMethod", modelStatus.get("primaryMethod"));
            
            // Add Google VaultGemma service status
            Map<String, Object> googleVaultGemmaStatus = googleVaultGemmaService.getServiceStatus();
            response.put("googleVaultGemmaStatus", googleVaultGemmaStatus);
            
            logger.info("Secure document classification completed: {}", classification);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Secure document classification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Secure classification failed",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
        }
    }
    
    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("vaultGemmaEnabled", vaultGemmaEnabled);
        health.put("localModelAvailable", localVaultGemmaService.isModelAvailable());
        
        return ResponseEntity.ok(health);
    }
}
