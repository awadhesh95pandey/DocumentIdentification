package org.example.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response model for file upload operations.
 * Contains information about the uploaded file and processing status.
 */
public class FileUploadResponse {
    
    private String jobId;
    private String fileName;
    private long fileSize;
    private String status;
    private String message;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;
    
    private List<String> errors;
    private ExtractionResult extractionResult;

    // Constructors
    public FileUploadResponse() {}

    public FileUploadResponse(String jobId, String fileName, long fileSize, String status) {
        this.jobId = jobId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
        this.uploadTime = LocalDateTime.now();
    }

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public ExtractionResult getExtractionResult() {
        return extractionResult;
    }

    public void setExtractionResult(ExtractionResult extractionResult) {
        this.extractionResult = extractionResult;
    }

    // Status constants
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
