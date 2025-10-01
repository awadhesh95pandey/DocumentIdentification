package com.documentclassifier.controller;

import com.documentclassifier.service.LocalVaultGemmaService;
import com.documentclassifier.service.VaultGemmaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentClassifierController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentClassifierController.class);
    
    private final LocalVaultGemmaService localVaultGemmaService;
    private final VaultGemmaService vaultGemmaService;
    
    @Value("${vaultgemma.enabled:true}")
    private boolean vaultGemmaEnabled;
    
    @Autowired
    public DocumentClassifierController(
            LocalVaultGemmaService localVaultGemmaService,
            VaultGemmaService vaultGemmaService) {
        this.localVaultGemmaService = localVaultGemmaService;
        this.vaultGemmaService = vaultGemmaService;
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
     * File upload classification endpoint
     */
    @PostMapping("/classify-file")
    public ResponseEntity<?> classifyFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is required"));
            }
            
            // For simplicity, just extract filename as text (in real scenario, you'd use OCR)
            String extractedText = "Document: " + file.getOriginalFilename();
            
            String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Use VaultGemma service with three-tier fallback
            String classification = vaultGemmaService.classifyWithPrivacy(extractedText, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("filename", file.getOriginalFilename());
            response.put("classification", classification);
            response.put("vaultGemmaEnabled", vaultGemmaEnabled);
            response.put("localModelUsed", localVaultGemmaService.isModelAvailable());
            response.put("userId", userId);
            response.put("timestamp", System.currentTimeMillis());
            
            // Add privacy budget status
            Map<String, Object> privacyStatus = vaultGemmaService.getPrivacyBudgetStatus(userId);
            response.put("privacyBudgetStatus", privacyStatus);
            
            logger.info("File classified: {} -> {} for user: {}", 
                       file.getOriginalFilename(), classification, userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("File classification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "File classification failed: " + e.getMessage()));
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
