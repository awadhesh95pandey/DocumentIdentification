package org.example.service;

import org.example.model.ClassificationResult;
import org.example.model.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.theokanning.openai.service.OpenAiService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentClassificationService.
 */
@ExtendWith(MockitoExtension.class)
class DocumentClassificationServiceTest {

    @Mock
    private OpenAiService openAiService;

    @Mock
    private ImageProcessingService imageProcessingService;

    @InjectMocks
    private DocumentClassificationService classificationService;

    @BeforeEach
    void setUp() {
        // Set up test configuration
        classificationService = new DocumentClassificationService();
    }

    @Test
    void testClassifyByFilename_AadharCard() {
        // Test Aadhar card filename classification
        ClassificationResult result = classificationService.classifyByFilename("aadhar-card-copy.jpg");
        
        assertEquals(DocumentType.AADHAR_CARD, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_PANCard() {
        // Test PAN card filename classification
        ClassificationResult result = classificationService.classifyByFilename("pan-card-scan.pdf");
        
        assertEquals(DocumentType.PAN_CARD, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_Passport() {
        // Test passport filename classification
        ClassificationResult result = classificationService.classifyByFilename("passport-front-page.png");
        
        assertEquals(DocumentType.PASSPORT, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_DrivingLicense() {
        // Test driving license filename classification
        ClassificationResult result = classificationService.classifyByFilename("driving-license.jpg");
        
        assertEquals(DocumentType.DRIVING_LICENSE, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_BankStatement() {
        // Test bank statement filename classification
        ClassificationResult result = classificationService.classifyByFilename("bank-statement-jan-2024.pdf");
        
        assertEquals(DocumentType.BANK_STATEMENT, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.4);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_Invoice() {
        // Test invoice filename classification
        ClassificationResult result = classificationService.classifyByFilename("invoice-12345.pdf");
        
        assertEquals(DocumentType.INVOICE, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.4);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_UnknownDocument() {
        // Test unknown document filename classification
        ClassificationResult result = classificationService.classifyByFilename("random-document.txt");
        
        assertEquals(DocumentType.OTHER, result.getDocumentType());
        assertTrue(result.getConfidence() <= 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_EmptyFilename() {
        // Test empty filename
        ClassificationResult result = classificationService.classifyByFilename("");
        
        assertEquals(DocumentType.OTHER, result.getDocumentType());
        assertTrue(result.getConfidence() <= 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testClassifyByFilename_NullFilename() {
        // Test null filename
        ClassificationResult result = classificationService.classifyByFilename(null);
        
        assertEquals(DocumentType.OTHER, result.getDocumentType());
        assertTrue(result.getConfidence() <= 0.5);
        assertNotNull(result.getReasoning());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testGetClassificationStatus_ServiceNotAvailable() {
        // Test status when service is not available
        DocumentClassificationService service = new DocumentClassificationService();
        String status = service.getClassificationStatus();
        
        assertTrue(status.contains("not configured") || status.contains("disabled"));
    }

    @Test
    void testClassificationResultMethods() {
        // Test ClassificationResult utility methods
        ClassificationResult highConfidence = new ClassificationResult(DocumentType.AADHAR_CARD, 0.9);
        ClassificationResult mediumConfidence = new ClassificationResult(DocumentType.PAN_CARD, 0.6);
        ClassificationResult lowConfidence = new ClassificationResult(DocumentType.OTHER, 0.3);

        assertTrue(highConfidence.isHighConfidence());
        assertTrue(highConfidence.isMediumConfidence());
        assertTrue(highConfidence.isSuccessful());

        assertFalse(mediumConfidence.isHighConfidence());
        assertTrue(mediumConfidence.isMediumConfidence());
        assertTrue(mediumConfidence.isSuccessful());

        assertFalse(lowConfidence.isHighConfidence());
        assertFalse(lowConfidence.isMediumConfidence());
        assertTrue(lowConfidence.isSuccessful());
    }

    @Test
    void testDocumentTypeFromString() {
        // Test DocumentType.fromString method
        assertEquals(DocumentType.AADHAR_CARD, DocumentType.fromString("AADHAR_CARD"));
        assertEquals(DocumentType.AADHAR_CARD, DocumentType.fromString("aadhar_card"));
        assertEquals(DocumentType.AADHAR_CARD, DocumentType.fromString("Aadhar Card"));
        assertEquals(DocumentType.AADHAR_CARD, DocumentType.fromString("aadhar"));
        assertEquals(DocumentType.AADHAR_CARD, DocumentType.fromString("aadhaar"));
        
        assertEquals(DocumentType.PAN_CARD, DocumentType.fromString("PAN_CARD"));
        assertEquals(DocumentType.PAN_CARD, DocumentType.fromString("pan"));
        
        assertEquals(DocumentType.PASSPORT, DocumentType.fromString("passport"));
        assertEquals(DocumentType.DRIVING_LICENSE, DocumentType.fromString("driving license"));
        assertEquals(DocumentType.DRIVING_LICENSE, DocumentType.fromString("license"));
        
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromString(""));
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromString(null));
        assertEquals(DocumentType.OTHER, DocumentType.fromString("some random text"));
    }
}
