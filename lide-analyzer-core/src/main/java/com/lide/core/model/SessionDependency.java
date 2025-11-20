package com.lide.core.model;

/**
 * Represents a dependency on session state.
 */
public class SessionDependency {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String key;
    private String source;
    private String snippet;
    private String confidence;

    public SessionDependency() {
        // Default constructor
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
