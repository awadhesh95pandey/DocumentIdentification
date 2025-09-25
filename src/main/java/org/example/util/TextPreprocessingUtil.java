package org.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for text preprocessing and cleaning operations.
 * Provides methods to clean, normalize, and prepare text content for document classification.
 */
public class TextPreprocessingUtil {

    private static final Logger logger = LoggerFactory.getLogger(TextPreprocessingUtil.class);

    // Common OCR error corrections
    private static final Map<String, String> OCR_CORRECTIONS = Map.of(
        "0", "O",  // Zero to letter O
        "1", "I",  // One to letter I
        "5", "S",  // Five to letter S
        "8", "B",  // Eight to letter B
        "rn", "m", // Common OCR error
        "vv", "w", // Double v to w
        "cl", "d", // cl to d
        "li", "h"  // li to h
    );

    // Patterns for cleaning
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\w\\s\\u0900-\\u097F]"); // Keep alphanumeric and Devanagari
    private static final Pattern LINE_BREAKS = Pattern.compile("\\r?\\n");
    private static final Pattern TABS = Pattern.compile("\\t");

    /**
     * Comprehensive text cleaning and normalization.
     */
    public static String cleanAndNormalize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String cleaned = text;

        // Step 1: Basic cleaning
        cleaned = removeExcessiveWhitespace(cleaned);
        cleaned = normalizeLineBreaks(cleaned);
        cleaned = removeTabs(cleaned);

        // Step 2: OCR error correction
        cleaned = correctCommonOCRErrors(cleaned);

        // Step 3: Normalize case and trim
        cleaned = cleaned.toLowerCase().trim();

        logger.debug("Text cleaned: {} -> {}", text.substring(0, Math.min(50, text.length())), 
                    cleaned.substring(0, Math.min(50, cleaned.length())));

        return cleaned;
    }

    /**
     * Extracts potential document identifiers from text.
     */
    public static List<String> extractPotentialIdentifiers(String text) {
        List<String> identifiers = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return identifiers;
        }

        String cleanText = cleanAndNormalize(text);

        // Extract potential Aadhaar numbers (12 digits with or without spaces)
        identifiers.addAll(extractAadhaarNumbers(cleanText));

        // Extract potential PAN numbers (ABCDE1234F format)
        identifiers.addAll(extractPANNumbers(cleanText));

        // Extract potential DL numbers
        identifiers.addAll(extractDLNumbers(cleanText));

        // Extract potential passport numbers
        identifiers.addAll(extractPassportNumbers(cleanText));

        // Extract potential voter ID numbers
        identifiers.addAll(extractVoterIDNumbers(cleanText));

        return identifiers;
    }

    /**
     * Removes excessive whitespace and normalizes spacing.
     */
    public static String removeExcessiveWhitespace(String text) {
        if (text == null) return "";
        return MULTIPLE_SPACES.matcher(text).replaceAll(" ");
    }

    /**
     * Normalizes line breaks to single spaces.
     */
    public static String normalizeLineBreaks(String text) {
        if (text == null) return "";
        return LINE_BREAKS.matcher(text).replaceAll(" ");
    }

    /**
     * Removes tab characters.
     */
    public static String removeTabs(String text) {
        if (text == null) return "";
        return TABS.matcher(text).replaceAll(" ");
    }

    /**
     * Corrects common OCR errors in text.
     */
    public static String correctCommonOCRErrors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String corrected = text;

        // Apply OCR corrections
        for (Map.Entry<String, String> correction : OCR_CORRECTIONS.entrySet()) {
            corrected = corrected.replace(correction.getKey(), correction.getValue());
        }

        // Specific corrections for Indian documents
        corrected = corrected.replace("Govt.", "Government");
        corrected = corrected.replace("Govt ", "Government ");
        corrected = corrected.replace("lndia", "India");
        corrected = corrected.replace("0f", "of");
        corrected = corrected.replace("Aadhar", "Aadhaar");

        return corrected;
    }

    /**
     * Removes special characters while preserving alphanumeric and Devanagari script.
     */
    public static String removeSpecialCharacters(String text) {
        if (text == null) return "";
        return SPECIAL_CHARS.matcher(text).replaceAll(" ");
    }

    /**
     * Extracts potential Aadhaar numbers from text.
     */
    private static List<String> extractAadhaarNumbers(String text) {
        List<String> aadhaarNumbers = new ArrayList<>();
        
        // Pattern for 12 digits with optional spaces
        Pattern aadhaarPattern = Pattern.compile("\\b\\d{4}\\s*\\d{4}\\s*\\d{4}\\b|\\b\\d{12}\\b");
        
        java.util.regex.Matcher matcher = aadhaarPattern.matcher(text);
        while (matcher.find()) {
            String number = matcher.group().replaceAll("\\s", "");
            if (number.length() == 12 && isValidAadhaarNumber(number)) {
                aadhaarNumbers.add(number);
            }
        }
        
        return aadhaarNumbers;
    }

    /**
     * Extracts potential PAN numbers from text.
     */
    private static List<String> extractPANNumbers(String text) {
        List<String> panNumbers = new ArrayList<>();
        
        // Pattern for PAN: 5 letters, 4 digits, 1 letter
        Pattern panPattern = Pattern.compile("\\b[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}\\b");
        
        java.util.regex.Matcher matcher = panPattern.matcher(text);
        while (matcher.find()) {
            String pan = matcher.group().toUpperCase();
            if (isValidPANNumber(pan)) {
                panNumbers.add(pan);
            }
        }
        
        return panNumbers;
    }

    /**
     * Extracts potential driving license numbers from text.
     */
    private static List<String> extractDLNumbers(String text) {
        List<String> dlNumbers = new ArrayList<>();
        
        // Pattern for DL numbers (various state formats)
        Pattern dlPattern = Pattern.compile(
            "\\b[A-Za-z]{2}[-\\s]?\\d{2}[-\\s]?\\d{4}[-\\s]?\\d{7}\\b|\\bDL[-\\s]?\\d{14}\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = dlPattern.matcher(text);
        while (matcher.find()) {
            dlNumbers.add(matcher.group().trim());
        }
        
        return dlNumbers;
    }

    /**
     * Extracts potential passport numbers from text.
     */
    private static List<String> extractPassportNumbers(String text) {
        List<String> passportNumbers = new ArrayList<>();
        
        // Pattern for Indian passport numbers
        Pattern passportPattern = Pattern.compile(
            "\\b[A-Za-z]\\d{7}\\b|\\b[A-Za-z]{2}\\d{6}\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = passportPattern.matcher(text);
        while (matcher.find()) {
            passportNumbers.add(matcher.group().toUpperCase());
        }
        
        return passportNumbers;
    }

    /**
     * Extracts potential voter ID numbers from text.
     */
    private static List<String> extractVoterIDNumbers(String text) {
        List<String> voterNumbers = new ArrayList<>();
        
        // Pattern for voter ID/EPIC numbers
        Pattern voterPattern = Pattern.compile(
            "\\b[A-Za-z]{3}\\d{7}\\b|\\bEPIC\\s*:?\\s*[A-Za-z]{3}\\d{7}\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = voterPattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group().toUpperCase();
            // Extract just the ID part if it includes "EPIC:"
            if (match.contains("EPIC")) {
                match = match.replaceAll("EPIC\\s*:?\\s*", "");
            }
            voterNumbers.add(match.trim());
        }
        
        return voterNumbers;
    }

    /**
     * Validates Aadhaar number format and basic checks.
     */
    private static boolean isValidAadhaarNumber(String aadhaar) {
        if (aadhaar == null || aadhaar.length() != 12) {
            return false;
        }
        
        // Check if all digits
        if (!aadhaar.matches("\\d{12}")) {
            return false;
        }
        
        // Check if all digits are the same (invalid)
        char firstDigit = aadhaar.charAt(0);
        boolean allSame = true;
        for (char c : aadhaar.toCharArray()) {
            if (c != firstDigit) {
                allSame = false;
                break;
            }
        }
        
        return !allSame;
    }

    /**
     * Validates PAN number format.
     */
    private static boolean isValidPANNumber(String pan) {
        if (pan == null || pan.length() != 10) {
            return false;
        }
        
        // PAN format: ABCDE1234F (5 letters, 4 digits, 1 letter)
        return pan.matches("[A-Z]{5}[0-9]{4}[A-Z]{1}");
    }

    /**
     * Extracts keywords and phrases that might indicate document type.
     */
    public static Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        
        if (text == null || text.trim().isEmpty()) {
            return keywords;
        }

        String cleanText = cleanAndNormalize(text);
        
        // Split into words and phrases
        String[] words = cleanText.split("\\s+");
        
        // Add individual words
        for (String word : words) {
            if (word.length() > 2) { // Skip very short words
                keywords.add(word);
            }
        }
        
        // Add common phrases (2-3 word combinations)
        for (int i = 0; i < words.length - 1; i++) {
            String phrase = words[i] + " " + words[i + 1];
            keywords.add(phrase);
            
            if (i < words.length - 2) {
                String threePhrases = phrase + " " + words[i + 2];
                keywords.add(threePhrases);
            }
        }
        
        return keywords;
    }

    /**
     * Calculates text similarity between two strings using Jaccard similarity.
     */
    public static double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        Set<String> words1 = extractKeywords(text1);
        Set<String> words2 = extractKeywords(text2);
        
        if (words1.isEmpty() && words2.isEmpty()) {
            return 1.0;
        }
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return (double) intersection.size() / union.size();
    }

    /**
     * Checks if text contains any of the specified patterns.
     */
    public static boolean containsPattern(String text, Pattern... patterns) {
        if (text == null || patterns == null) {
            return false;
        }
        
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Extracts numeric sequences from text that might be document numbers.
     */
    public static List<String> extractNumericSequences(String text) {
        List<String> sequences = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return sequences;
        }
        
        // Pattern for sequences of digits (4 or more)
        Pattern numericPattern = Pattern.compile("\\b\\d{4,}\\b");
        java.util.regex.Matcher matcher = numericPattern.matcher(text);
        
        while (matcher.find()) {
            sequences.add(matcher.group());
        }
        
        return sequences;
    }

    /**
     * Extracts alphanumeric sequences that might be document identifiers.
     */
    public static List<String> extractAlphanumericSequences(String text) {
        List<String> sequences = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return sequences;
        }
        
        // Pattern for alphanumeric sequences (6 or more characters)
        Pattern alphanumericPattern = Pattern.compile("\\b[A-Za-z0-9]{6,}\\b");
        java.util.regex.Matcher matcher = alphanumericPattern.matcher(text);
        
        while (matcher.find()) {
            sequences.add(matcher.group().toUpperCase());
        }
        
        return sequences;
    }
}
