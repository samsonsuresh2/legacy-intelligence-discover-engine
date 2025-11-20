package com.lide.core.java;

import com.lide.core.fs.CodebaseIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultJavaUsageAnalyzerTest {

    private final JavaUsageAnalyzer analyzer = new DefaultJavaUsageAnalyzer();

    @TempDir
    Path tempDir;

    @Test
    void capturesStrutsFormFieldsAndConstraints() throws Exception {
        String javaSource = """
                package com.example.forms;

                import org.apache.struts.action.ActionForm;
                import jakarta.validation.constraints.NotNull;
                import jakarta.validation.constraints.Size;

                public class CustomerForm extends ActionForm {
                    @NotNull
                    @Size(max = 10)
                    private String customerId;

                    public String getCustomerId() {
                        return customerId;
                    }

                    public void setCustomerId(String customerId) {
                        this.customerId = customerId;
                    }
                }
                """;

        Path javaPath = tempDir.resolve("com/example/forms/CustomerForm.java");
        Files.createDirectories(javaPath.getParent());
        Files.writeString(javaPath, javaSource, StandardCharsets.UTF_8);

        CodebaseIndex index = new CodebaseIndex();
        index.addJavaFile(javaPath);

        JavaMetadataIndex metadataIndex = analyzer.analyze(index);

        assertTrue(metadataIndex.getStrutsFormClasses().contains("com.example.forms.CustomerForm"));
        List<JavaFieldMetadata> fields = metadataIndex.getFieldsForClass("com.example.forms.CustomerForm");
        assertEquals(1, fields.size());

        JavaFieldMetadata field = fields.get(0);
        assertEquals("customerId", field.getFieldName());
        assertEquals("String", field.getFieldType());
        assertTrue(field.getConstraints().contains("required"));
        assertEquals(10L, field.getAttributes().get("maxLength"));
    }

    @Test
    void capturesControllerHandlersAndNestedDtoMetadata() throws Exception {
        String javaSource = """
                package com.example.web;

                import jakarta.validation.constraints.Size;
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.ModelAttribute;

                @Controller
                public class CustomerController {

                    @GetMapping(path = "/customers")
                    public String listCustomers(@ModelAttribute("search") SearchForm form) {
                        return "ok";
                    }

                    public static class SearchForm {
                        @Size(min = 1, max = 5)
                        private String query;

                        public String getQuery() {
                            return query;
                        }

                        public void setQuery(String query) {
                            this.query = query;
                        }
                    }
                }
                """;

        Path javaPath = tempDir.resolve("com/example/web/CustomerController.java");
        Files.createDirectories(javaPath.getParent());
        Files.writeString(javaPath, javaSource, StandardCharsets.UTF_8);

        CodebaseIndex index = new CodebaseIndex();
        index.addJavaFile(javaPath);

        JavaMetadataIndex metadataIndex = analyzer.analyze(index);

        assertTrue(metadataIndex.getControllerClasses().contains("com.example.web.CustomerController"));
        List<JavaMetadataIndex.HandlerMethodMetadata> handlerMethods =
                metadataIndex.getHandlerMethodsByController().get("com.example.web.CustomerController");
        assertNotNull(handlerMethods);
        assertEquals(1, handlerMethods.size());
        JavaMetadataIndex.HandlerMethodMetadata handler = handlerMethods.get(0);
        assertTrue(handler.getPaths().contains("/customers"));
        assertTrue(handler.getHttpMethods().contains("GET"));

        JavaMetadataIndex.HandlerParameterMetadata parameter = handler.getParameters().get(0);
        assertEquals("form", parameter.getParameterName());
        assertTrue(parameter.getAnnotations().contains("ModelAttribute"));

        List<JavaFieldMetadata> nestedFields =
                metadataIndex.getFieldsForClass("com.example.web.CustomerController.SearchForm");
        assertEquals(1, nestedFields.size());
        JavaFieldMetadata queryField = nestedFields.get(0);
        assertEquals("query", queryField.getFieldName());
        assertTrue(queryField.getConstraints().contains("size"));
        assertEquals(1L, queryField.getAttributes().get("minLength"));
        assertEquals(5L, queryField.getAttributes().get("maxLength"));
    }
}
