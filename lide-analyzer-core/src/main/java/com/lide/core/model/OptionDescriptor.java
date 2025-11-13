package com.lide.core.model;

/**
 * Represents a selectable option for dropdowns, radio buttons, or similar controls.
 */
public class OptionDescriptor {

    private String value; // TODO: capture underlying value attribute
    private String label; // TODO: capture rendered label
    private boolean selected; // TODO: indicate default selection state

    public OptionDescriptor() {
        // Default constructor.
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
