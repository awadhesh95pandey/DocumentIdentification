package org.example.service;

import org.example.model.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PatternMatchingService to verify document classification patterns.
 */
@SpringBootTest
class PatternMatchingServiceTest {

    private PatternMatchingService patternMatchingService;

    @BeforeEach
    void setUp() {
        patternMatchingService = new PatternMatchingService();
    }

    @Test
    void testAadhaarCardPatternMatching() {
        // Test with valid Aadhaar number format
        String aadhaarContent = "Government of India Aadhaar Card 1234 5678 9012 Unique Identification Authority";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(aadhaarContent);
        
        assertEquals(DocumentType.AADHAR_CARD, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.7);
        assertFalse(result.getExtractedIdentifiers().isEmpty());
        assertTrue(result.getMatchedKeywords().contains("aadhaar"));
    }

    @Test
    void testPANCardPatternMatching() {
        // Test with valid PAN number format
        String panContent = "Income Tax Department PAN Card ABCDE1234F Permanent Account Number";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(panContent);
        
        assertEquals(DocumentType.PAN_CARD, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.7);
        assertFalse(result.getExtractedIdentifiers().isEmpty());
        assertTrue(result.getMatchedKeywords().contains("pan"));
    }

    @Test
    void testDrivingLicensePatternMatching() {
        // Test with driving license content
        String dlContent = "Driving Licence MH02 2019 0012345 Transport Department Motor Vehicle";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(dlContent);
        
        assertEquals(DocumentType.DRIVING_LICENSE, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertTrue(result.getMatchedKeywords().contains("driving licence"));
    }

    @Test
    void testPassportPatternMatching() {
        // Test with passport content
        String passportContent = "Republic of India Passport A1234567 Ministry of External Affairs";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(passportContent);
        
        assertEquals(DocumentType.PASSPORT, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertTrue(result.getMatchedKeywords().contains("passport"));
    }

    @Test
    void testVoterIDPatternMatching() {
        // Test with voter ID content
        String voterContent = "Election Commission of India Voter ID ABC1234567 Electoral Photo Identity Card";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(voterContent);
        
        assertEquals(DocumentType.VOTER_ID, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertTrue(result.getMatchedKeywords().contains("election commission"));
    }

    @Test
    void testFilenameAnalysis() {
        // Test Aadhaar filename
        PatternMatchingService.PatternMatchResult result1 = 
            patternMatchingService.analyzeFilename("aadhaar_card_scan.pdf");
        assertEquals(DocumentType.AADHAR_CARD, result1.getDocumentType());
        assertTrue(result1.getConfidence() > 0.7);

        // Test PAN filename
        PatternMatchingService.PatternMatchResult result2 = 
            patternMatchingService.analyzeFilename("pan_card_copy.jpg");
        assertEquals(DocumentType.PAN_CARD, result2.getDocumentType());
        assertTrue(result2.getConfidence() > 0.7);

        // Test driving license filename
        PatternMatchingService.PatternMatchResult result3 = 
            patternMatchingService.analyzeFilename("driving_license.pdf");
        assertEquals(DocumentType.DRIVING_LICENSE, result3.getDocumentType());
        assertTrue(result3.getConfidence() > 0.6);
    }

    @Test
    void testBankStatementPatternMatching() {
        // Test with bank statement content
        String bankContent = "Bank Statement Account Number 123456789 Transaction History Debit Credit Balance";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(bankContent);
        
        assertEquals(DocumentType.BANK_STATEMENT, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.5);
        assertTrue(result.getMatchedKeywords().contains("bank statement"));
    }

    @Test
    void testUnknownDocument() {
        // Test with content that doesn't match any patterns
        String unknownContent = "This is some random text that doesn't match any document patterns";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(unknownContent);
        
        assertEquals(DocumentType.OTHER, result.getDocumentType());
        assertTrue(result.getConfidence() < 0.5);
    }

    @Test
    void testEmptyContent() {
        // Test with empty content
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent("");
        
        assertEquals(DocumentType.UNKNOWN, result.getDocumentType());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void testMultiplePatternMatches() {
        // Test content that might match multiple patterns
        String mixedContent = "Government of India Aadhaar 1234 5678 9012 and PAN ABCDE1234F";
        PatternMatchingService.PatternMatchResult result = patternMatchingService.analyzeContent(mixedContent);
        
        // Should prioritize Aadhaar due to higher confidence
        assertEquals(DocumentType.AADHAR_CARD, result.getDocumentType());
        assertTrue(result.getConfidence() > 0.7);
    }
}
