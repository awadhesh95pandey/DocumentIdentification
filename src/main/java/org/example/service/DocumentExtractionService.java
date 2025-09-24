package org.example.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.example.model.ClassificationResult;
import org.example.model.DocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for extracting content and metadata from various document formats using Apache Tika.
 * Supports PDF, Word, Excel, PowerPoint, text files, and many other formats.
 */
@Service
public class DocumentExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentExtractionService.class);

    @Value("${app.allowed-file-types:pdf,doc,docx,xls,xlsx,ppt,pptx,txt,rtf,odt,ods,odp}")
    private String allowedFileTypes;

    private final Tika tika;
    private final AutoDetectParser parser;
    private Set<String> allowedExtensions;

    @Autowired(required = false)
    private DocumentClassificationService classificationService;

    @Autowired(required = false)
    private ImageProcessingService imageProcessingService;

    public DocumentExtractionService() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    /**
     * Extract content and metadata from a single document file.
     *
     * @param filePath Path to the document file
     * @return DocumentInfo containing extracted content and metadata
     */
    public DocumentInfo extractDocument(String filePath) {
        File file = new File(filePath);
        logger.debug("Extracting document: {}", filePath);

        DocumentInfo docInfo = new DocumentInfo(
            file.getName(),
            filePath,
            null,
            file.length()
        );

        try {
            // Validate file type
            if (!isAllowedFileType(file)) {
                logger.warn("File type not allowed: {}", file.getName());
                return docInfo;
            }

            // Detect MIME type
            String mimeType = tika.detect(file);
            docInfo.setMimeType(mimeType);

            // Extract content and metadata
            extractContentAndMetadata(file, docInfo);

            // Classify document using AI
            classifyDocument(file, docInfo);

            logger.debug("Successfully extracted document: {} (content length: {})", 
                file.getName(), docInfo.getContent() != null ? docInfo.getContent().length() : 0);

        } catch (Exception e) {
            logger.error("Failed to extract document: {}", filePath, e);
            docInfo.setContent("Error extracting content: " + e.getMessage());
        }

        return docInfo;
    }

    /**
     * Extract content and metadata from multiple document files.
     *
     * @param filePaths List of file paths to process
     * @return List of DocumentInfo objects
     */
    public List<DocumentInfo> extractDocuments(List<String> filePaths) {
        logger.info("Extracting {} documents", filePaths.size());

        return filePaths.parallelStream()
            .map(this::extractDocument)
            .collect(Collectors.toList());
    }

    /**
     * Check if a file type is allowed for processing.
     *
     * @param file The file to check
     * @return true if the file type is allowed
     */
    public boolean isAllowedFileType(File file) {
        if (allowedExtensions == null) {
            initializeAllowedExtensions();
        }

        String fileName = file.getName().toLowerCase();
        String extension = getFileExtension(fileName);
        
        return allowedExtensions.contains(extension);
    }

    /**
     * Get supported file extensions.
     *
     * @return Set of supported file extensions
     */
    public Set<String> getSupportedExtensions() {
        if (allowedExtensions == null) {
            initializeAllowedExtensions();
        }
        return new HashSet<>(allowedExtensions);
    }

    private void extractContentAndMetadata(File file, DocumentInfo docInfo) throws IOException, SAXException, TikaException {
        // Use BodyContentHandler with a limit to prevent memory issues
        BodyContentHandler handler = new BodyContentHandler(10 * 1024 * 1024); // 10MB limit
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();

        try (FileInputStream inputStream = new FileInputStream(file)) {
            parser.parse(inputStream, handler, metadata, parseContext);

            // Set extracted content
            String content = handler.toString().trim();
            
            // For image files, if no text content is extracted, provide a placeholder
            if (content.isEmpty() && isImageFile(file.getName())) {
                content = String.format("Image file: %s (MIME: %s)", file.getName(), docInfo.getMimeType());
                logger.debug("Image file detected with no text content: {}", file.getName());
            }
            
            docInfo.setContent(content);

            // Extract and set metadata
            Map<String, String> metadataMap = extractMetadata(metadata);
            docInfo.setMetadata(metadataMap);

            // Set additional properties from metadata
            setAdditionalProperties(docInfo, metadata, content);
        }
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> metadataMap = new HashMap<>();

        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.trim().isEmpty()) {
                metadataMap.put(name, value);
            }
        }

        return metadataMap;
    }

    private void setAdditionalProperties(DocumentInfo docInfo, Metadata metadata, String content) {
        // Set language if detected
        String language = metadata.get("language");
        if (language != null) {
            docInfo.setLanguage(language);
        }

        // Set page count if available
        String pageCount = metadata.get("xmpTPg:NPages");
        if (pageCount == null) {
            pageCount = metadata.get("meta:page-count");
        }
        if (pageCount != null) {
            try {
                docInfo.setPageCount(Integer.parseInt(pageCount));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse page count: {}", pageCount);
            }
        } else if (isImageFile(docInfo.getFileName())) {
            // For image files, set page count to 1
            docInfo.setPageCount(1);
        }

        // Check for images (basic heuristic)
        docInfo.setHasImages(content.contains("image") || content.contains("picture") || 
                            metadata.get("hasImages") != null || isImageFile(docInfo.getFileName()));

        // Check for links (basic heuristic)
        docInfo.setHasLinks(content.contains("http://") || content.contains("https://") || 
                           content.contains("www.") || content.contains("@"));
    }

    private void initializeAllowedExtensions() {
        allowedExtensions = Arrays.stream(allowedFileTypes.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        
        logger.info("Initialized allowed file extensions: {}", allowedExtensions);
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Check if a file is an image based on its extension.
     */
    private boolean isImageFile(String fileName) {
        String extension = getFileExtension(fileName);
        return extension.matches("jpg|jpeg|png|gif|bmp|tiff|tif|webp");
    }

    /**
     * Get document statistics for a list of extracted documents.
     *
     * @param documents List of DocumentInfo objects
     * @return Map containing statistics
     */
    public Map<String, Object> getDocumentStatistics(List<DocumentInfo> documents) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDocuments", documents.size());
        stats.put("totalSize", documents.stream().mapToLong(DocumentInfo::getFileSize).sum());
        
        // Count by file type
        Map<String, Long> typeCount = documents.stream()
            .collect(Collectors.groupingBy(
                doc -> getFileExtension(doc.getFileName()),
                Collectors.counting()
            ));
        stats.put("fileTypes", typeCount);
        
        // Count documents with content
        long documentsWithContent = documents.stream()
            .filter(doc -> doc.getContent() != null && !doc.getContent().trim().isEmpty())
            .count();
        stats.put("documentsWithContent", documentsWithContent);
        
        // Average content length
        double avgContentLength = documents.stream()
            .filter(doc -> doc.getContent() != null)
            .mapToInt(doc -> doc.getContent().length())
            .average()
            .orElse(0.0);
        stats.put("averageContentLength", Math.round(avgContentLength));
        
        return stats;
    }

    /**
     * Classifies a document using AI-powered classification service.
     *
     * @param file The document file to classify
     * @param docInfo The DocumentInfo object to update with classification results
     */
    private void classifyDocument(File file, DocumentInfo docInfo) {
        if (classificationService == null) {
            logger.debug("Classification service not available, skipping classification for: {}", file.getName());
            docInfo.setClassification(ClassificationResult.disabled());
            return;
        }

        try {
            ClassificationResult result;

            // Check if it's an image file
            if (imageProcessingService != null && imageProcessingService.isImageFile(file.getName())) {
                logger.debug("Classifying image document: {}", file.getName());
                result = classificationService.classifyImageDocument(file);
            } else if (docInfo.getContent() != null && !docInfo.getContent().trim().isEmpty()) {
                // Classify based on text content
                logger.debug("Classifying text document: {}", file.getName());
                result = classificationService.classifyTextDocument(docInfo.getContent(), file.getName());
            } else {
                // Fallback to filename-based classification
                logger.debug("Using filename-based classification for: {}", file.getName());
                result = classificationService.classifyByFilename(file.getName());
            }

            docInfo.setClassification(result);
            
            if (result.isSuccessful()) {
                logger.info("Document classified: {} -> {} (confidence: {:.2f})", 
                           file.getName(), result.getDocumentType(), result.getConfidence());
            } else {
                logger.warn("Classification failed for {}: {}", file.getName(), result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Error during document classification for {}: {}", file.getName(), e.getMessage());
            docInfo.setClassification(ClassificationResult.failure("Classification error: " + e.getMessage(), 0));
        }
    }
}
