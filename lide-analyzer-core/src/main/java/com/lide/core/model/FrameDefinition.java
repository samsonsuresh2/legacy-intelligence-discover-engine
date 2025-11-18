package com.lide.core.model;

/**
 * Represents a frame definition within a page.
 */
public class FrameDefinition {

    public static final String CONFIDENCE_HIGH = "HIGH";

    private String frameName;
    private String source;
    private String parentFrameName;
    private Integer depth;
    private String tag;
    private String confidence;

    public String getFrameName() {
        return frameName;
    }

    public void setFrameName(String frameName) {
        this.frameName = frameName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getParentFrameName() {
        return parentFrameName;
    }

    public void setParentFrameName(String parentFrameName) {
        this.parentFrameName = parentFrameName;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
