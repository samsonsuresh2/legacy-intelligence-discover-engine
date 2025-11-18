package com.lide.core.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.OutputFieldDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates migration dashboards and supporting assets summarizing analysis confidence and complexity.
 */
public class DefaultMigrationReportGenerator implements MigrationReportGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMigrationReportGenerator.class);

    private final ObjectMapper mapper;

    public DefaultMigrationReportGenerator() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public void generate(Path rootDir,
                         Path outputDir,
                         List<PageDescriptor> pages,
                         JavaMetadataIndex javaMetadata) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(javaMetadata, "javaMetadata");

        Files.createDirectories(outputDir);

        List<PageReportEntry> entries = new ArrayList<>();
        for (PageDescriptor descriptor : pages) {
            entries.add(evaluatePage(rootDir, descriptor));
        }

        writeJsonReport(outputDir.resolve("migration-report.json"), entries);
        writeCsvReport(outputDir.resolve("migration-report.csv"), entries);
        writeHtmlReport(outputDir.resolve("migration-report.html"), entries);

        LOGGER.info("Migration reports generated for {} pages", entries.size());
    }

    private PageReportEntry evaluatePage(Path rootDir, PageDescriptor page) {
        int formCount = ensureList(page.getForms()).size();
        int fieldCount = ensureList(page.getForms()).stream()
                .map(FormDescriptor::getFields)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();
        int outputCount = ensureList(page.getOutputs()).size();
        int navigationCount = ensureList(page.getNavigationTargets()).size();
        int urlParameterCount = ensureList(page.getUrlParameterCandidates()).size();

        String pageContent = readPageContent(rootDir, page);
        int dynamicExpressions = countDynamicExpressions(page, pageContent);
        boolean hasScriptlets = pageContent != null && pageContent.contains("<%");
        boolean hasSessionUsage = containsIgnoreCase(pageContent, "session")
                || containsIgnoreCase(pageContent, "sessionScope");
        boolean hasFrames = containsIgnoreCase(pageContent, "<frame")
                || containsIgnoreCase(pageContent, "<frameset")
                || containsIgnoreCase(pageContent, "<iframe");
        boolean missingMappings = isMissingMappings(page);

        double complexityScore = computeComplexity(formCount, fieldCount, outputCount, dynamicExpressions,
                hasScriptlets, hasSessionUsage, hasFrames, missingMappings);
        String difficulty = classifyDifficulty(complexityScore);

        List<String> notes = new ArrayList<>(ensureList(page.getNotes()));
        if (hasScriptlets) {
            notes.add("Scriptlets detected");
        }
        if (dynamicExpressions > 0) {
            notes.add("Dynamic expressions detected: " + dynamicExpressions);
        }
        if (hasSessionUsage) {
            notes.add("Session usage detected");
        }
        if (hasFrames) {
            notes.add("Frames or iframes detected");
        }
        if (missingMappings) {
            notes.add("No controller/backing bean mapping identified");
        }

        String pageId = resolvePageId(rootDir, page);
        return new PageReportEntry(pageId,
                page.getTitle(),
                formCount,
                fieldCount,
                outputCount,
                navigationCount,
                urlParameterCount,
                dynamicExpressions,
                hasScriptlets,
                hasSessionUsage,
                hasFrames,
                missingMappings,
                roundScore(complexityScore),
                difficulty,
                page.getConfidenceLabel(),
                ensureList(page.getControllerCandidates()),
                ensureList(page.getBackingBeanCandidates()),
                notes);
    }

    private String resolvePageId(Path rootDir, PageDescriptor page) {
        if (page.getPageId() != null && !page.getPageId().isBlank()) {
            return page.getPageId();
        }
        if (page.getSourcePath() != null) {
            try {
                return rootDir.relativize(page.getSourcePath()).toString().replace('\\', '/');
            } catch (Exception ignored) {
                // fall through
            }
            return page.getSourcePath().toString().replace('\\', '/');
        }
        return "unknown";
    }

    private String readPageContent(Path rootDir, PageDescriptor page) {
        if (page.getSourcePath() == null) {
            return null;
        }
        Path source = page.getSourcePath();
        if (!source.isAbsolute()) {
            source = rootDir.resolve(source);
        }
        try {
            return Files.readString(source);
        } catch (IOException ex) {
            LOGGER.debug("Unable to read page content for {}: {}", source, ex.getMessage());
            return null;
        }
    }

    private boolean isMissingMappings(PageDescriptor page) {
        List<String> controllers = ensureList(page.getControllerCandidates());
        List<String> beans = ensureList(page.getBackingBeanCandidates());
        return controllers.isEmpty() && beans.isEmpty();
    }

    private int countDynamicExpressions(PageDescriptor page, String pageContent) {
        Set<String> expressions = new LinkedHashSet<>();

        for (FormDescriptor form : ensureList(page.getForms())) {
            for (FieldDescriptor field : ensureList(form.getFields())) {
                expressions.addAll(ensureList(field.getBindingExpressions()));
            }
        }

        for (OutputSectionDescriptor output : ensureList(page.getOutputs())) {
            for (OutputFieldDescriptor field : ensureList(output.getFields())) {
                if (field.getBindingExpression() != null) {
                    expressions.add(field.getBindingExpression());
                }
            }
        }

        if (pageContent != null) {
            int index = pageContent.indexOf("${");
            while (index >= 0) {
                expressions.add("${...");
                index = pageContent.indexOf("${", index + 2);
            }
        }

        return expressions.size();
    }

    private double computeComplexity(int formCount,
                                     int fieldCount,
                                     int outputCount,
                                     int dynamicExpressions,
                                     boolean hasScriptlets,
                                     boolean hasSessionUsage,
                                     boolean hasFrames,
                                     boolean missingMappings) {
        double score = 0;
        score += Math.min(30, formCount * 6.5);
        score += Math.min(30, fieldCount * 1.5);
        score += Math.min(15, outputCount * 3.5);

        if (hasScriptlets) {
            score += 15;
        }
        if (dynamicExpressions > 0) {
            score += Math.min(15, 5 + dynamicExpressions);
        }
        if (hasSessionUsage) {
            score += 8;
        }
        if (hasFrames) {
            score += 8;
        }
        if (missingMappings) {
            score += 8;
        }

        return Math.min(100, score);
    }

    private String classifyDifficulty(double score) {
        if (score < 25) {
            return "LOW";
        }
        if (score < 50) {
            return "MEDIUM";
        }
        if (score < 75) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    private void writeJsonReport(Path path, List<PageReportEntry> entries) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());
        root.put("totalPages", entries.size());
        root.put("pages", entries);
        mapper.writeValue(path.toFile(), root);
    }

    private void writeCsvReport(Path path, List<PageReportEntry> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("pageId,title,forms,fields,outputs,navigationTargets,urlParameters,dynamicExpressions,scriptlets,sessionUsage,frames,missingMappings,complexity,difficulty,confidence");
        for (PageReportEntry entry : entries) {
            lines.add(String.join(",",
                    escapeCsv(entry.pageId()),
                    escapeCsv(entry.title()),
                    Integer.toString(entry.formCount()),
                    Integer.toString(entry.fieldCount()),
                    Integer.toString(entry.outputCount()),
                    Integer.toString(entry.navigationTargets()),
                    Integer.toString(entry.urlParameters()),
                    Integer.toString(entry.dynamicExpressions()),
                    Boolean.toString(entry.scriptlets()),
                    Boolean.toString(entry.sessionUsage()),
                    Boolean.toString(entry.framesPresent()),
                    Boolean.toString(entry.missingMappings()),
                    Double.toString(entry.complexityScore()),
                    escapeCsv(entry.difficulty()),
                    escapeCsv(entry.confidenceLabel())));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private void writeHtmlReport(Path path, List<PageReportEntry> entries) throws IOException {
        String data = mapper.writeValueAsString(entries);
        String html = """
                <!DOCTYPE html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <title>LIDE Migration Report</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid #ccc; padding: 8px; }
                    th { background: #f5f5f5; }
                    .badge { padding: 2px 6px; border-radius: 4px; color: #fff; font-size: 12px; }
                    .LOW { background: #2b9348; }
                    .MEDIUM { background: #f0ad4e; }
                    .HIGH { background: #d9534f; }
                    .CRITICAL { background: #5c0a0a; }
                  </style>
                </head>
                <body>
                  <h1>LIDE Migration Report</h1>
                  <label for=\"difficultyFilter\">Filter by difficulty:</label>
                  <select id=\"difficultyFilter\">
                    <option value=\"ALL\">All</option>
                    <option value=\"LOW\">Low</option>
                    <option value=\"MEDIUM\">Medium</option>
                    <option value=\"HIGH\">High</option>
                    <option value=\"CRITICAL\">Critical</option>
                  </select>
                  <table id=\"reportTable\">
                    <thead>
                      <tr>
                        <th>Page</th>
                        <th>Forms</th>
                        <th>Fields</th>
                        <th>Outputs</th>
                        <th>Navigation</th>
                        <th>URL Params</th>
                        <th>Complexity</th>
                        <th>Difficulty</th>
                        <th>Notes</th>
                      </tr>
                    </thead>
                    <tbody></tbody>
                  </table>
                  <script>
                    const data = %s;
                    const tbody = document.querySelector('#reportTable tbody');
                    const filter = document.getElementById('difficultyFilter');

                    function render() {
                      const target = filter.value;
                      tbody.innerHTML = '';
                      data.filter(entry => target === 'ALL' || entry.difficulty === target)
                          .sort((a, b) => b.complexityScore - a.complexityScore)
                          .forEach(entry => {
                            const row = document.createElement('tr');
                            row.innerHTML = `
                              <td>${entry.pageId}</td>
                              <td>${entry.formCount}</td>
                              <td>${entry.fieldCount}</td>
                              <td>${entry.outputCount}</td>
                              <td>${entry.navigationTargets}</td>
                              <td>${entry.urlParameters}</td>
                              <td>${entry.complexityScore.toFixed(1)}</td>
                              <td><span class="badge ${entry.difficulty}">${entry.difficulty}</span></td>
                              <td>${(entry.notes || []).join('<br/>')}</td>`;
                            tbody.appendChild(row);
                          });
                    }

                    filter.addEventListener('change', render);
                    render();
                  </script>
                </body>
                </html>
                """.formatted(data);

        Files.writeString(path, html, StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private double roundScore(double score) {
        return Math.round(score * 10.0) / 10.0;
    }

    private <T> List<T> ensureList(List<T> value) {
        if (value == null) {
            return Collections.emptyList();
        }
        return value;
    }

    private record PageReportEntry(String pageId,
                                   String title,
                                   int formCount,
                                   int fieldCount,
                                   int outputCount,
                                   int navigationTargets,
                                   int urlParameters,
                                   int dynamicExpressions,
                                   boolean scriptlets,
                                   boolean sessionUsage,
                                   boolean framesPresent,
                                   boolean missingMappings,
                                   double complexityScore,
                                   String difficulty,
                                   String confidenceLabel,
                                   List<String> controllerCandidates,
                                   List<String> backingBeanCandidates,
                                   List<String> notes) {
    }
}
