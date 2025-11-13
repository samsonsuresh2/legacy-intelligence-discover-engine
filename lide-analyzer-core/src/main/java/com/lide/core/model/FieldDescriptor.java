package com.lide.core.model;

import java.util.List;

/**
 * Describes an input control within a form.
 */
public class FieldDescriptor {

    private String name; // TODO: capture field name attribute
    private String id; // TODO: capture field identifier attribute
    private String label; // TODO: capture human-readable label
    private String type; // TODO: capture input type (text, select, checkbox, etc.)
    private boolean required; // TODO: infer required attribute or validation
    private Integer maxLength; // TODO: infer max length constraint when available
    private Integer minLength; // TODO: capture minimum length constraint when available
    private String pattern; // TODO: derive regex patterns or validation hints
    private String placeholder; // TODO: capture placeholder hints
    private String defaultValue; // TODO: capture default value attribute
    private List<OptionDescriptor> options; // TODO: map dropdown/select options
    private List<String> bindingExpressions; // TODO: capture dynamic binding expressions
    private String minValue; // TODO: handle numeric/date minimums
    private String maxValue; // TODO: handle numeric/date maximums
    private String sourceTagName; // TODO: record originating tag name (input/select/etc.)
    private String javaType; // TODO: reflect Java type when resolved from metadata
    private List<String> constraints; // TODO: merge validation constraints from Java metadata
    private String sourceBeanClass; // TODO: indicate the resolved bean that backs this field
    private String sourceBeanProperty; // TODO: indicate the property name within the bean
    private List<String> notes; // TODO: capture diagnostic notes for the field

    public FieldDescriptor() {
        // Default constructor.
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<OptionDescriptor> getOptions() {
        return options;
    }

    public void setOptions(List<OptionDescriptor> options) {
        this.options = options;
    }

    public List<String> getBindingExpressions() {
        return bindingExpressions;
    }

    public void setBindingExpressions(List<String> bindingExpressions) {
        this.bindingExpressions = bindingExpressions;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public String getSourceTagName() {
        return sourceTagName;
    }

    public void setSourceTagName(String sourceTagName) {
        this.sourceTagName = sourceTagName;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    public String getSourceBeanClass() {
        return sourceBeanClass;
    }

    public void setSourceBeanClass(String sourceBeanClass) {
        this.sourceBeanClass = sourceBeanClass;
    }

    public String getSourceBeanProperty() {
        return sourceBeanProperty;
    }

    public void setSourceBeanProperty(String sourceBeanProperty) {
        this.sourceBeanProperty = sourceBeanProperty;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
