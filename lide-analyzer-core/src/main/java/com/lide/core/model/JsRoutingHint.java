package com.lide.core.model;

/**
 * Represents a JavaScript routing hint detected during analysis.
 */
public class JsRoutingHint {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String targetPage;
    private String sourcePattern;
    private String snippet;
    private String confidence;

    public JsRoutingHint() {
        // default constructor
    }

    public JsRoutingHint(String targetPage, String sourcePattern, String snippet, String confidence) {
        this.targetPage = targetPage;
        this.sourcePattern = sourcePattern;
        this.snippet = snippet;
        this.confidence = confidence;
    }

    public String getTargetPage() {
        return targetPage;
    }

    public void setTargetPage(String targetPage) {
        this.targetPage = targetPage;
    }

    public String getSourcePattern() {
        return sourcePattern;
    }

    public void setSourcePattern(String sourcePattern) {
        this.sourcePattern = sourcePattern;
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
