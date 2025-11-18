package com.lide.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.extractors.UrlParameterExtractor;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.java.DefaultJavaUsageAnalyzer;
import com.lide.core.java.JavaFieldMetadata;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.java.JavaUsageAnalyzer;
import com.lide.core.jsp.DefaultNavigationTargetExtractor;
import com.lide.core.jsp.DefaultUrlParameterExtractor;
import com.lide.core.jsp.DefaultJspAnalyzer;
import com.lide.core.jsp.JspAnalyzer;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDescriptor;
import com.lide.core.report.DefaultJsonSchemaGenerator;
import com.lide.core.report.JsonSchemaGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerFixturesTest {

    private Path tempRoot;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("lide-fixtures");
        outputDir = Files.createTempDirectory("lide-output");
    }

    @Test
    void parsesFormsAndOutputsFromSampleJsp() throws IOException {
        Path jsp = copyFixture("customer/searchCustomer.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jsp);

        JspAnalyzer analyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = analyzer.analyze(tempRoot, index);

        NavigationTargetExtractor navigationTargetExtractor = new DefaultNavigationTargetExtractor();
        navigationTargetExtractor.extract(tempRoot, pages);

        UrlParameterExtractor urlParameterExtractor = new DefaultUrlParameterExtractor();
        urlParameterExtractor.extract(tempRoot, pages);

        assertEquals(1, pages.size());
        PageDescriptor page = pages.get(0);
        assertEquals("customer/searchCustomer.jsp", page.getPageId());
        assertEquals(3, page.getNavigationTargets().size());

        FormDescriptor form = assertSingleForm(page);
        assertEquals("searchForm", form.getFormId());
        assertEquals("/customerSearch.do", form.getAction());
        assertEquals("POST", form.getMethod());

        FieldDescriptor customerId = findField(form, "customerId");
        assertTrue(customerId.isRequired());
        assertEquals(10, customerId.getMaxLength());
        assertEquals("text", customerId.getType());

        FieldDescriptor status = findField(form, "status");
        assertEquals("select", status.getType());
        assertEquals(3, status.getOptions().size());

        FieldDescriptor age = findField(form, "age");
        assertEquals("number", age.getType());
        assertEquals("18", age.getMinValue());
        assertEquals("120", age.getMaxValue());

        List<OutputSectionDescriptor> outputs = page.getOutputs();
        assertEquals(2, outputs.size(), "Table and inline text output should be detected");
        OutputSectionDescriptor table = outputs.stream()
                .filter(section -> "TABLE".equals(section.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals("customerTable", table.getSectionId());
        assertEquals(3, table.getFields().size());
        assertEquals("customer.id", table.getFields().get(0).getBindingExpression());
    }

    @Test
    void extractsJavaMetadataFromFixtures() throws IOException {
        Path formFile = copyFixture("java/CustomerSearchForm.java");
        Path controllerFile = copyFixture("java/CustomerController.java");

        CodebaseIndex index = new CodebaseIndex();
        index.addJavaFile(formFile);
        index.addJavaFile(controllerFile);

        JavaUsageAnalyzer analyzer = new DefaultJavaUsageAnalyzer();
        JavaMetadataIndex metadata = analyzer.analyze(index);

        List<JavaFieldMetadata> fields = metadata.getFieldsForClass("com.example.legacy.forms.CustomerSearchForm");
        assertEquals(3, fields.size());

        JavaFieldMetadata customerId = fields.stream()
                .filter(f -> f.getFieldName().equals("customerId"))
                .findFirst()
                .orElseThrow();
        assertTrue(customerId.getConstraints().contains("required"));
        assertEquals(10, customerId.getAttributes().get("maxLength"));

        JavaFieldMetadata age = fields.stream()
                .filter(f -> f.getFieldName().equals("age"))
                .findFirst()
                .orElseThrow();
        assertEquals(18, age.getAttributes().get("min"));
        assertEquals(120, age.getAttributes().get("max"));

        assertTrue(metadata.getControllerClasses().contains("com.example.legacy.controller.CustomerController"));
        assertFalse(metadata.getHandlerMethodsByController().getOrDefault(
                "com.example.legacy.controller.CustomerController", List.of()).isEmpty());
    }

    @Test
    void generatesJsonOutputForFixtures() throws Exception {
        Path jsp = copyFixture("customer/searchCustomer.jsp");
        Path formFile = copyFixture("java/CustomerSearchForm.java");
        Path controllerFile = copyFixture("java/CustomerController.java");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jsp);
        index.addJavaFile(formFile);
        index.addJavaFile(controllerFile);

        JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = jspAnalyzer.analyze(tempRoot, index);

        NavigationTargetExtractor navigationTargetExtractor = new DefaultNavigationTargetExtractor();
        navigationTargetExtractor.extract(tempRoot, pages);

        UrlParameterExtractor urlParameterExtractor = new DefaultUrlParameterExtractor();
        urlParameterExtractor.extract(tempRoot, pages);

        JavaUsageAnalyzer javaAnalyzer = new DefaultJavaUsageAnalyzer();
        JavaMetadataIndex javaMetadata = javaAnalyzer.analyze(index);

        JsonSchemaGenerator generator = new DefaultJsonSchemaGenerator();
        generator.generate(tempRoot, outputDir, pages, javaMetadata);

        Path pageJson = outputDir.resolve("customer/searchCustomer.jsp.json");
        assertTrue(Files.exists(pageJson));

        Map<?, ?> json = new ObjectMapper().readValue(pageJson.toFile(), Map.class);
        assertEquals("customer/searchCustomer.jsp", json.get("pageId"));

        List<Map<?, ?>> forms = (List<Map<?, ?>>) json.get("forms");
        assertEquals(1, forms.size());
        Map<?, ?> form = forms.get(0);
        assertEquals("com.example.legacy.forms.CustomerSearchForm", form.get("backingBeanClass"));

        List<Map<?, ?>> fields = (List<Map<?, ?>>) form.get("fields");
        Map<?, ?> idField = fields.stream()
                .filter(map -> "customerId".equals(map.get("name")))
                .findFirst()
                .orElseThrow();
        assertEquals(10, idField.get("maxLength"));
        List<?> constraints = (List<?>) idField.get("constraints");
        assertTrue(constraints.contains("required"));

        List<Map<?, ?>> navigationTargets = (List<Map<?, ?>>) json.get("navigationTargets");
        assertEquals(3, navigationTargets.size());

        Path summary = outputDir.resolve("summary.json");
        assertTrue(Files.exists(summary));
    }

    private Path copyFixture(String relativePath) throws IOException {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/" + relativePath)) {
            assertNotNull(in, "Missing fixture: " + relativePath);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private FormDescriptor assertSingleForm(PageDescriptor page) {
        List<FormDescriptor> forms = page.getForms();
        assertNotNull(forms);
        assertEquals(1, forms.size());
        return forms.get(0);
    }

    private FieldDescriptor findField(FormDescriptor form, String name) {
        Optional<FieldDescriptor> match = form.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst();
        assertTrue(match.isPresent(), "Expected field named " + name);
        return match.get();
    }
}
