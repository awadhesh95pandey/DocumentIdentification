package com.documentclassifier.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class FileUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    public static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".webp", ".bmp"
    );
    
    /**
     * Check if a file has a supported image extension
     */
    public static boolean isImageFile(String filename) {
        if (filename == null) return false;
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) return false;
        
        String extension = filename.substring(lastDotIndex).toLowerCase();
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension);
    }
    
    /**
     * Save uploaded file to temporary location
     */
    public static File saveUploadedFile(InputStream inputStream, String filename, Path tempDir) throws IOException {
        File tempFile = tempDir.resolve(filename).toFile();
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            IOUtils.copy(inputStream, fos);
        }
        
        logger.debug("Saved uploaded file: {}", tempFile.getAbsolutePath());
        return tempFile;
    }
    
    /**
     * Get all files recursively from a directory
     */
    public static List<File> getAllFiles(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return List.of();
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return List.of();
        }
        
        return Arrays.stream(files)
                .filter(File::isFile)
                .toList();
    }
    
    /**
     * Clean up temporary directory and all its contents
     */
    public static void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temporary file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.error("Failed to cleanup temporary directory: {}", tempDir, e);
        }
    }
}
