package com.documentclassifier.service;

import com.documentclassifier.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

    /**
     * Extract images from uploaded ZIP file
     */
    public List<File> extractImagesFromZip(MultipartFile zipFile) throws IOException {
        // Create temporary directory
        Path tempDir = Files.createTempDirectory("document-classifier-");
        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        List<File> imageFiles = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = entry.getName();

                // Skip files in __MACOSX directory (common in ZIP files from Mac)
                if (fileName.startsWith("__MACOSX/") || fileName.startsWith(".")) {
                    continue;
                }

                // Check if it's an image file
                if (FileUtils.isImageFile(fileName)) {
                    // Extract just the filename without path
                    String simpleFileName = fileName.substring(fileName.lastIndexOf('/') + 1);

                    File extractedFile = FileUtils.saveUploadedFile(
                            zipInputStream,
                            simpleFileName,
                            extractDir
                    );

                    imageFiles.add(extractedFile);
                    logger.debug("Extracted image file: {}", simpleFileName);
                }

                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            // Clean up on error
            FileUtils.cleanupTempDirectory(tempDir);
            throw new IOException("Failed to extract ZIP file: " + e.getMessage(), e);
        }

        if (imageFiles.isEmpty()) {
            FileUtils.cleanupTempDirectory(tempDir);
            throw new IOException("No image files found in ZIP archive");
        }

        logger.info("Successfully extracted {} image files from ZIP", imageFiles.size());
        return imageFiles;
    }

    /**
     * Clean up temporary files after processing
     */
    public void cleanupFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        // Get the parent directory of the first file to clean up the entire temp directory
        File firstFile = files.get(0);
        Path tempDir = firstFile.toPath().getParent().getParent(); // Go up two levels to get the temp directory

        FileUtils.cleanupTempDirectory(tempDir);
        logger.debug("Cleaned up temporary files");
    }
}
