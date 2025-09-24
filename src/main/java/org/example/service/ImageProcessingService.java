package org.example.service;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Service for processing image files before sending to OpenAI for classification.
 * Handles image validation, format conversion, and resizing.
 */
@Service
public class ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);

    @Value("${app.image.allowed-formats:jpg,jpeg,png,pdf,tiff,bmp}")
    private String allowedFormats;

    @Value("${app.image.resize-max-width:1024}")
    private int maxWidth;

    @Value("${app.image.resize-max-height:1024}")
    private int maxHeight;

    @Value("${app.image.max-size:10MB}")
    private String maxSizeStr;

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp"
    );

    /**
     * Checks if a file is an image based on its extension.
     */
    public boolean isImageFile(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * Validates if the image file is supported and within size limits.
     */
    public boolean isValidImageFile(File imageFile) {
        try {
            if (!imageFile.exists() || !imageFile.isFile()) {
                return false;
            }

            // Check file size
            long maxSizeBytes = parseSize(maxSizeStr);
            if (imageFile.length() > maxSizeBytes) {
                logger.warn("Image file {} exceeds maximum size limit: {} bytes", 
                           imageFile.getName(), maxSizeBytes);
                return false;
            }

            // Check if it's a valid image
            ImageInfo imageInfo = Imaging.getImageInfo(imageFile);
            if (imageInfo == null) {
                return false;
            }

            // Check format
            String format = imageInfo.getFormat().getName().toLowerCase();
            List<String> allowedFormatsList = Arrays.asList(allowedFormats.toLowerCase().split(","));
            
            return allowedFormatsList.contains(format) || 
                   allowedFormatsList.contains("jpg") && format.equals("jpeg");

        } catch (Exception e) {
            logger.error("Error validating image file {}: {}", imageFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Processes an image file for OpenAI Vision API.
     * Resizes if necessary and converts to base64.
     */
    public String processImageForAI(File imageFile) throws IOException {
        logger.info("Processing image file: {}", imageFile.getName());

        try {
            // Read the image
            BufferedImage originalImage = ImageIO.read(imageFile);
            if (originalImage == null) {
                throw new IOException("Unable to read image file: " + imageFile.getName());
            }

            // Resize if necessary
            BufferedImage processedImage = resizeImageIfNeeded(originalImage);

            // Convert to base64
            String base64Image = convertToBase64(processedImage, getImageFormat(imageFile));
            
            logger.info("Successfully processed image: {} ({}x{} -> {}x{})", 
                       imageFile.getName(), 
                       originalImage.getWidth(), originalImage.getHeight(),
                       processedImage.getWidth(), processedImage.getHeight());

            return base64Image;

        } catch (Exception e) {
            logger.error("Error processing image file {}: {}", imageFile.getName(), e.getMessage());
            throw new IOException("Failed to process image: " + e.getMessage(), e);
        }
    }

    /**
     * Resizes image if it exceeds maximum dimensions while maintaining aspect ratio.
     */
    private BufferedImage resizeImageIfNeeded(BufferedImage originalImage) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Check if resizing is needed
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return originalImage;
        }

        // Calculate new dimensions maintaining aspect ratio
        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

        logger.info("Resizing image from {}x{} to {}x{}", 
                   originalWidth, originalHeight, newWidth, newHeight);

        // Create resized image
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * Converts BufferedImage to base64 string.
     */
    private String convertToBase64(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Gets the image format from file extension.
     */
    private String getImageFormat(File imageFile) {
        String fileName = imageFile.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "jpg";
        } else if (fileName.endsWith(".png")) {
            return "png";
        } else if (fileName.endsWith(".gif")) {
            return "gif";
        } else if (fileName.endsWith(".bmp")) {
            return "bmp";
        } else if (fileName.endsWith(".tiff") || fileName.endsWith(".tif")) {
            return "tiff";
        } else {
            return "jpg"; // Default fallback
        }
    }

    /**
     * Parses size string (e.g., "10MB") to bytes.
     */
    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 10 * 1024 * 1024; // Default 10MB
        }

        String size = sizeStr.trim().toUpperCase();
        long multiplier = 1;

        if (size.endsWith("KB")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("MB")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        }

        try {
            return Long.parseLong(size.trim()) * multiplier;
        } catch (NumberFormatException e) {
            logger.warn("Invalid size format: {}, using default 10MB", sizeStr);
            return 10 * 1024 * 1024;
        }
    }

    /**
     * Gets image metadata information.
     */
    public String getImageInfo(File imageFile) {
        try {
            ImageInfo imageInfo = Imaging.getImageInfo(imageFile);
            if (imageInfo != null) {
                return String.format("Format: %s, Size: %dx%d, Color Type: %s", 
                                    imageInfo.getFormat().getName(),
                                    imageInfo.getWidth(),
                                    imageInfo.getHeight(),
                                    imageInfo.getColorType());
            }
        } catch (Exception e) {
            logger.debug("Could not get image info for {}: {}", imageFile.getName(), e.getMessage());
        }
        return "Image information not available";
    }
}
