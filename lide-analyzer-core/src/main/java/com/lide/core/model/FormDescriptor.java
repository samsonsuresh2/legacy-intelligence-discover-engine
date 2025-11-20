package com.lide.core.model;

import java.util.List;

/**
 * Captures metadata about a single HTML form element.
 */
public class FormDescriptor {

    private String formId; // TODO: derive from id/name attributes
    private String action; // TODO: extract form action
    private String method; // TODO: extract HTTP method with sensible default
    private String backingBeanClassName; // TODO: resolve backing bean or command object
    private List<FieldDescriptor> fields; // TODO: populate with discovered form fields
    private List<String> notes; // TODO: include notes or uncertainties discovered during analysis

    public FormDescriptor() {
        // Default constructor.
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getBackingBeanClassName() {
        return backingBeanClassName;
    }

    public void setBackingBeanClassName(String backingBeanClassName) {
        this.backingBeanClassName = backingBeanClassName;
    }

    public List<FieldDescriptor> getFields() {
        return fields;
    }

    public void setFields(List<FieldDescriptor> fields) {
        this.fields = fields;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
