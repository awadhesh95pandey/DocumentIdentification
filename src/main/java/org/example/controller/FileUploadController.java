package org.example.controller;

import org.example.model.DocumentInfo;
import org.example.model.ExtractionResult;
import org.example.model.FileUploadResponse;
import org.example.service.DocumentClassificationService;
import org.example.service.DocumentExtractionService;
import org.example.service.ZipExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for handling file upload and document extraction operations.
 * Provides endpoints for uploading ZIP files, checking processing status, and retrieving results.
 */
@RestController
@RequestMapping("/")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private ZipExtractionService zipExtractionService;

    @Autowired(required = false)
    private DocumentClassificationService classificationService;

    @Autowired
    private DocumentExtractionService documentExtractionService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    // In-memory storage for job results (in production, use a database or cache)
    private final Map<String, FileUploadResponse> jobResults = new ConcurrentHashMap<>();

    /**
     * Upload a ZIP file and start document extraction process.
     *
     * @param file The ZIP file to upload
     * @return FileUploadResponse with job ID and status
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("Received file upload request: {}", file.getOriginalFilename());

        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("File is empty"));
            }

            if (!isZipFile(file)) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Only ZIP files are allowed"));
            }

            // Generate job ID
            String jobId = UUID.randomUUID().toString();

            // Save uploaded file
            File uploadedFile = saveUploadedFile(file, jobId);

            // Create initial response
            FileUploadResponse response = new FileUploadResponse(
                jobId, 
                file.getOriginalFilename(), 
                file.getSize(), 
                FileUploadResponse.STATUS_UPLOADED
            );
            response.setMessage("File uploaded successfully. Processing started.");

            // Store job result
            jobResults.put(jobId, response);

            // Start asynchronous processing
            processFileAsync(uploadedFile, jobId);

            logger.info("File upload successful. Job ID: {}", jobId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("File upload failed: " + e.getMessage()));
        }
    }

    /**
     * Get the status and results of a processing job.
     *
     * @param jobId The job identifier
     * @return FileUploadResponse with current status and results
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<FileUploadResponse> getJobStatus(@PathVariable String jobId) {
        logger.debug("Status request for job: {}", jobId);

        FileUploadResponse response = jobResults.get(jobId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get extraction results for a completed job.
     *
     * @param jobId The job identifier
     * @return ExtractionResult with detailed extraction information
     */
    @GetMapping("/results/{jobId}")
    public ResponseEntity<ExtractionResult> getJobResults(@PathVariable String jobId) {
        logger.debug("Results request for job: {}", jobId);

        FileUploadResponse response = jobResults.get(jobId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        if (!FileUploadResponse.STATUS_COMPLETED.equals(response.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(response.getExtractionResult());
    }

    /**
     * Get list of all jobs and their statuses.
     *
     * @return List of all job responses
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<FileUploadResponse>> getAllJobs() {
        List<FileUploadResponse> jobs = new ArrayList<>(jobResults.values());
        jobs.sort((a, b) -> b.getUploadTime().compareTo(a.getUploadTime())); // Sort by upload time, newest first
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get supported file types and system information.
     *
     * @return Map containing system information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("supportedExtensions", documentExtractionService.getSupportedExtensions());
        info.put("maxFileSize", "100MB");
        info.put("maxZipEntries", 1000);
        info.put("version", "1.1.0");
        info.put("description", "Document Extraction Service with AI Classification - Upload ZIP files to extract and classify documents");
        
        // Add classification service status
        if (classificationService != null) {
            info.put("classificationStatus", classificationService.getClassificationStatus());
            info.put("aiClassificationEnabled", true);
        } else {
            info.put("classificationStatus", "Classification service not available");
            info.put("aiClassificationEnabled", false);
        }
        
        return ResponseEntity.ok(info);
    }

    /**
     * Health check endpoint.
     *
     * @return Simple health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", new Date().toString());
        return ResponseEntity.ok(health);
    }

    /**
     * Delete a job and its associated files.
     *
     * @param jobId The job identifier
     * @return Success message
     */
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable String jobId) {
        logger.info("Delete request for job: {}", jobId);

        FileUploadResponse response = jobResults.remove(jobId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        // Clean up files
        zipExtractionService.cleanupExtractionDirectory(jobId);
        cleanupUploadedFile(jobId);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Job deleted successfully");
        result.put("jobId", jobId);
        
        return ResponseEntity.ok(result);
    }

    private boolean isZipFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        return filename.toLowerCase().endsWith(".zip");
    }

    private File saveUploadedFile(MultipartFile file, String jobId) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        // Save file with job ID prefix
        String filename = jobId + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

        return filePath.toFile();
    }

    private void processFileAsync(File zipFile, String jobId) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting async processing for job: {}", jobId);

                // Update status to processing
                FileUploadResponse response = jobResults.get(jobId);
                response.setStatus(FileUploadResponse.STATUS_PROCESSING);
                response.setMessage("Extracting ZIP file and processing documents...");

                // Create extraction result
                ExtractionResult extractionResult = new ExtractionResult(jobId, zipFile.getName());

                // Extract ZIP file
                List<String> extractedFiles = zipExtractionService.extractZipFile(zipFile, jobId);
                extractionResult.setTotalFilesInZip(extractedFiles.size());
                extractionResult.setProcessedFiles(extractedFiles.size());

                // Extract documents
                List<DocumentInfo> documents = documentExtractionService.extractDocuments(extractedFiles);
                
                // Add documents to result
                for (DocumentInfo doc : documents) {
                    // Always add the document, even if content is empty (especially for images)
                    extractionResult.addDocument(doc);
                    
                    // Add warning only for non-image files with no content
                    if ((doc.getContent() == null || doc.getContent().trim().isEmpty()) && 
                        !isImageFile(doc.getFileName())) {
                        extractionResult.addWarning("No content extracted from: " + doc.getFileName());
                    }
                }

                // Mark processing complete
                extractionResult.markProcessingComplete();

                // Update response
                response.setStatus(FileUploadResponse.STATUS_COMPLETED);
                response.setMessage("Processing completed successfully");
                response.setExtractionResult(extractionResult);

                logger.info("Async processing completed for job: {}. Extracted {} documents", 
                    jobId, extractionResult.getSuccessfulExtractions());

            } catch (Exception e) {
                logger.error("Async processing failed for job: {}", jobId, e);

                // Update response with error
                FileUploadResponse response = jobResults.get(jobId);
                response.setStatus(FileUploadResponse.STATUS_FAILED);
                response.setMessage("Processing failed: " + e.getMessage());
                response.setErrors(Arrays.asList(e.getMessage()));
            } finally {
                // Clean up uploaded file
                cleanupUploadedFile(jobId);
            }
        });
    }

    /**
     * Check if a file is an image based on its extension.
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String extension = getFileExtension(fileName);
        return extension.matches("jpg|jpeg|png|gif|bmp|tiff|tif|webp");
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    private void cleanupUploadedFile(String jobId) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            Files.list(uploadPath)
                .filter(path -> path.getFileName().toString().startsWith(jobId + "_"))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                        logger.debug("Cleaned up uploaded file: {}", path);
                    } catch (IOException e) {
                        logger.warn("Failed to cleanup uploaded file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to cleanup uploaded files for job: {}", jobId, e);
        }
    }

    private FileUploadResponse createErrorResponse(String message) {
        FileUploadResponse response = new FileUploadResponse();
        response.setStatus(FileUploadResponse.STATUS_FAILED);
        response.setMessage(message);
        response.setErrors(Arrays.asList(message));
        return response;
    }
}
