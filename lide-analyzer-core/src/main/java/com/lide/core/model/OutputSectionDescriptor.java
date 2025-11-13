package com.lide.core.model;

/**
 * Represents an output section such as a rendered table, list, or inline text block.
 */
public class OutputSectionDescriptor {

    private String sectionId; // TODO: capture DOM identifier when available
    private String type; // TODO: capture structure type such as TABLE, LIST, TEXT_BLOCK
    private String itemVariable; // TODO: store iteration variable (e.g., loan)
    private String itemsExpression; // TODO: capture collection binding (e.g., loanList)
    private java.util.List<OutputFieldDescriptor> fields; // TODO: describe rendered columns/items
    private java.util.List<String> notes; // TODO: diagnostics about extraction confidence

    public OutputSectionDescriptor() {
        // Default constructor.
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getItemVariable() {
        return itemVariable;
    }

    public void setItemVariable(String itemVariable) {
        this.itemVariable = itemVariable;
    }

    public String getItemsExpression() {
        return itemsExpression;
    }

    public void setItemsExpression(String itemsExpression) {
        this.itemsExpression = itemsExpression;
    }

    public java.util.List<OutputFieldDescriptor> getFields() {
        return fields;
    }

    public void setFields(java.util.List<OutputFieldDescriptor> fields) {
        this.fields = fields;
    }

    public java.util.List<String> getNotes() {
        return notes;
    }

    public void setNotes(java.util.List<String> notes) {
        this.notes = notes;
    }
}
