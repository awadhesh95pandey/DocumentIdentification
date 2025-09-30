package com.documentclassifier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ClassificationResult {
    
    @JsonProperty
    private Map<String, String> results;
    
    public ClassificationResult() {
    }
    
    public ClassificationResult(Map<String, String> results) {
        this.results = results;
    }
    
    public Map<String, String> getResults() {
        return results;
    }
    
    public void setResults(Map<String, String> results) {
        this.results = results;
    }
}
