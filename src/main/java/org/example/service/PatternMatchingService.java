package org.example.service;

import org.example.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for pattern-based document classification using regex patterns and keyword matching.
 * Provides comprehensive pattern recognition for Indian identity documents and other document types.
 */
@Service
public class PatternMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(PatternMatchingService.class);

    // Aadhaar Card Patterns
    private static final Pattern AADHAAR_PATTERN = Pattern.compile(
        "\\b\\d{4}\\s*\\d{4}\\s*\\d{4}\\b|\\b\\d{12}\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> AADHAAR_KEYWORDS = Set.of(
        "aadhaar", "aadhar", "government of india", "unique identification", "uid", "uidai",
        "भारत सरकार", "आधार", "विशिष्ट पहचान"
    );

    // PAN Card Patterns
    private static final Pattern PAN_PATTERN = Pattern.compile(
        "\\b[A-Z]{5}[0-9]{4}[A-Z]{1}\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> PAN_KEYWORDS = Set.of(
        "pan", "permanent account number", "income tax department", "govt of india",
        "पैन", "स्थायी खाता संख्या", "आयकर विभाग"
    );

    // Driving License Patterns
    private static final Pattern DL_PATTERN = Pattern.compile(
        "\\b[A-Z]{2}[-\\s]?\\d{2}[-\\s]?\\d{4}[-\\s]?\\d{7}\\b|\\bDL[-\\s]?\\d{14}\\b", 
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> DL_KEYWORDS = Set.of(
        "driving licence", "driving license", "transport", "motor vehicle", "dl no", "license no",
        "ड्राइविंग लाइसेंस", "चालक अनुज्ञप्ति", "परिवहन विभाग"
    );

    // Passport Patterns
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
        "\\b[A-Z]\\d{7}\\b|\\b[A-Z]{2}\\d{6}\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> PASSPORT_KEYWORDS = Set.of(
        "passport", "republic of india", "ministry of external affairs", "passport no",
        "पासपोर्ट", "भारत गणराज्य", "विदेश मंत्रालय"
    );

    // Voter ID Patterns
    private static final Pattern VOTER_ID_PATTERN = Pattern.compile(
        "\\b[A-Z]{3}\\d{7}\\b|\\bEPIC\\s*:?\\s*[A-Z]{3}\\d{7}\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> VOTER_ID_KEYWORDS = Set.of(
        "voter", "election commission", "epic", "electoral photo identity card", "voter id",
        "मतदाता", "चुनाव आयोग", "मतदाता पहचान पत्र"
    );

    // Bank Statement Keywords
    private static final Set<String> BANK_STATEMENT_KEYWORDS = Set.of(
        "bank statement", "account statement", "transaction", "balance", "debit", "credit",
        "account number", "ifsc", "branch", "बैंक स्टेटमेंट", "खाता विवरण"
    );

    // Utility Bill Keywords
    private static final Set<String> UTILITY_BILL_KEYWORDS = Set.of(
        "electricity bill", "water bill", "gas bill", "telephone bill", "mobile bill",
        "utility bill", "consumer number", "meter reading", "बिजली बिल", "पानी बिल"
    );

    /**
     * Result of pattern matching analysis
     */
    public static class PatternMatchResult {
        private final DocumentType documentType;
        private final double confidence;
        private final String reasoning;
        private final List<String> matchedPatterns;
        private final List<String> matchedKeywords;
        private final List<String> extractedIdentifiers;

        public PatternMatchResult(DocumentType documentType, double confidence, String reasoning,
                                List<String> matchedPatterns, List<String> matchedKeywords,
                                List<String> extractedIdentifiers) {
            this.documentType = documentType;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.matchedPatterns = matchedPatterns != null ? matchedPatterns : new ArrayList<>();
            this.matchedKeywords = matchedKeywords != null ? matchedKeywords : new ArrayList<>();
            this.extractedIdentifiers = extractedIdentifiers != null ? extractedIdentifiers : new ArrayList<>();
        }

        // Getters
        public DocumentType getDocumentType() { return documentType; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public List<String> getMatchedPatterns() { return matchedPatterns; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }
        public List<String> getExtractedIdentifiers() { return extractedIdentifiers; }
    }

    /**
     * Analyzes text content using pattern matching to identify document type.
     */
    public PatternMatchResult analyzeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, 
                "No content provided for analysis", null, null, null);
        }

        String normalizedContent = content.toLowerCase().trim();
        
        // Check each document type in order of specificity
        PatternMatchResult result;
        
        // Check Aadhaar Card
        result = checkAadhaarCard(normalizedContent);
        if (result.confidence > 0.7) return result;
        
        // Check PAN Card
        result = checkPanCard(normalizedContent);
        if (result.confidence > 0.7) return result;
        
        // Check Driving License
        result = checkDrivingLicense(normalizedContent);
        if (result.confidence > 0.7) return result;
        
        // Check Passport
        result = checkPassport(normalizedContent);
        if (result.confidence > 0.7) return result;
        
        // Check Voter ID
        result = checkVoterID(normalizedContent);
        if (result.confidence > 0.7) return result;
        
        // Check other document types
        result = checkBankStatement(normalizedContent);
        if (result.confidence > 0.6) return result;
        
        result = checkUtilityBill(normalizedContent);
        if (result.confidence > 0.6) return result;
        
        // Return the best match found, or UNKNOWN if no good matches
        return new PatternMatchResult(DocumentType.OTHER, 0.2, 
            "No specific document patterns identified", null, null, null);
    }

    /**
     * Analyzes filename for document type hints.
     */
    public PatternMatchResult analyzeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, 
                "No filename provided", null, null, null);
        }

        String normalizedFilename = filename.toLowerCase();
        List<String> matchedKeywords = new ArrayList<>();
        
        // Check for document type keywords in filename
        if (containsAny(normalizedFilename, AADHAAR_KEYWORDS)) {
            matchedKeywords.addAll(findMatches(normalizedFilename, AADHAAR_KEYWORDS));
            return new PatternMatchResult(DocumentType.AADHAR_CARD, 0.8,
                "Filename contains Aadhaar-related keywords", null, matchedKeywords, null);
        }
        
        if (containsAny(normalizedFilename, PAN_KEYWORDS)) {
            matchedKeywords.addAll(findMatches(normalizedFilename, PAN_KEYWORDS));
            return new PatternMatchResult(DocumentType.PAN_CARD, 0.8,
                "Filename contains PAN-related keywords", null, matchedKeywords, null);
        }
        
        if (containsAny(normalizedFilename, DL_KEYWORDS)) {
            matchedKeywords.addAll(findMatches(normalizedFilename, DL_KEYWORDS));
            return new PatternMatchResult(DocumentType.DRIVING_LICENSE, 0.7,
                "Filename contains driving license keywords", null, matchedKeywords, null);
        }
        
        if (containsAny(normalizedFilename, PASSPORT_KEYWORDS)) {
            matchedKeywords.addAll(findMatches(normalizedFilename, PASSPORT_KEYWORDS));
            return new PatternMatchResult(DocumentType.PASSPORT, 0.7,
                "Filename contains passport keywords", null, matchedKeywords, null);
        }
        
        if (containsAny(normalizedFilename, VOTER_ID_KEYWORDS)) {
            matchedKeywords.addAll(findMatches(normalizedFilename, VOTER_ID_KEYWORDS));
            return new PatternMatchResult(DocumentType.VOTER_ID, 0.7,
                "Filename contains voter ID keywords", null, matchedKeywords, null);
        }

        return new PatternMatchResult(DocumentType.OTHER, 0.1,
            "No specific keywords found in filename", null, null, null);
    }

    private PatternMatchResult checkAadhaarCard(String content) {
        List<String> matchedPatterns = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();
        List<String> extractedIdentifiers = new ArrayList<>();
        
        // Check for Aadhaar number pattern
        Matcher matcher = AADHAAR_PATTERN.matcher(content);
        while (matcher.find()) {
            String aadhaarNumber = matcher.group().replaceAll("\\s", "");
            if (isValidAadhaarNumber(aadhaarNumber)) {
                matchedPatterns.add("12-digit Aadhaar number");
                extractedIdentifiers.add(aadhaarNumber);
            }
        }
        
        // Check for keywords
        matchedKeywords.addAll(findMatches(content, AADHAAR_KEYWORDS));
        
        double confidence = calculateConfidence(matchedPatterns.size(), matchedKeywords.size());
        
        if (confidence > 0.3) {
            String reasoning = String.format("Found %d Aadhaar number patterns and %d related keywords", 
                matchedPatterns.size(), matchedKeywords.size());
            return new PatternMatchResult(DocumentType.AADHAR_CARD, confidence, reasoning,
                matchedPatterns, matchedKeywords, extractedIdentifiers);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkPanCard(String content) {
        List<String> matchedPatterns = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();
        List<String> extractedIdentifiers = new ArrayList<>();
        
        // Check for PAN number pattern
        Matcher matcher = PAN_PATTERN.matcher(content);
        while (matcher.find()) {
            String panNumber = matcher.group().toUpperCase();
            if (isValidPanNumber(panNumber)) {
                matchedPatterns.add("PAN number format");
                extractedIdentifiers.add(panNumber);
            }
        }
        
        // Check for keywords
        matchedKeywords.addAll(findMatches(content, PAN_KEYWORDS));
        
        double confidence = calculateConfidence(matchedPatterns.size(), matchedKeywords.size());
        
        if (confidence > 0.3) {
            String reasoning = String.format("Found %d PAN number patterns and %d related keywords", 
                matchedPatterns.size(), matchedKeywords.size());
            return new PatternMatchResult(DocumentType.PAN_CARD, confidence, reasoning,
                matchedPatterns, matchedKeywords, extractedIdentifiers);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkDrivingLicense(String content) {
        List<String> matchedPatterns = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();
        List<String> extractedIdentifiers = new ArrayList<>();
        
        // Check for DL number pattern
        Matcher matcher = DL_PATTERN.matcher(content);
        while (matcher.find()) {
            matchedPatterns.add("Driving license number format");
            extractedIdentifiers.add(matcher.group());
        }
        
        // Check for keywords
        matchedKeywords.addAll(findMatches(content, DL_KEYWORDS));
        
        double confidence = calculateConfidence(matchedPatterns.size(), matchedKeywords.size());
        
        if (confidence > 0.3) {
            String reasoning = String.format("Found %d DL number patterns and %d related keywords", 
                matchedPatterns.size(), matchedKeywords.size());
            return new PatternMatchResult(DocumentType.DRIVING_LICENSE, confidence, reasoning,
                matchedPatterns, matchedKeywords, extractedIdentifiers);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkPassport(String content) {
        List<String> matchedPatterns = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();
        List<String> extractedIdentifiers = new ArrayList<>();
        
        // Check for passport number pattern
        Matcher matcher = PASSPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            matchedPatterns.add("Passport number format");
            extractedIdentifiers.add(matcher.group());
        }
        
        // Check for keywords
        matchedKeywords.addAll(findMatches(content, PASSPORT_KEYWORDS));
        
        double confidence = calculateConfidence(matchedPatterns.size(), matchedKeywords.size());
        
        if (confidence > 0.3) {
            String reasoning = String.format("Found %d passport number patterns and %d related keywords", 
                matchedPatterns.size(), matchedKeywords.size());
            return new PatternMatchResult(DocumentType.PASSPORT, confidence, reasoning,
                matchedPatterns, matchedKeywords, extractedIdentifiers);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkVoterID(String content) {
        List<String> matchedPatterns = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();
        List<String> extractedIdentifiers = new ArrayList<>();
        
        // Check for voter ID pattern
        Matcher matcher = VOTER_ID_PATTERN.matcher(content);
        while (matcher.find()) {
            matchedPatterns.add("Voter ID/EPIC number format");
            extractedIdentifiers.add(matcher.group());
        }
        
        // Check for keywords
        matchedKeywords.addAll(findMatches(content, VOTER_ID_KEYWORDS));
        
        double confidence = calculateConfidence(matchedPatterns.size(), matchedKeywords.size());
        
        if (confidence > 0.3) {
            String reasoning = String.format("Found %d voter ID patterns and %d related keywords", 
                matchedPatterns.size(), matchedKeywords.size());
            return new PatternMatchResult(DocumentType.VOTER_ID, confidence, reasoning,
                matchedPatterns, matchedKeywords, extractedIdentifiers);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkBankStatement(String content) {
        List<String> matchedKeywords = findMatches(content, BANK_STATEMENT_KEYWORDS);
        
        if (matchedKeywords.size() >= 2) {
            double confidence = Math.min(0.8, 0.3 + (matchedKeywords.size() * 0.1));
            String reasoning = String.format("Found %d bank statement related keywords", matchedKeywords.size());
            return new PatternMatchResult(DocumentType.BANK_STATEMENT, confidence, reasoning,
                null, matchedKeywords, null);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private PatternMatchResult checkUtilityBill(String content) {
        List<String> matchedKeywords = findMatches(content, UTILITY_BILL_KEYWORDS);
        
        if (matchedKeywords.size() >= 1) {
            double confidence = Math.min(0.7, 0.4 + (matchedKeywords.size() * 0.1));
            String reasoning = String.format("Found %d utility bill related keywords", matchedKeywords.size());
            return new PatternMatchResult(DocumentType.UTILITY_BILL, confidence, reasoning,
                null, matchedKeywords, null);
        }
        
        return new PatternMatchResult(DocumentType.UNKNOWN, 0.0, "", null, null, null);
    }

    private boolean isValidAadhaarNumber(String aadhaar) {
        if (aadhaar == null || aadhaar.length() != 12) {
            return false;
        }
        
        // Basic validation - all digits and not all same digit
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

    private boolean isValidPanNumber(String pan) {
        if (pan == null || pan.length() != 10) {
            return false;
        }
        
        // PAN format: ABCDE1234F (5 letters, 4 digits, 1 letter)
        return pan.matches("[A-Z]{5}[0-9]{4}[A-Z]{1}");
    }

    private double calculateConfidence(int patternMatches, int keywordMatches) {
        double confidence = 0.0;
        
        // Pattern matches are more valuable
        confidence += patternMatches * 0.4;
        
        // Keyword matches add to confidence
        confidence += keywordMatches * 0.2;
        
        // Cap at 0.9 for pattern-based classification
        return Math.min(0.9, confidence);
    }

    private boolean containsAny(String text, Set<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private List<String> findMatches(String text, Set<String> keywords) {
        return keywords.stream()
            .filter(text::contains)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
