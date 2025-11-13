package com.lide.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lide.core.config.AnalyzerConfig;
import com.lide.core.java.JavaFieldMetadata;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultJsonSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void mergesJavaMetadataIntoFieldDescriptors() throws Exception {
        PageDescriptor page = new PageDescriptor();
        page.setPageId("customer/searchCustomer.jsp");
        page.setSourcePath(tempDir.resolve("customer/searchCustomer.jsp"));
        page.setTitle("Customer Search");
        page.setNotes(new ArrayList<>());

        FormDescriptor form = new FormDescriptor();
        form.setFormId("searchForm");
        form.setAction("/customerSearch.do");
        form.setMethod("POST");
        form.setNotes(new ArrayList<>());

        FieldDescriptor field = new FieldDescriptor();
        field.setName("customerId");
        field.setLabel("Customer ID");
        field.setType("text");
        field.setRequired(false);
        field.setConstraints(new ArrayList<>());
        field.setNotes(new ArrayList<>());
        field.setBindingExpressions(List.of("${customer.id}"));

        form.setFields(new ArrayList<>(List.of(field)));
        page.setForms(new ArrayList<>(List.of(form)));
        page.setOutputs(new ArrayList<>());

        JavaFieldMetadata metadata = new JavaFieldMetadata.Builder("com.example.CustomerForm", "customerId")
                .fieldType("String")
                .addConstraint("required")
                .putAttribute("required", Boolean.TRUE)
                .putAttribute("maxLength", 10)
                .build();

        Map<String, List<JavaFieldMetadata>> fieldsByClass = new LinkedHashMap<>();
        fieldsByClass.put("com.example.CustomerForm", List.of(metadata));

        JavaMetadataIndex index = new JavaMetadataIndex(fieldsByClass, Map.of(), Set.of(),
                Set.of("com.example.CustomerAction"), Set.of());

        AnalyzerConfig config = AnalyzerConfig.defaultConfig();
        config.normalize();
        JsonSchemaGenerator generator = new DefaultJsonSchemaGenerator(config);
        Path outputDir = tempDir.resolve("out");
        generator.generate(tempDir, outputDir, List.of(page), index);

        Path jsonPath = outputDir.resolve("customer/searchCustomer.jsp.json");
        Map<?, ?> document = mapper.readValue(Files.readString(jsonPath), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> formJson = ((List<Map<String, Object>>) document.get("forms")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldJson = ((List<Map<String, Object>>) formJson.get("fields")).get(0);

        assertEquals("com.example.CustomerForm", formJson.get("backingBeanClass"));
        assertTrue((Boolean) fieldJson.get("required"));
        assertEquals(10, fieldJson.get("maxLength"));
        assertEquals("String", fieldJson.get("javaType"));
        assertEquals("com.example.CustomerForm", fieldJson.get("sourceBeanClass"));
        assertEquals("customerId", fieldJson.get("sourceBeanProperty"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        @SuppressWarnings("unchecked")
        List<String> controllerCandidates = (List<String>) metadata.get("controllerCandidates");
        assertTrue(controllerCandidates.contains("com.example.CustomerAction"));
        assertEquals("HIGH", metadata.get("confidence"));
        @SuppressWarnings("unchecked")
        List<String> beanCandidates = (List<String>) metadata.get("backingBeanCandidates");
        assertTrue(beanCandidates.contains("com.example.CustomerForm"));

        Path summaryPath = outputDir.resolve("summary.json");
        Map<?, ?> summary = mapper.readValue(Files.readString(summaryPath), Map.class);
        assertEquals(1, summary.get("pageCount"));
    }
}
