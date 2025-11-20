package com.lide.core.model;

/**
 * Represents a single value displayed within an output section such as a table column or text binding.
 */
public class OutputFieldDescriptor {

    private String name; // TODO: capture logical field name (derived from binding)
    private String label; // TODO: capture user-facing column or field label
    private String bindingExpression; // TODO: capture expression used to render the value
    private String rawText; // TODO: record literal text content when expression is absent
    private java.util.List<String> notes; // TODO: include diagnostic extraction notes

    public OutputFieldDescriptor() {
        // Default constructor.
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBindingExpression() {
        return bindingExpression;
    }

    public void setBindingExpression(String bindingExpression) {
        this.bindingExpression = bindingExpression;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public java.util.List<String> getNotes() {
        return notes;
    }

    public void setNotes(java.util.List<String> notes) {
        this.notes = notes;
    }
}
