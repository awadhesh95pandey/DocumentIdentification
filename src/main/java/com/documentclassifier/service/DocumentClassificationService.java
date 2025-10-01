package com.documentclassifier.service;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Service
public class DocumentClassificationService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentClassificationService.class);
    private final GenerativeModel generativeModel;
    @Autowired
    public DocumentClassificationService(GenerativeModel generativeModel) {
        this.generativeModel = generativeModel;
    }
    /**
     * Classify document type based on extracted text using Gemini model
     */
    public String classifyDocumentType(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            logger.warn("Empty text provided for classification");
            return "None";
        }
        try {
            logger.debug("Classifying document with {} characters of text", extractedText.length());
            String prompt = buildClassificationPrompt(extractedText);
            logger.debug("Sending classification prompt to Gemini");
            // Generate content using Gemini
            var response = generativeModel.generateContent(prompt);
            String responseText = ResponseHandler.getText(response);
            logger.debug("Received response from Gemini: {}", responseText);
            String classification = parseClassificationResponse(responseText);
            logger.info("Document classified as: {}", classification);
            return classification;
        } catch (Exception e) {
            logger.error("Failed to classify document", e);
            return "Error: " + e.getMessage();
        }
    }
    /**
     * Build the classification prompt for Gemini
     */
    private String buildClassificationPrompt(String extractedText) {
        return String.format("""
                You are an assistant that classifies Indian identity documents based on OCR-extracted text.
                Respond with only ONE of the following:
                - Aadhaar
                - PAN
                - Voter ID
                - Driving License
                - None
                Here is the extracted text:
                %s
                What type of document is this? Respond with only one word.
                """, extractedText);
    }
    /**
     * Parse and validate the classification response
     */
    private String parseClassificationResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "None";
        }
        String cleanResponse = response.trim().toLowerCase();
        // Map common variations to standard classifications
        if (cleanResponse.contains("aadhaar") || cleanResponse.contains("aadhar")) {
            return "Aadhaar";
        } else if (cleanResponse.contains("pan")) {
            return "PAN";
        } else if (cleanResponse.contains("voter")) {
            return "Voter ID";
        } else if (cleanResponse.contains("driving") || cleanResponse.contains("license")) {
            return "Driving License";
        } else {
            return "None";
        }
    }
}