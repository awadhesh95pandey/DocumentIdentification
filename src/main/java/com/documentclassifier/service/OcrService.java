package com.documentclassifier.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Service
public class OcrService {
    
    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);
    
    private final ImageAnnotatorClient imageAnnotatorClient;
    
    @Autowired
    public OcrService(ImageAnnotatorClient imageAnnotatorClient) {
        this.imageAnnotatorClient = imageAnnotatorClient;
    }
    
    /**
     * Extract text from image using Google Cloud Vision API
     */
    public String extractTextFromImage(File imageFile) throws IOException {
        logger.debug("Extracting text from image: {}", imageFile.getName());
        
        // Read image file
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        ByteString imgBytes = ByteString.copyFrom(imageBytes);
        
        // Create image object
        Image image = Image.newBuilder().setContent(imgBytes).build();
        
        // Create feature for text detection
        Feature feature = Feature.newBuilder()
                .setType(Feature.Type.TEXT_DETECTION)
                .build();
        
        // Create annotation request
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build();
        
        // Perform text detection
        BatchAnnotateImagesResponse response = imageAnnotatorClient.batchAnnotateImages(
                List.of(request)
        );
        
        List<AnnotateImageResponse> responses = response.getResponsesList();
        
        if (responses.isEmpty()) {
            throw new IOException("No response received from Vision API");
        }
        
        AnnotateImageResponse imageResponse = responses.get(0);
        
        // Check for errors
        if (imageResponse.hasError()) {
            String errorMessage = imageResponse.getError().getMessage();
            logger.error("Vision API error for image {}: {}", imageFile.getName(), errorMessage);
            throw new IOException("OCR failed: " + errorMessage);
        }
        
        // Extract text
        TextAnnotation textAnnotation = imageResponse.getFullTextAnnotation();
        if (textAnnotation == null || textAnnotation.getText().trim().isEmpty()) {
            logger.warn("No text found in image: {}", imageFile.getName());
            return "";
        }
        
        String extractedText = textAnnotation.getText().trim();
        logger.debug("Extracted {} characters of text from image: {}", 
                extractedText.length(), imageFile.getName());
        
        return extractedText;
    }
}
