package com.documentclassifier.controller;

import com.documentclassifier.dto.ClassificationResult;
import com.documentclassifier.service.DocumentClassificationService;
import com.documentclassifier.service.FileProcessingService;
import com.documentclassifier.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    
    @Autowired
    public DocumentClassifierController(
            FileProcessingService fileProcessingService,
            OcrService ocrService,
            DocumentClassificationService classificationService) {
        this.fileProcessingService = fileProcessingService;
        this.ocrService = ocrService;
        this.classificationService = classificationService;
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
        
        try {
            // Extract images from ZIP
            extractedFiles = fileProcessingService.extractImagesFromZip(file);
            logger.info("Extracted {} images from ZIP file", extractedFiles.size());
            
            // Process each image
            Map<String, String> results = new HashMap<>();
            
            for (File imageFile : extractedFiles) {
                String filename = imageFile.getName();
                
                try {
                    // Extract text using OCR
                    String extractedText = ocrService.extractTextFromImage(imageFile);
                    
                    if (extractedText.isEmpty()) {
                        results.put(filename, "None");
                        logger.warn("No text extracted from image: {}", filename);
                        continue;
                    }
                    
                    // Classify document type
                    String documentType = classificationService.classifyDocumentType(extractedText);
                    results.put(filename, documentType);
                    
                    logger.debug("Processed {}: {}", filename, documentType);
                    
                } catch (Exception e) {
                    logger.error("Error processing image {}: {}", filename, e.getMessage());
                    results.put(filename, "Error: " + e.getMessage());
                }
            }
            
            logger.info("Successfully processed {} images", results.size());
            return ResponseEntity.ok(results);
            
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "Document Classifier API",
                "version", "1.0.0"
        ));
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
