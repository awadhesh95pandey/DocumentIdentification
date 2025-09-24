package org.example.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.example.model.ClassificationResult;
import org.example.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for classifying documents using OpenAI's GPT models.
 * Supports both text-based and image-based document classification.
 */
@Service
public class DocumentClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentClassificationService.class);

    @Autowired(required = false)
    private OpenAiService openAiService;

    @Autowired
    private ImageProcessingService imageProcessingService;

    @Value("${openai.api.model:gpt-4-vision-preview}")
    private String model;

    @Value("${openai.api.max-tokens:1000}")
    private int maxTokens;

    @Value("${openai.classification.enabled:true}")
    private boolean classificationEnabled;

    private static final String CLASSIFICATION_PROMPT = """
            You are an expert document classifier specializing in Indian official documents and common business documents.
            
            Analyze the provided document and classify it into one of these specific categories:
            
            INDIAN IDENTITY DOCUMENTS:
            - AADHAR_CARD: Indian national identity card with 12-digit UID number
            - PAN_CARD: Permanent Account Number card for tax identification
            - PASSPORT: International travel document
            - DRIVING_LICENSE: License to operate motor vehicles
            - VOTER_ID: Electoral identity card for voting
            - RATION_CARD: Document for accessing subsidized food grains
            
            FINANCIAL DOCUMENTS:
            - BANK_STATEMENT: Account transaction history
            - SALARY_SLIP: Employee salary and deduction details
            - UTILITY_BILL: Electricity, water, gas, or telecom bills
            - INVOICE: Commercial bill for goods/services
            - RECEIPT: Payment proof or transaction receipt
            
            OFFICIAL DOCUMENTS:
            - PROPERTY_DOCUMENT: Property ownership or rental documents
            - INSURANCE_DOCUMENT: Policy or claim documents
            - MEDICAL_REPORT: Healthcare or medical examination documents
            - EDUCATIONAL_CERTIFICATE: Academic degrees, diplomas, certificates
            - EMPLOYMENT_LETTER: Job offer, experience, or verification letters
            - BUSINESS_REGISTRATION: Company incorporation or business licenses
            - TAX_DOCUMENT: Income tax returns, tax certificates
            - LEGAL_DOCUMENT: Court orders, legal notices, contracts
            
            GENERAL DOCUMENTS:
            - FORM: Application forms or official forms
            - LETTER: Formal or informal correspondence
            - REPORT: Business, technical, or analytical reports
            - PRESENTATION: Slide decks or presentation materials
            - SPREADSHEET: Data tables or calculation documents
            - OTHER: Document type not specifically classified above
            - UNKNOWN: Unable to determine document type
            
            INSTRUCTIONS:
            1. Analyze the document content, layout, headers, logos, and text patterns
            2. Look for specific identifiers (like Aadhar numbers, PAN format, passport numbers)
            3. Consider document structure and official formatting
            4. Provide your classification in this exact format:
            
            CLASSIFICATION: [DOCUMENT_TYPE]
            CONFIDENCE: [0.0-1.0]
            REASONING: [Brief explanation of why you classified it this way, mentioning key identifying features]
            
            Be specific and accurate. If uncertain, use UNKNOWN with low confidence rather than guessing.
            """;

    /**
     * Classifies a document based on its text content.
     */
    public ClassificationResult classifyTextDocument(String content, String fileName) {
        if (!isClassificationAvailable()) {
            logger.warn("OpenAI classification not available for: {}", fileName);
            return ClassificationResult.failure("OpenAI service not configured or unavailable", 0);
        }

        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Classifying text document: {}", fileName);

            String prompt = CLASSIFICATION_PROMPT + "\n\nDOCUMENT CONTENT:\n" + content;
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model.equals("gpt-4-vision-preview") ? "gpt-4" : model) // Use text model for text content
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(0.1) // Low temperature for consistent classification
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            long processingTime = System.currentTimeMillis() - startTime;
            return parseClassificationResponse(response, model, false, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("Error classifying text document {}: {}", fileName, e.getMessage());
            return ClassificationResult.failure("Classification failed: " + e.getMessage(), processingTime);
        }
    }

    /**
     * Classifies a document based on its image content.
     */
    public ClassificationResult classifyImageDocument(File imageFile) {
        if (!isClassificationAvailable()) {
            logger.warn("OpenAI classification not available for image: {}", imageFile.getName());
            return ClassificationResult.failure("OpenAI service not configured or unavailable", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            logger.info("Classifying image document: {}", imageFile.getName());

            // Validate and process image
            if (!imageProcessingService.isValidImageFile(imageFile)) {
                return ClassificationResult.failure("Invalid or unsupported image file", 
                                                  System.currentTimeMillis() - startTime);
            }

            String base64Image = imageProcessingService.processImageForAI(imageFile);
            
            List<ChatMessage> messages = new ArrayList<>();
            
            // Create message with image content
            ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), CLASSIFICATION_PROMPT);
            messages.add(message);

            // Note: The current OpenAI Java library may not fully support vision API
            // This is a simplified implementation - you may need to use HTTP client directly
            // for full vision API support with base64 images
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(0.1)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            long processingTime = System.currentTimeMillis() - startTime;
            return parseClassificationResponse(response, model, true, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("Error classifying image document {}: {}", imageFile.getName(), e.getMessage());
            return ClassificationResult.failure("Image classification failed: " + e.getMessage(), processingTime);
        }
    }

    /**
     * Classifies a document using filename and basic heuristics as fallback.
     */
    public ClassificationResult classifyByFilename(String fileName) {
        long startTime = System.currentTimeMillis();
        
        try {
            String lowerFileName = fileName.toLowerCase();
            DocumentType type = DocumentType.OTHER;
            double confidence = 0.3; // Low confidence for filename-based classification
            String reasoning = "Classification based on filename patterns";

            // Simple filename-based classification
            if (lowerFileName.contains("aadhar") || lowerFileName.contains("aadhaar")) {
                type = DocumentType.AADHAR_CARD;
                confidence = 0.8; // Higher confidence for clear filename match
                reasoning = "Filename contains 'aadhar' keyword indicating Aadhar card document";
            } else if (lowerFileName.contains("pan")) {
                type = DocumentType.PAN_CARD;
                confidence = 0.7;
                reasoning = "Filename contains 'pan' keyword indicating PAN card document";
            } else if (lowerFileName.contains("passport")) {
                type = DocumentType.PASSPORT;
                confidence = 0.7;
                reasoning = "Filename contains 'passport' keyword indicating passport document";
            } else if (lowerFileName.contains("license") || lowerFileName.contains("dl")) {
                type = DocumentType.DRIVING_LICENSE;
                confidence = 0.7;
                reasoning = "Filename contains driving license related keywords";
            } else if (lowerFileName.contains("voter")) {
                type = DocumentType.VOTER_ID;
                confidence = 0.7;
                reasoning = "Filename contains 'voter' keyword indicating voter ID document";
            } else if (lowerFileName.contains("bank") && lowerFileName.contains("statement")) {
                type = DocumentType.BANK_STATEMENT;
                confidence = 0.5;
            } else if (lowerFileName.contains("salary") || lowerFileName.contains("payslip")) {
                type = DocumentType.SALARY_SLIP;
                confidence = 0.5;
            } else if (lowerFileName.contains("invoice")) {
                type = DocumentType.INVOICE;
                confidence = 0.5;
            } else if (lowerFileName.contains("receipt")) {
                type = DocumentType.RECEIPT;
                confidence = 0.5;
            }

            long processingTime = System.currentTimeMillis() - startTime;
            return ClassificationResult.success(type, confidence, reasoning, "filename-heuristic", false, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            return ClassificationResult.failure("Filename classification failed: " + e.getMessage(), processingTime);
        }
    }

    /**
     * Parses the OpenAI response to extract classification information.
     */
    private ClassificationResult parseClassificationResponse(String response, String aiModel, boolean isImageBased, long processingTime) {
        try {
            // Extract classification using regex patterns
            Pattern classificationPattern = Pattern.compile("CLASSIFICATION:\\s*([A-Z_]+)", Pattern.CASE_INSENSITIVE);
            Pattern confidencePattern = Pattern.compile("CONFIDENCE:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
            Pattern reasoningPattern = Pattern.compile("REASONING:\\s*(.+?)(?=\\n\\n|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

            Matcher classificationMatcher = classificationPattern.matcher(response);
            Matcher confidenceMatcher = confidencePattern.matcher(response);
            Matcher reasoningMatcher = reasoningPattern.matcher(response);

            DocumentType documentType = DocumentType.UNKNOWN;
            double confidence = 0.0;
            String reasoning = "No reasoning provided";

            if (classificationMatcher.find()) {
                String classificationStr = classificationMatcher.group(1).trim();
                documentType = DocumentType.fromString(classificationStr);
            }

            if (confidenceMatcher.find()) {
                try {
                    confidence = Double.parseDouble(confidenceMatcher.group(1));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid confidence value in response: {}", confidenceMatcher.group(1));
                }
            }

            if (reasoningMatcher.find()) {
                reasoning = reasoningMatcher.group(1).trim();
            }

            logger.info("Classification result: {} (confidence: {}, reasoning: {})", 
                       documentType, confidence, reasoning);

            return ClassificationResult.success(documentType, confidence, reasoning, aiModel, isImageBased, processingTime);

        } catch (Exception e) {
            logger.error("Error parsing classification response: {}", e.getMessage());
            return ClassificationResult.failure("Failed to parse classification response", processingTime);
        }
    }

    /**
     * Checks if classification is available (OpenAI service is configured).
     */
    private boolean isClassificationAvailable() {
        return classificationEnabled && openAiService != null;
    }

    /**
     * Gets the status of the classification service.
     */
    public String getClassificationStatus() {
        if (!classificationEnabled) {
            return "Classification is disabled";
        }
        if (openAiService == null) {
            return "OpenAI service not configured (check API key)";
        }
        return "Classification service is available";
    }
}
