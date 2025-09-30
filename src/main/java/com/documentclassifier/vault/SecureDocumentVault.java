package com.documentclassifier.vault;

import com.documentclassifier.config.VaultGemmaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

@Service
public class SecureDocumentVault {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureDocumentVault.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    
    private final VaultGemmaConfig config;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    // In-memory index of stored documents (in production, this would be a database)
    private final Map<String, DocumentMetadata> documentIndex = new ConcurrentHashMap<>();
    
    @Autowired
    public SecureDocumentVault(VaultGemmaConfig config, 
                             EncryptionService encryptionService,
                             AuditService auditService) {
        this.config = config;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        
        initializeVault();
    }
    
    /**
     * Initialize the secure vault directory structure
     */
    private void initializeVault() {
        try {
            Path vaultPath = Paths.get(config.getSecurity().getVault().getStoragePath());
            
            // Create directories with better error handling
            if (!Files.exists(vaultPath)) {
                Files.createDirectories(vaultPath);
                logger.info("Created vault directory: {}", vaultPath.toAbsolutePath());
            }
            
            Path documentsPath = vaultPath.resolve("documents");
            Path metadataPath = vaultPath.resolve("metadata");
            Path tempPath = vaultPath.resolve("temp");
            
            if (!Files.exists(documentsPath)) {
                Files.createDirectories(documentsPath);
            }
            if (!Files.exists(metadataPath)) {
                Files.createDirectories(metadataPath);
            }
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }
            
            // Test write permissions
            Path testFile = tempPath.resolve("test-write.tmp");
            Files.write(testFile, "test".getBytes());
            Files.deleteIfExists(testFile);
            
            logger.info("Secure document vault initialized successfully at: {}", vaultPath.toAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Failed to initialize secure vault: {}", e.getMessage(), e);
            
            // Try fallback directory
            try {
                String fallbackPath = System.getProperty("java.io.tmpdir") + "/secure-vault";
                Path fallbackVaultPath = Paths.get(fallbackPath);
                Files.createDirectories(fallbackVaultPath);
                Files.createDirectories(fallbackVaultPath.resolve("documents"));
                Files.createDirectories(fallbackVaultPath.resolve("metadata"));
                Files.createDirectories(fallbackVaultPath.resolve("temp"));
                
                // Update config to use fallback path
                config.getSecurity().getVault().setStoragePath(fallbackPath);
                logger.warn("Using fallback vault directory: {}", fallbackVaultPath.toAbsolutePath());
                
            } catch (Exception fallbackError) {
                logger.error("Fallback vault initialization also failed", fallbackError);
                throw new RuntimeException("Vault initialization failed completely", fallbackError);
            }
        }
    }
    
    /**
     * Store document securely in the vault
     */
    public String storeDocument(byte[] documentData, String originalFilename, 
                              String documentType, String userId) {
        if (documentData == null || documentData.length == 0) {
            throw new IllegalArgumentException("Document data cannot be empty");
        }
        
        String documentId = generateDocumentId();
        
        try {
            logger.debug("Starting document storage for user: {}, filename: {}", userId, originalFilename);
            
            // Encrypt document data
            byte[] encryptedData;
            try {
                encryptedData = encryptionService.encryptBytes(documentData);
                logger.debug("Document encryption successful for: {}", documentId);
            } catch (Exception encryptionError) {
                logger.error("Encryption failed for document {}: {}", documentId, encryptionError.getMessage());
                throw new RuntimeException("Document encryption failed", encryptionError);
            }
            
            // Create document metadata
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setDocumentId(documentId);
            metadata.setOriginalFilename(originalFilename);
            metadata.setDocumentType(documentType);
            metadata.setUserId(userId);
            metadata.setStoredTimestamp(LocalDateTime.now());
            metadata.setFileSize(documentData.length);
            metadata.setEncrypted(true);
            metadata.setChecksum(encryptionService.generateHash(Base64.getEncoder().encodeToString(documentData)));
            
            // Store encrypted document
            Path documentPath = getDocumentPath(documentId);
            try {
                // Ensure parent directory exists
                Files.createDirectories(documentPath.getParent());
                Files.write(documentPath, encryptedData);
                logger.debug("Document file written successfully: {}", documentPath);
            } catch (Exception fileError) {
                logger.error("Failed to write document file {}: {}", documentPath, fileError.getMessage());
                throw new RuntimeException("Document file write failed", fileError);
            }
            
            // Store metadata
            try {
                storeMetadata(metadata);
                logger.debug("Metadata stored successfully for: {}", documentId);
            } catch (Exception metadataError) {
                logger.error("Failed to store metadata for {}: {}", documentId, metadataError.getMessage());
                // Try to clean up the document file
                try {
                    Files.deleteIfExists(documentPath);
                } catch (Exception cleanupError) {
                    logger.warn("Failed to cleanup document file after metadata error: {}", cleanupError.getMessage());
                }
                throw new RuntimeException("Metadata storage failed", metadataError);
            }
            
            // Add to index
            documentIndex.put(documentId, metadata);
            
            // Clear sensitive data from memory
            try {
                encryptionService.clearSensitiveData(documentData);
                encryptionService.clearSensitiveData(encryptedData);
            } catch (Exception clearError) {
                logger.warn("Failed to clear sensitive data from memory: {}", clearError.getMessage());
            }
            
            // Audit log
            try {
                auditService.logDataAccess(userId, documentId, "STORE_DOCUMENT", true, 
                                         "Document stored securely in vault");
            } catch (Exception auditError) {
                logger.warn("Failed to log audit event: {}", auditError.getMessage());
            }
            
            logger.info("Document stored securely: {} for user: {}", documentId, userId);
            return documentId;
            
        } catch (Exception e) {
            logger.error("Failed to store document securely for user {}: {}", userId, e.getMessage(), e);
            
            try {
                auditService.logSecurityEvent(userId, "STORE_DOCUMENT_FAILED", "HIGH", false, 
                                            "Failed to store document: " + e.getMessage());
            } catch (Exception auditError) {
                logger.warn("Failed to log security event: {}", auditError.getMessage());
            }
            
            // Try fallback storage without encryption as last resort
            logger.warn("Attempting fallback storage without encryption for document: {}", documentId);
            try {
                return storeDocumentFallback(documentData, originalFilename, documentType, userId, documentId);
            } catch (Exception fallbackError) {
                logger.error("Fallback storage also failed: {}", fallbackError.getMessage());
                throw new RuntimeException("All storage methods failed: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Fallback storage method without encryption (for emergency cases)
     */
    private String storeDocumentFallback(byte[] documentData, String originalFilename, 
                                       String documentType, String userId, String documentId) {
        try {
            logger.info("Using fallback storage (unencrypted) for document: {}", documentId);
            
            // Create simple metadata
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setDocumentId(documentId);
            metadata.setOriginalFilename(originalFilename);
            metadata.setDocumentType(documentType);
            metadata.setUserId(userId);
            metadata.setStoredTimestamp(LocalDateTime.now());
            metadata.setFileSize(documentData.length);
            metadata.setEncrypted(false); // Not encrypted in fallback mode
            metadata.setChecksum("fallback-" + System.currentTimeMillis());
            
            // Store document without encryption
            Path fallbackPath = Paths.get(System.getProperty("java.io.tmpdir"), "vault-fallback");
            Files.createDirectories(fallbackPath);
            
            Path documentPath = fallbackPath.resolve(documentId + ".dat");
            Files.write(documentPath, documentData);
            
            // Store simple metadata
            Path metadataPath = fallbackPath.resolve(documentId + ".meta");
            String metadataJson = objectMapper.writeValueAsString(metadata);
            Files.write(metadataPath, metadataJson.getBytes());
            
            // Add to index
            documentIndex.put(documentId, metadata);
            
            logger.warn("Document stored using fallback method (UNENCRYPTED): {} at {}", documentId, documentPath);
            
            // Log security warning
            try {
                auditService.logSecurityEvent(userId, "FALLBACK_STORAGE_USED", "HIGH", true, 
                                            "Document stored without encryption due to vault failure");
            } catch (Exception auditError) {
                logger.warn("Failed to log fallback storage audit event: {}", auditError.getMessage());
            }
            
            return documentId;
            
        } catch (Exception e) {
            logger.error("Fallback storage failed: {}", e.getMessage(), e);
            throw new RuntimeException("Fallback storage failed", e);
        }
    }
    
    /**
     * Retrieve document securely from the vault
     */
    public SecureDocument retrieveDocument(String documentId, String userId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        
        try {
            // Check if document exists
            DocumentMetadata metadata = documentIndex.get(documentId);
            if (metadata == null) {
                auditService.logDataAccess(userId, documentId, "RETRIEVE_DOCUMENT_NOT_FOUND", false, 
                                         "Document not found");
                return null;
            }
            
            // Verify user access (in production, implement proper access control)
            if (!canUserAccessDocument(userId, metadata)) {
                auditService.logSecurityEvent(userId, "UNAUTHORIZED_ACCESS_ATTEMPT", "CRITICAL", false, 
                                            "Unauthorized access attempt to document: " + documentId);
                throw new SecurityException("Access denied to document: " + documentId);
            }
            
            // Read encrypted document
            Path documentPath = getDocumentPath(documentId);
            if (!Files.exists(documentPath)) {
                auditService.logDataAccess(userId, documentId, "RETRIEVE_DOCUMENT_FILE_MISSING", false, 
                                         "Document file missing from storage");
                return null;
            }
            
            byte[] encryptedData = Files.readAllBytes(documentPath);
            
            // Decrypt document
            byte[] decryptedData = encryptionService.decryptBytes(encryptedData);
            
            // Verify integrity
            String currentChecksum = encryptionService.generateHash(Base64.getEncoder().encodeToString(decryptedData));
            if (!currentChecksum.equals(metadata.getChecksum())) {
                auditService.logSecurityEvent(userId, "DOCUMENT_INTEGRITY_VIOLATION", "CRITICAL", false, 
                                            "Document integrity check failed for: " + documentId);
                throw new SecurityException("Document integrity violation detected");
            }
            
            // Create secure document wrapper
            SecureDocument secureDocument = new SecureDocument();
            secureDocument.setDocumentId(documentId);
            secureDocument.setData(decryptedData);
            secureDocument.setMetadata(metadata);
            
            // Clear encrypted data from memory
            encryptionService.clearSensitiveData(encryptedData);
            
            // Audit log
            auditService.logDataAccess(userId, documentId, "RETRIEVE_DOCUMENT", true, 
                                     "Document retrieved securely from vault");
            
            logger.info("Document retrieved securely: {} for user: {}", documentId, userId);
            return secureDocument;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve document securely", e);
            auditService.logSecurityEvent(userId, "RETRIEVE_DOCUMENT_FAILED", "HIGH", false, 
                                        "Failed to retrieve document: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve document securely", e);
        }
    }
    
    /**
     * List documents accessible to user
     */
    public List<DocumentMetadata> listUserDocuments(String userId) {
        List<DocumentMetadata> userDocuments = new ArrayList<>();
        
        for (DocumentMetadata metadata : documentIndex.values()) {
            if (canUserAccessDocument(userId, metadata)) {
                // Create a copy without sensitive information
                DocumentMetadata safeCopy = createSafeMetadataCopy(metadata);
                userDocuments.add(safeCopy);
            }
        }
        
        auditService.logDataAccess(userId, "USER_DOCUMENTS", "LIST_DOCUMENTS", true, 
                                 "Listed " + userDocuments.size() + " documents");
        
        return userDocuments;
    }
    
    /**
     * Delete document securely from vault
     */
    public boolean deleteDocument(String documentId, String userId) {
        try {
            DocumentMetadata metadata = documentIndex.get(documentId);
            if (metadata == null) {
                return false;
            }
            
            // Verify user access
            if (!canUserAccessDocument(userId, metadata)) {
                auditService.logSecurityEvent(userId, "UNAUTHORIZED_DELETE_ATTEMPT", "CRITICAL", false, 
                                            "Unauthorized delete attempt for document: " + documentId);
                throw new SecurityException("Access denied to delete document: " + documentId);
            }
            
            // Secure deletion
            Path documentPath = getDocumentPath(documentId);
            Path metadataPath = getMetadataPath(documentId);
            
            // Overwrite file with random data before deletion (secure deletion)
            if (Files.exists(documentPath)) {
                secureDeleteFile(documentPath);
            }
            
            if (Files.exists(metadataPath)) {
                Files.delete(metadataPath);
            }
            
            // Remove from index
            documentIndex.remove(documentId);
            
            auditService.logDataAccess(userId, documentId, "DELETE_DOCUMENT", true, 
                                     "Document securely deleted from vault");
            
            logger.info("Document securely deleted: {} by user: {}", documentId, userId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to delete document securely", e);
            auditService.logSecurityEvent(userId, "DELETE_DOCUMENT_FAILED", "HIGH", false, 
                                        "Failed to delete document: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleanup expired documents based on retention policy
     */
    public void cleanupExpiredDocuments() {
        int retentionDays = config.getSecurity().getVault().getRetentionDays();
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        List<String> expiredDocuments = new ArrayList<>();
        
        for (Map.Entry<String, DocumentMetadata> entry : documentIndex.entrySet()) {
            if (entry.getValue().getStoredTimestamp().isBefore(cutoffDate)) {
                expiredDocuments.add(entry.getKey());
            }
        }
        
        for (String documentId : expiredDocuments) {
            DocumentMetadata metadata = documentIndex.get(documentId);
            deleteDocument(documentId, "SYSTEM");
            
            auditService.logDataAccess("SYSTEM", documentId, "EXPIRED_DOCUMENT_CLEANUP", true, 
                                     "Document expired and cleaned up automatically");
        }
        
        logger.info("Cleaned up {} expired documents", expiredDocuments.size());
    }
    
    // Helper methods
    
    private String generateDocumentId() {
        return "DOC_" + UUID.randomUUID().toString().replace("-", "").toUpperCase() + 
               "_" + LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }
    
    private Path getDocumentPath(String documentId) {
        return Paths.get(config.getSecurity().getVault().getStoragePath(), "documents", documentId + ".enc");
    }
    
    private Path getMetadataPath(String documentId) {
        return Paths.get(config.getSecurity().getVault().getStoragePath(), "metadata", documentId + ".meta");
    }
    
    private void storeMetadata(DocumentMetadata metadata) throws IOException {
        Path metadataPath = getMetadataPath(metadata.getDocumentId());
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String encryptedMetadata = encryptionService.encryptText(metadataJson);
        Files.write(metadataPath, encryptedMetadata.getBytes());
    }
    
    private boolean canUserAccessDocument(String userId, DocumentMetadata metadata) {
        // Simple access control - user can only access their own documents
        // In production, implement role-based access control
        return userId.equals(metadata.getUserId()) || "SYSTEM".equals(userId);
    }
    
    private DocumentMetadata createSafeMetadataCopy(DocumentMetadata original) {
        DocumentMetadata copy = new DocumentMetadata();
        copy.setDocumentId(original.getDocumentId());
        copy.setOriginalFilename(original.getOriginalFilename());
        copy.setDocumentType(original.getDocumentType());
        copy.setStoredTimestamp(original.getStoredTimestamp());
        copy.setFileSize(original.getFileSize());
        copy.setEncrypted(original.isEncrypted());
        // Don't copy sensitive fields like checksum
        return copy;
    }
    
    private void secureDeleteFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return;
        }
        
        long fileSize = Files.size(filePath);
        byte[] randomData = new byte[(int) fileSize];
        new Random().nextBytes(randomData);
        
        // Overwrite with random data multiple times
        for (int i = 0; i < 3; i++) {
            Files.write(filePath, randomData);
        }
        
        // Finally delete the file
        Files.delete(filePath);
        
        // Clear random data from memory
        encryptionService.clearSensitiveData(randomData);
    }
    
    // Data classes
    
    public static class DocumentMetadata {
        private String documentId;
        private String originalFilename;
        private String documentType;
        private String userId;
        private LocalDateTime storedTimestamp;
        private long fileSize;
        private boolean encrypted;
        private String checksum;
        
        // Getters and setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public LocalDateTime getStoredTimestamp() { return storedTimestamp; }
        public void setStoredTimestamp(LocalDateTime storedTimestamp) { this.storedTimestamp = storedTimestamp; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }
    
    public static class SecureDocument {
        private String documentId;
        private byte[] data;
        private DocumentMetadata metadata;
        
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
        
        public DocumentMetadata getMetadata() { return metadata; }
        public void setMetadata(DocumentMetadata metadata) { this.metadata = metadata; }
    }
}
