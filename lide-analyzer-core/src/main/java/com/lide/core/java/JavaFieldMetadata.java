package com.lide.core.java;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents metadata discovered for a single Java-backed field that can enrich JSP descriptors.
 */
public final class JavaFieldMetadata {

    private final String className;
    private final String fieldName;
    private final String fieldType;
    private final List<String> constraints;
    private final Map<String, Object> attributes;

    private JavaFieldMetadata(Builder builder) {
        this.className = builder.className;
        this.fieldName = builder.fieldName;
        this.fieldType = builder.fieldType;
        this.constraints = List.copyOf(builder.constraints);
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public String getClassName() {
        return className;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "JavaFieldMetadata{" +
                "className='" + className + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", fieldType='" + fieldType + '\'' +
                ", constraints=" + constraints +
                ", attributes=" + attributes +
                '}';
    }

    /**
     * Builder for {@link JavaFieldMetadata} to accumulate constraint/attribute information from
     * multiple declaration points (fields, getters, setters).
     */
    public static final class Builder {

        private final String className;
        private final String fieldName;
        private String fieldType;
        private final List<String> constraints = new java.util.ArrayList<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        public Builder(String className, String fieldName) {
            this.className = Objects.requireNonNull(className, "className");
            this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        }

        public Builder fieldType(String fieldType) {
            if (fieldType != null && !fieldType.isBlank()) {
                this.fieldType = fieldType;
            }
            return this;
        }

        public Builder addConstraint(String constraint) {
            if (constraint != null && !constraint.isBlank() && !constraints.contains(constraint)) {
                constraints.add(constraint);
            }
            return this;
        }

        public Builder putAttribute(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                attributes.putIfAbsent(key, value);
            }
            return this;
        }

        public JavaFieldMetadata build() {
            return new JavaFieldMetadata(this);
        }
    }
}
