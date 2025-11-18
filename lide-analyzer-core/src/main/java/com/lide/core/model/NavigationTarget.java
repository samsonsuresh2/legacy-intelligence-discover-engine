package com.lide.core.model;

import java.util.Objects;

/**
 * Represents a navigation target discovered during analysis.
 */
public class NavigationTarget {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String targetPage;
    private String sourcePattern;
    private String snippet;
    private String confidence;

    public NavigationTarget() {
        // Default constructor
    }

    public NavigationTarget(String targetPage, String sourcePattern, String snippet, String confidence) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NavigationTarget that = (NavigationTarget) o;
        return Objects.equals(targetPage, that.targetPage)
                && Objects.equals(sourcePattern, that.sourcePattern)
                && Objects.equals(snippet, that.snippet)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetPage, sourcePattern, snippet, confidence);
    }
}
