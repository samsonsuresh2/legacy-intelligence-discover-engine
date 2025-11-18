package com.lide.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.java.DefaultJavaUsageAnalyzer;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.java.JavaUsageAnalyzer;
import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.extractors.UrlParameterExtractor;
import com.lide.core.jsp.DefaultFrameAnalyzer;
import com.lide.core.jsp.DefaultJspAnalyzer;
import com.lide.core.jsp.DefaultNavigationTargetExtractor;
import com.lide.core.jsp.DefaultUrlParameterExtractor;
import com.lide.core.jsp.JspAnalyzer;
import com.lide.core.model.PageDescriptor;
import com.lide.core.report.DefaultMigrationReportGenerator;
import com.lide.core.report.JsonSchemaGenerator;
import com.lide.core.report.MigrationReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMigrationReportGeneratorTest {

    private Path tempRoot;
    private Path outputDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("lide-report-root");
        outputDir = Files.createTempDirectory("lide-report-output");
    }

    @Test
    void generatesReportsWithComplexityScoring() throws Exception {
        Path customerJsp = copyFixture("customer/searchCustomer.jsp");
        Path auditJsp = copyFixture("admin/audit.jsp");
        Path formFile = copyFixture("java/CustomerSearchForm.java");
        Path controllerFile = copyFixture("java/CustomerController.java");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(customerJsp);
        index.addJspFile(auditJsp);
        index.addJavaFile(formFile);
        index.addJavaFile(controllerFile);

        JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = jspAnalyzer.analyze(tempRoot, index);

        DefaultFrameAnalyzer frameAnalyzer = new DefaultFrameAnalyzer();
        frameAnalyzer.extract(tempRoot, pages);

        NavigationTargetExtractor navigationTargetExtractor = new DefaultNavigationTargetExtractor();
        navigationTargetExtractor.extract(tempRoot, pages);

        UrlParameterExtractor urlParameterExtractor = new DefaultUrlParameterExtractor();
        urlParameterExtractor.extract(tempRoot, pages);

        JavaUsageAnalyzer javaAnalyzer = new DefaultJavaUsageAnalyzer();
        JavaMetadataIndex javaMetadata = javaAnalyzer.analyze(index);

        JsonSchemaGenerator jsonGenerator = new DefaultJsonSchemaGenerator();
        jsonGenerator.generate(tempRoot, outputDir, pages, javaMetadata);

        MigrationReportGenerator migrationGenerator = new DefaultMigrationReportGenerator();
        migrationGenerator.generate(tempRoot, outputDir, pages, javaMetadata);

        Path reportJson = outputDir.resolve("migration-report.json");
        Path reportCsv = outputDir.resolve("migration-report.csv");
        Path reportHtml = outputDir.resolve("migration-report.html");

        assertTrue(Files.exists(reportJson));
        assertTrue(Files.exists(reportCsv));
        assertTrue(Files.exists(reportHtml));

        Map<?, ?> root = mapper.readValue(reportJson.toFile(), Map.class);
        List<Map<?, ?>> reportPages = (List<Map<?, ?>>) root.get("pages");
        assertEquals(2, reportPages.size());

        Map<?, ?> auditEntry = reportPages.stream()
                .filter(p -> ((String) p.get("pageId")).contains("admin/audit.jsp"))
                .findFirst()
                .orElseThrow();

        assertEquals(Boolean.TRUE, auditEntry.get("scriptlets"));
        assertEquals(Boolean.TRUE, auditEntry.get("sessionUsage"));
        assertEquals(Boolean.TRUE, auditEntry.get("framesPresent"));
        assertEquals(Boolean.TRUE, auditEntry.get("missingMappings"));
        assertEquals("CRITICAL", auditEntry.get("difficulty"));

        Map<?, ?> searchEntry = reportPages.stream()
                .filter(p -> ((String) p.get("pageId")).contains("customer/searchCustomer.jsp"))
                .findFirst()
                .orElseThrow();

        double auditScore = ((Number) auditEntry.get("complexityScore")).doubleValue();
        double searchScore = ((Number) searchEntry.get("complexityScore")).doubleValue();
        assertTrue(auditScore > searchScore, "Audit page should be scored higher due to risk factors");
    }

    private Path copyFixture(String relativePath) throws Exception {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/" + relativePath)) {
            assertNotNull(in, "Missing fixture: " + relativePath);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
