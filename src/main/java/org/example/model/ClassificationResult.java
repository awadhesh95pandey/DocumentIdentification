package org.example.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Represents the result of document classification using AI.
 * Contains the identified document type, confidence score, and metadata.
 */
public class ClassificationResult {
    
    private DocumentType documentType;
    private double confidence;
    private String reasoning;
    private String aiModel;
    private boolean isImageBased;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime classifiedAt;
    
    private long processingTimeMs;
    private String errorMessage;

    public ClassificationResult() {
        this.classifiedAt = LocalDateTime.now();
    }

    public ClassificationResult(DocumentType documentType, double confidence) {
        this();
        this.documentType = documentType;
        this.confidence = confidence;
    }

    public ClassificationResult(DocumentType documentType, double confidence, String reasoning) {
        this(documentType, confidence);
        this.reasoning = reasoning;
    }

    /**
     * Creates a successful classification result.
     */
    public static ClassificationResult success(DocumentType documentType, double confidence, String reasoning, String aiModel, boolean isImageBased, long processingTimeMs) {
        ClassificationResult result = new ClassificationResult(documentType, confidence, reasoning);
        result.setAiModel(aiModel);
        result.setImageBased(isImageBased);
        result.setProcessingTimeMs(processingTimeMs);
        return result;
    }

    /**
     * Creates a failed classification result.
     */
    public static ClassificationResult failure(String errorMessage, long processingTimeMs) {
        ClassificationResult result = new ClassificationResult();
        result.setDocumentType(DocumentType.UNKNOWN);
        result.setConfidence(0.0);
        result.setErrorMessage(errorMessage);
        result.setProcessingTimeMs(processingTimeMs);
        return result;
    }

    /**
     * Creates a fallback result when classification is disabled or unavailable.
     */
    public static ClassificationResult disabled() {
        ClassificationResult result = new ClassificationResult();
        result.setDocumentType(DocumentType.OTHER);
        result.setConfidence(0.0);
        result.setReasoning("Classification is disabled or unavailable");
        result.setProcessingTimeMs(0);
        return result;
    }

    /**
     * Checks if the classification was successful.
     */
    public boolean isSuccessful() {
        return errorMessage == null && documentType != DocumentType.UNKNOWN;
    }

    /**
     * Checks if the classification has high confidence (>= 0.8).
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Checks if the classification has medium confidence (>= 0.5).
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.5;
    }

    // Getters and Setters
    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = Math.max(0.0, Math.min(1.0, confidence)); // Clamp between 0 and 1
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public boolean isImageBased() {
        return isImageBased;
    }

    public void setImageBased(boolean imageBased) {
        isImageBased = imageBased;
    }

    public LocalDateTime getClassifiedAt() {
        return classifiedAt;
    }

    public void setClassifiedAt(LocalDateTime classifiedAt) {
        this.classifiedAt = classifiedAt;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "ClassificationResult{" +
                "documentType=" + documentType +
                ", confidence=" + confidence +
                ", reasoning='" + reasoning + '\'' +
                ", aiModel='" + aiModel + '\'' +
                ", isImageBased=" + isImageBased +
                ", classifiedAt=" + classifiedAt +
                ", processingTimeMs=" + processingTimeMs +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
