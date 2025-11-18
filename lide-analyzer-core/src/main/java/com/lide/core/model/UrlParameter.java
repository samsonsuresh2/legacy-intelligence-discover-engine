package com.lide.core.model;

/**
 * Represents a URL parameter hint.
 */
public class UrlParameter {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String name;
    private String source;
    private String snippet;
    private String confidence;

    public UrlParameter() {
        // Default constructor
    }

    public UrlParameter(String name, String source, String snippet, String confidence) {
        this.name = name;
        this.source = source;
        this.snippet = snippet;
        this.confidence = confidence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
