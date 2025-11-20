package com.lide.core.model;

/**
 * Represents an interaction between frames.
 */
public class CrossFrameInteraction {

    public static final String TYPE_LOCATION_CHANGE = "locationChange";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";

    private String fromFrame;
    private String toJsp;
    private String type;
    private String snippet;
    private String confidence;

    public String getFromFrame() {
        return fromFrame;
    }

    public void setFromFrame(String fromFrame) {
        this.fromFrame = fromFrame;
    }

    public String getToJsp() {
        return toJsp;
    }

    public void setToJsp(String toJsp) {
        this.toJsp = toJsp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
