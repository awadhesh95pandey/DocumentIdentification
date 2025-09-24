package org.example.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * Model class representing the complete extraction result from a ZIP file.
 * Contains all extracted documents and processing statistics.
 */
public class ExtractionResult {
    
    private String jobId;
    private String zipFileName;
    private int totalFilesInZip;
    private int processedFiles;
    private int successfulExtractions;
    private int failedExtractions;
    private List<DocumentInfo> extractedDocuments;
    private List<String> errors;
    private List<String> warnings;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processingStartTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processingEndTime;
    
    private long processingTimeMs;

    // Constructors
    public ExtractionResult() {
        this.extractedDocuments = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.processingStartTime = LocalDateTime.now();
    }

    public ExtractionResult(String jobId, String zipFileName) {
        this();
        this.jobId = jobId;
        this.zipFileName = zipFileName;
    }

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getZipFileName() {
        return zipFileName;
    }

    public void setZipFileName(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    public int getTotalFilesInZip() {
        return totalFilesInZip;
    }

    public void setTotalFilesInZip(int totalFilesInZip) {
        this.totalFilesInZip = totalFilesInZip;
    }

    public int getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(int processedFiles) {
        this.processedFiles = processedFiles;
    }

    public int getSuccessfulExtractions() {
        return successfulExtractions;
    }

    public void setSuccessfulExtractions(int successfulExtractions) {
        this.successfulExtractions = successfulExtractions;
    }

    public int getFailedExtractions() {
        return failedExtractions;
    }

    public void setFailedExtractions(int failedExtractions) {
        this.failedExtractions = failedExtractions;
    }

    public List<DocumentInfo> getExtractedDocuments() {
        return extractedDocuments;
    }

    public void setExtractedDocuments(List<DocumentInfo> extractedDocuments) {
        this.extractedDocuments = extractedDocuments;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public LocalDateTime getProcessingStartTime() {
        return processingStartTime;
    }

    public void setProcessingStartTime(LocalDateTime processingStartTime) {
        this.processingStartTime = processingStartTime;
    }

    public LocalDateTime getProcessingEndTime() {
        return processingEndTime;
    }

    public void setProcessingEndTime(LocalDateTime processingEndTime) {
        this.processingEndTime = processingEndTime;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    // Utility methods
    public void addDocument(DocumentInfo document) {
        if (this.extractedDocuments == null) {
            this.extractedDocuments = new ArrayList<>();
        }
        this.extractedDocuments.add(document);
        this.successfulExtractions++;
    }

    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.failedExtractions++;
    }

    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }

    public void markProcessingComplete() {
        this.processingEndTime = LocalDateTime.now();
        if (this.processingStartTime != null) {
            this.processingTimeMs = java.time.Duration.between(
                this.processingStartTime, this.processingEndTime
            ).toMillis();
        }
    }

    public double getSuccessRate() {
        if (processedFiles == 0) {
            return 0.0;
        }
        return (double) successfulExtractions / processedFiles * 100.0;
    }

    public String getProcessingDuration() {
        if (processingTimeMs < 1000) {
            return processingTimeMs + " ms";
        } else if (processingTimeMs < 60000) {
            return String.format("%.1f seconds", processingTimeMs / 1000.0);
        } else {
            return String.format("%.1f minutes", processingTimeMs / 60000.0);
        }
    }
}
