package org.example.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for extracting ZIP files safely with security validations.
 * Handles ZIP bomb protection, path traversal prevention, and file type validation.
 */
@Service
public class ZipExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ZipExtractionService.class);

    @Value("${app.temp.dir:./temp}")
    private String tempDir;

    @Value("${app.max-zip-entries:1000}")
    private int maxZipEntries;

    @Value("${app.max-zip-size:104857600}") // 100MB in bytes
    private long maxZipSize;

    private static final int BUFFER_SIZE = 4096;
    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024; // 50MB per entry

    /**
     * Extract ZIP file to a temporary directory and return list of extracted file paths.
     *
     * @param zipFile The ZIP file to extract
     * @param jobId   Unique job identifier for creating extraction directory
     * @return List of extracted file paths
     * @throws IOException if extraction fails
     */
    public List<String> extractZipFile(File zipFile, String jobId) throws IOException {
        logger.info("Starting ZIP extraction for job: {}, file: {}", jobId, zipFile.getName());

        // Validate ZIP file
        validateZipFile(zipFile);

        // Create extraction directory
        Path extractionDir = createExtractionDirectory(jobId);
        List<String> extractedFiles = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalExtractedSize = 0;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Security checks
                entryCount++;
                if (entryCount > maxZipEntries) {
                    throw new IOException("ZIP file contains too many entries (max: " + maxZipEntries + ")");
                }

                if (entry.getSize() > MAX_ENTRY_SIZE) {
                    logger.warn("Skipping large entry: {} (size: {} bytes)", entry.getName(), entry.getSize());
                    continue;
                }

                // Prevent path traversal attacks
                String entryName = sanitizeEntryName(entry.getName());
                if (entryName == null) {
                    logger.warn("Skipping potentially malicious entry: {}", entry.getName());
                    continue;
                }

                Path entryPath = extractionDir.resolve(entryName);

                // Ensure the entry path is within the extraction directory
                if (!entryPath.normalize().startsWith(extractionDir.normalize())) {
                    logger.warn("Skipping entry with path traversal attempt: {}", entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // Create parent directories if they don't exist
                    Files.createDirectories(entryPath.getParent());

                    // Extract file
                    long extractedSize = extractFile(zipInputStream, entryPath.toFile());
                    totalExtractedSize += extractedSize;

                    // Check total extracted size
                    if (totalExtractedSize > maxZipSize) {
                        throw new IOException("Total extracted size exceeds limit (max: " + maxZipSize + " bytes)");
                    }

                    extractedFiles.add(entryPath.toString());
                    logger.debug("Extracted: {} ({} bytes)", entryName, extractedSize);
                }

                zipInputStream.closeEntry();
            }
        }

        logger.info("ZIP extraction completed for job: {}. Extracted {} files", jobId, extractedFiles.size());
        return extractedFiles;
    }

    /**
     * Clean up extraction directory for a specific job.
     *
     * @param jobId The job identifier
     */
    public void cleanupExtractionDirectory(String jobId) {
        try {
            Path extractionDir = Paths.get(tempDir, "extraction", jobId);
            if (Files.exists(extractionDir)) {
                FileUtils.deleteDirectory(extractionDir.toFile());
                logger.info("Cleaned up extraction directory for job: {}", jobId);
            }
        } catch (IOException e) {
            logger.error("Failed to cleanup extraction directory for job: {}", jobId, e);
        }
    }

    private void validateZipFile(File zipFile) throws IOException {
        if (!zipFile.exists()) {
            throw new IOException("ZIP file does not exist: " + zipFile.getPath());
        }

        if (zipFile.length() > maxZipSize) {
            throw new IOException("ZIP file too large (max: " + maxZipSize + " bytes)");
        }

        // Basic ZIP file validation
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            if (zis.getNextEntry() == null) {
                throw new IOException("Invalid or empty ZIP file");
            }
        }
    }

    private Path createExtractionDirectory(String jobId) throws IOException {
        Path extractionDir = Paths.get(tempDir, "extraction", jobId);
        Files.createDirectories(extractionDir);
        return extractionDir;
    }

    private String sanitizeEntryName(String entryName) {
        if (entryName == null || entryName.trim().isEmpty()) {
            return null;
        }

        // Remove leading slashes and normalize path separators
        entryName = entryName.replaceAll("^/+", "").replace("\\", "/");

        // Check for path traversal attempts
        if (entryName.contains("../") || entryName.contains("..\\") || entryName.equals("..")) {
            return null;
        }

        // Check for absolute paths
        if (entryName.startsWith("/") || (entryName.length() > 1 && entryName.charAt(1) == ':')) {
            return null;
        }

        // Limit filename length
        if (entryName.length() > 255) {
            return null;
        }

        return entryName;
    }

    private long extractFile(ZipInputStream zipInputStream, File outputFile) throws IOException {
        long totalBytesRead = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            int bytesRead;
            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;

                // Prevent ZIP bomb attacks
                if (totalBytesRead > MAX_ENTRY_SIZE) {
                    throw new IOException("Entry size exceeds maximum allowed size");
                }

                bos.write(buffer, 0, bytesRead);
            }
        }

        return totalBytesRead;
    }
}
