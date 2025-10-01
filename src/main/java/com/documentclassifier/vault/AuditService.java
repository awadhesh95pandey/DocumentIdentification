package com.documentclassifier.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Value("${audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${audit.log-file:logs/audit.log}")
    private String auditLogFile;
    
    private final ObjectMapper objectMapper;
    
    public AuditService() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Log document processing event
     */
    public void logDocumentProcessing(String userId, String filename, String documentType, 
                                    String operation, boolean success, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        event.setEventType("DOCUMENT_PROCESSING");
        event.setUserId(userId != null ? userId : "anonymous");
        event.setOperation(operation);
        event.setSuccess(success);
        event.setResourceType("DOCUMENT");
        event.setResourceId(filename);
        event.setDetails(details);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentType", documentType);
        metadata.put("filename", filename);
        event.setMetadata(metadata);
        
        writeAuditEvent(event);
    }
    
    /**
     * Log authentication event
     */
    public void logAuthentication(String userId, String operation, boolean success, 
                                String ipAddress, String userAgent) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        event.setEventType("AUTHENTICATION");
        event.setUserId(userId != null ? userId : "anonymous");
        event.setOperation(operation);
        event.setSuccess(success);
        event.setResourceType("USER_SESSION");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ipAddress", ipAddress);
        metadata.put("userAgent", userAgent);
        event.setMetadata(metadata);
        
        writeAuditEvent(event);
    }
    
    /**
     * Log data access event
     */
    public void logDataAccess(String userId, String resourceId, String operation, 
                            boolean success, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        event.setEventType("DATA_ACCESS");
        event.setUserId(userId != null ? userId : "anonymous");
        event.setOperation(operation);
        event.setSuccess(success);
        event.setResourceType("SECURE_VAULT");
        event.setResourceId(resourceId);
        event.setDetails(details);
        
        writeAuditEvent(event);
    }
    
    /**
     * Log privacy-related event
     */
    public void logPrivacyEvent(String userId, String operation, String privacyLevel, 
                              String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        event.setEventType("PRIVACY_OPERATION");
        event.setUserId(userId != null ? userId : "anonymous");
        event.setOperation(operation);
        event.setSuccess(true);
        event.setResourceType("PRIVACY_SYSTEM");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("privacyLevel", privacyLevel);
        metadata.put("differentialPrivacy", true);
        event.setMetadata(metadata);
        event.setDetails(details);
        
        writeAuditEvent(event);
    }
    
    /**
     * Log security event
     */
    public void logSecurityEvent(String userId, String operation, String severity, 
                               boolean success, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        event.setEventType("SECURITY_EVENT");
        event.setUserId(userId != null ? userId : "anonymous");
        event.setOperation(operation);
        event.setSuccess(success);
        event.setResourceType("SECURITY_SYSTEM");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("severity", severity);
        event.setMetadata(metadata);
        event.setDetails(details);
        
        writeAuditEvent(event);
    }
    
    /**
     * Write audit event to log file
     */
    private void writeAuditEvent(AuditEvent event) {
        try {
            // Ensure log directory exists
            Path logPath = Paths.get(auditLogFile);
            Files.createDirectories(logPath.getParent());
            
            // Convert event to JSON and write to file
            String jsonEvent = objectMapper.writeValueAsString(event);
            
            try (FileWriter writer = new FileWriter(auditLogFile, true)) {
                writer.write(jsonEvent + System.lineSeparator());
                writer.flush();
            }
            
            // Also log to application logger for immediate visibility
            logger.info("AUDIT: {} - {} - {} - {}", 
                event.getEventType(), 
                event.getOperation(), 
                event.getUserId(), 
                event.isSuccess() ? "SUCCESS" : "FAILURE");
                
        } catch (IOException e) {
            logger.error("Failed to write audit event", e);
        }
    }
    
    /**
     * Audit event data structure
     */
    public static class AuditEvent {
        private String eventId;
        private String timestamp;
        private String eventType;
        private String userId;
        private String operation;
        private boolean success;
        private String resourceType;
        private String resourceId;
        private String details;
        private Map<String, Object> metadata;
        
        // Getters and setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
