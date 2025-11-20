package com.lide.core.model;

/**
 * Represents a hidden field within a legacy page.
 */
public class HiddenField {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String name;
    private String defaultValue;
    private String expression;
    private String snippet;
    private String confidence;

    public HiddenField() {
        // Default constructor
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
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
