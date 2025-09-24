package org.example.model;

import java.util.Map;

/**
 * Model class representing extracted document information.
 * Contains document content, metadata, and file information.
 */
public class DocumentInfo {
    
    private String fileName;
    private String filePath;
    private String mimeType;
    private long fileSize;
    private String content;
    private Map<String, String> metadata;
    private String language;
    private int pageCount;
    private boolean hasImages;
    private boolean hasLinks;
    
    // Classification information
    private ClassificationResult classification;

    // Constructors
    public DocumentInfo() {}

    public DocumentInfo(String fileName, String filePath, String mimeType, long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public boolean isHasImages() {
        return hasImages;
    }

    public void setHasImages(boolean hasImages) {
        this.hasImages = hasImages;
    }

    public boolean isHasLinks() {
        return hasLinks;
    }

    public void setHasLinks(boolean hasLinks) {
        this.hasLinks = hasLinks;
    }

    public ClassificationResult getClassification() {
        return classification;
    }

    public void setClassification(ClassificationResult classification) {
        this.classification = classification;
    }

    /**
     * Get a summary of the document content (first 200 characters)
     */
    public String getContentSummary() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    /**
     * Get human-readable file size
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}
