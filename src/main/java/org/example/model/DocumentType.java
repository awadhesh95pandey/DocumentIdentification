package org.example.model;

/**
 * Enumeration of supported document types for classification.
 * Includes common Indian identity and official documents.
 */
public enum DocumentType {
    AADHAR_CARD("Aadhar Card", "Indian national identity card with 12-digit unique identification number"),
    PAN_CARD("PAN Card", "Permanent Account Number card for tax identification in India"),
    PASSPORT("Passport", "International travel document issued by government"),
    DRIVING_LICENSE("Driving License", "Official permit to operate motor vehicles"),
    VOTER_ID("Voter ID", "Electoral identity card for voting in elections"),
    RATION_CARD("Ration Card", "Document for accessing subsidized food grains"),
    BANK_STATEMENT("Bank Statement", "Financial document showing account transactions"),
    SALARY_SLIP("Salary Slip", "Document showing employee salary and deductions"),
    UTILITY_BILL("Utility Bill", "Bill for electricity, water, gas, or other utilities"),
    PROPERTY_DOCUMENT("Property Document", "Legal document related to property ownership"),
    INSURANCE_DOCUMENT("Insurance Document", "Policy or claim related insurance document"),
    MEDICAL_REPORT("Medical Report", "Healthcare or medical examination document"),
    EDUCATIONAL_CERTIFICATE("Educational Certificate", "Academic degree, diploma, or certificate"),
    EMPLOYMENT_LETTER("Employment Letter", "Job offer, experience, or employment verification letter"),
    BUSINESS_REGISTRATION("Business Registration", "Company incorporation or business license document"),
    TAX_DOCUMENT("Tax Document", "Income tax return, tax certificate, or related tax document"),
    LEGAL_DOCUMENT("Legal Document", "Court order, legal notice, or other legal paperwork"),
    INVOICE("Invoice", "Commercial invoice or bill for goods/services"),
    RECEIPT("Receipt", "Payment receipt or transaction proof"),
    CONTRACT("Contract", "Legal agreement or contract document"),
    FORM("Form", "Application form or official form document"),
    LETTER("Letter", "Formal or informal letter document"),
    REPORT("Report", "Business, technical, or analytical report"),
    PRESENTATION("Presentation", "Slide deck or presentation document"),
    SPREADSHEET("Spreadsheet", "Data table or calculation document"),
    OTHER("Other", "Document type not specifically classified"),
    UNKNOWN("Unknown", "Unable to determine document type");

    private final String displayName;
    private final String description;

    DocumentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get DocumentType from string value (case-insensitive).
     */
    public static DocumentType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedValue = value.trim().toLowerCase();
        
        // Direct enum name match
        for (DocumentType type : values()) {
            if (type.name().toLowerCase().equals(normalizedValue)) {
                return type;
            }
        }

        // Display name match
        for (DocumentType type : values()) {
            if (type.displayName.toLowerCase().equals(normalizedValue)) {
                return type;
            }
        }

        // Partial matches for common variations
        if (normalizedValue.contains("aadhar") || normalizedValue.contains("aadhaar")) {
            return AADHAR_CARD;
        }
        if (normalizedValue.contains("pan")) {
            return PAN_CARD;
        }
        if (normalizedValue.contains("passport")) {
            return PASSPORT;
        }
        if (normalizedValue.contains("driving") || normalizedValue.contains("license")) {
            return DRIVING_LICENSE;
        }
        if (normalizedValue.contains("voter")) {
            return VOTER_ID;
        }
        if (normalizedValue.contains("ration")) {
            return RATION_CARD;
        }
        if (normalizedValue.contains("bank") && normalizedValue.contains("statement")) {
            return BANK_STATEMENT;
        }
        if (normalizedValue.contains("salary") || normalizedValue.contains("payslip")) {
            return SALARY_SLIP;
        }
        if (normalizedValue.contains("utility") || normalizedValue.contains("bill")) {
            return UTILITY_BILL;
        }
        if (normalizedValue.contains("invoice")) {
            return INVOICE;
        }
        if (normalizedValue.contains("receipt")) {
            return RECEIPT;
        }

        return OTHER;
    }
}
