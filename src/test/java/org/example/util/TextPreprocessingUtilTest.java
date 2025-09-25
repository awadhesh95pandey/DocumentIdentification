package org.example.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TextPreprocessingUtil to verify text cleaning and preprocessing functionality.
 */
@SpringBootTest
class TextPreprocessingUtilTest {

    @Test
    void testCleanAndNormalize() {
        String dirtyText = "  Government   of\tIndia\n\nAadhaar   Card  ";
        String cleaned = TextPreprocessingUtil.cleanAndNormalize(dirtyText);
        
        assertEquals("government of india aadhaar card", cleaned);
        assertFalse(cleaned.contains("\t"));
        assertFalse(cleaned.contains("\n"));
        assertFalse(cleaned.startsWith(" "));
        assertFalse(cleaned.endsWith(" "));
    }

    @Test
    void testExtractAadhaarNumbers() {
        String content = "My Aadhaar number is 1234 5678 9012 and another one is 987654321098";
        List<String> identifiers = TextPreprocessingUtil.extractPotentialIdentifiers(content);
        
        assertTrue(identifiers.contains("123456789012"));
        assertTrue(identifiers.contains("987654321098"));
    }

    @Test
    void testExtractPANNumbers() {
        String content = "PAN number ABCDE1234F and another PAN XYZAB9876C";
        List<String> identifiers = TextPreprocessingUtil.extractPotentialIdentifiers(content);
        
        assertTrue(identifiers.contains("ABCDE1234F"));
        assertTrue(identifiers.contains("XYZAB9876C"));
    }

    @Test
    void testOCRErrorCorrection() {
        String textWithErrors = "Govt. 0f lndia Aadhar Card";
        String corrected = TextPreprocessingUtil.correctCommonOCRErrors(textWithErrors);
        
        assertTrue(corrected.contains("Government"));
        assertTrue(corrected.contains("of"));
        assertTrue(corrected.contains("India"));
        assertTrue(corrected.contains("Aadhaar"));
    }

    @Test
    void testRemoveExcessiveWhitespace() {
        String text = "Government    of     India   Aadhaar";
        String normalized = TextPreprocessingUtil.removeExcessiveWhitespace(text);
        
        assertEquals("Government of India Aadhaar", normalized);
        assertFalse(normalized.contains("  ")); // No double spaces
    }

    @Test
    void testNormalizeLineBreaks() {
        String text = "Government of India\nAadhaar Card\r\nUID";
        String normalized = TextPreprocessingUtil.normalizeLineBreaks(text);
        
        assertEquals("Government of India Aadhaar Card UID", normalized);
        assertFalse(normalized.contains("\n"));
        assertFalse(normalized.contains("\r"));
    }

    @Test
    void testExtractKeywords() {
        String text = "Government of India Aadhaar Card Unique Identification";
        Set<String> keywords = TextPreprocessingUtil.extractKeywords(text);
        
        assertTrue(keywords.contains("government"));
        assertTrue(keywords.contains("aadhaar"));
        assertTrue(keywords.contains("government of"));
        assertTrue(keywords.contains("of india"));
        assertTrue(keywords.contains("government of india"));
    }

    @Test
    void testCalculateSimilarity() {
        String text1 = "Government of India Aadhaar Card";
        String text2 = "India Government Aadhaar Document";
        
        double similarity = TextPreprocessingUtil.calculateSimilarity(text1, text2);
        
        assertTrue(similarity > 0.0);
        assertTrue(similarity <= 1.0);
        assertTrue(similarity > 0.3); // Should have reasonable similarity
    }

    @Test
    void testExtractNumericSequences() {
        String text = "Account number 1234567890 and phone 9876543210 and short 123";
        List<String> sequences = TextPreprocessingUtil.extractNumericSequences(text);
        
        assertTrue(sequences.contains("1234567890"));
        assertTrue(sequences.contains("9876543210"));
        assertFalse(sequences.contains("123")); // Too short (less than 4 digits)
    }

    @Test
    void testExtractAlphanumericSequences() {
        String text = "PAN ABCDE1234F and license MH0220190012345 and short ABC12";
        List<String> sequences = TextPreprocessingUtil.extractAlphanumericSequences(text);
        
        assertTrue(sequences.contains("ABCDE1234F"));
        assertTrue(sequences.contains("MH0220190012345"));
        assertFalse(sequences.contains("ABC12")); // Too short (less than 6 characters)
    }

    @Test
    void testHandleNullAndEmptyInputs() {
        // Test null inputs
        assertEquals("", TextPreprocessingUtil.cleanAndNormalize(null));
        assertTrue(TextPreprocessingUtil.extractPotentialIdentifiers(null).isEmpty());
        assertTrue(TextPreprocessingUtil.extractKeywords(null).isEmpty());
        
        // Test empty inputs
        assertEquals("", TextPreprocessingUtil.cleanAndNormalize(""));
        assertTrue(TextPreprocessingUtil.extractPotentialIdentifiers("").isEmpty());
        assertTrue(TextPreprocessingUtil.extractKeywords("").isEmpty());
    }

    @Test
    void testValidateAadhaarNumber() {
        // This tests the internal validation through the extraction method
        String validAadhaar = "Valid Aadhaar 1234 5678 9012";
        String invalidAadhaar = "Invalid Aadhaar 1111 1111 1111"; // All same digits
        
        List<String> validIds = TextPreprocessingUtil.extractPotentialIdentifiers(validAadhaar);
        List<String> invalidIds = TextPreprocessingUtil.extractPotentialIdentifiers(invalidAadhaar);
        
        assertFalse(validIds.isEmpty());
        assertTrue(invalidIds.isEmpty()); // Should reject all-same-digit Aadhaar
    }

    @Test
    void testValidatePANNumber() {
        // This tests the internal validation through the extraction method
        String validPAN = "Valid PAN ABCDE1234F";
        String invalidPAN = "Invalid PAN ABC123DEF"; // Wrong format
        
        List<String> validIds = TextPreprocessingUtil.extractPotentialIdentifiers(validPAN);
        List<String> invalidIds = TextPreprocessingUtil.extractPotentialIdentifiers(invalidPAN);
        
        assertFalse(validIds.isEmpty());
        assertTrue(invalidIds.isEmpty()); // Should reject invalid PAN format
    }
}
