package com.lide.core.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.FrameDefinition;
import com.lide.core.model.HiddenField;
import com.lide.core.model.JsRoutingHint;
import com.lide.core.model.NavigationTarget;
import com.lide.core.model.OutputFieldDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDependency;
import com.lide.core.model.PageDescriptor;
import com.lide.core.model.SessionDependency;
import com.lide.core.model.UrlParameter;
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
        int jsRoutingCount = ensureList(page.getJsRoutingHints()).size();
        int urlParameterCount = ensureList(page.getUrlParameterCandidates()).size();
        int frameCount = ensureList(page.getFrameDefinitions()).size();
        int crossFrameInteractionCount = ensureList(page.getCrossFrameInteractions()).size();
        int hiddenFieldCount = ensureList(page.getHiddenFields()).size();
        int sessionDependencyCount = ensureList(page.getSessionDependencies()).size();
        int pageDependencyCount = ensureList(page.getPageDependencies()).size();
        boolean framesetPage = Boolean.TRUE.equals(page.getFramesetPage());

        String pageContent = readPageContent(rootDir, page);
        int dynamicExpressions = countDynamicExpressions(page, pageContent);
        boolean hasScriptlets = pageContent != null && pageContent.contains("<%");
        boolean hasSessionUsage = sessionDependencyCount > 0
                || containsIgnoreCase(pageContent, "session")
                || containsIgnoreCase(pageContent, "sessionScope");
        boolean hasFrames = frameCount > 0 || framesetPage
                || containsIgnoreCase(pageContent, "<frame")
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
            String frameNote = frameCount > 0
                    ? "Frames detected: " + frameCount
                    : "Frames or iframes detected";
            notes.add(frameNote);
            if (framesetPage) {
                notes.add("Likely layout/frameset page");
            }
        }
        if (missingMappings) {
            notes.add("No controller/backing bean mapping identified");
        }
        if (crossFrameInteractionCount > 0) {
            notes.add("Cross-frame interactions detected: " + crossFrameInteractionCount);
        }
        if (hiddenFieldCount > 0) {
            notes.add("Hidden fields detected: " + hiddenFieldCount);
        }
        if (sessionDependencyCount > 0) {
            notes.add("Session dependencies detected: " + sessionDependencyCount);
        }

        List<NavigationTarget> navigationTargets = ensureList(page.getNavigationTargets());
        List<UrlParameter> urlParameters = ensureList(page.getUrlParameterCandidates());
        List<HiddenField> hiddenFields = ensureList(page.getHiddenFields());
        List<FrameDefinition> frameDefinitions = ensureList(page.getFrameDefinitions());
        List<SessionDependency> sessionDependencies = ensureList(page.getSessionDependencies());
        List<JsRoutingHint> jsRoutingHints = ensureList(page.getJsRoutingHints());
        List<PageDependency> pageDependencies = ensureList(page.getPageDependencies());

        String pageId = resolvePageId(rootDir, page);
        return new PageReportEntry(pageId,
                page.getTitle(),
                formCount,
                fieldCount,
                outputCount,
                navigationCount,
                jsRoutingCount,
                urlParameterCount,
                crossFrameInteractionCount,
                hiddenFieldCount,
                sessionDependencyCount,
                pageDependencyCount,
                dynamicExpressions,
                hasScriptlets,
                hasSessionUsage,
                hasFrames,
                frameCount,
                framesetPage,
                missingMappings,
                roundScore(complexityScore),
                difficulty,
                page.getConfidenceLabel(),
                ensureList(page.getControllerCandidates()),
                ensureList(page.getBackingBeanCandidates()),
                notes,
                navigationTargets,
                urlParameters,
                hiddenFields,
                frameDefinitions,
                sessionDependencies,
                jsRoutingHints,
                pageDependencies);
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
        lines.add("pageId,title,forms,fields,outputs,navigationTargets,jsRoutingHints,urlParameters,crossFrameInteractions,hiddenFields,sessionDependencies,pageDependencies,dynamicExpressions,scriptlets,sessionUsage,frames,frameCount,frameset,missingMappings,complexity,difficulty,confidence");
        for (PageReportEntry entry : entries) {
            lines.add(String.join(",",
                    escapeCsv(entry.pageId()),
                    escapeCsv(entry.title()),
                    Integer.toString(entry.formCount()),
                    Integer.toString(entry.fieldCount()),
                    Integer.toString(entry.outputCount()),
                    Integer.toString(entry.navigationTargets()),
                    Integer.toString(entry.jsRoutingHints()),
                    Integer.toString(entry.urlParameters()),
                    Integer.toString(entry.crossFrameInteractions()),
                    Integer.toString(entry.hiddenFields()),
                    Integer.toString(entry.sessionDependencies()),
                    Integer.toString(entry.pageDependencies()),
                    Integer.toString(entry.dynamicExpressions()),
                    Boolean.toString(entry.scriptlets()),
                    Boolean.toString(entry.sessionUsage()),
                    Boolean.toString(entry.framesPresent()),
                    Integer.toString(entry.frameCount()),
                    Boolean.toString(entry.framesetPage()),
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
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <title>LIDE Migration Report</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid #ccc; padding: 8px; }
                    th { background: #f5f5f5; }
                    tr.clickable-row { cursor: pointer; }
                    tr.selected { background: #eef6ff; }
                    .badge { padding: 2px 6px; border-radius: 4px; color: #fff; font-size: 12px; }
                    .LOW { background: #2b9348; }
                    .MEDIUM { background: #f0ad4e; }
                    .HIGH { background: #d9534f; }
                    .CRITICAL { background: #5c0a0a; }
                    .detail-panel { margin-top: 20px; border-top: 1px solid #ddd; padding-top: 16px; }
                    .detail-summary { margin-bottom: 12px; }
                    details summary { cursor: pointer; font-weight: bold; }
                    .detail-item { border: 1px solid #e0e0e0; padding: 8px; margin: 6px 0; border-radius: 4px; background: #fafafa; }
                    .detail-meta { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: #555; }
                    .detail-snippet { background: #f5f5f5; padding: 6px; border-radius: 4px; white-space: pre-wrap; word-break: break-word; margin-top: 6px; }
                    .confidence-pill { padding: 2px 6px; border-radius: 4px; color: #fff; font-size: 12px; }
                    .confidence-high { background: #2b9348; }
                    .confidence-medium { background: #f0ad4e; color: #000; }
                    .confidence-low { background: #6c757d; }
                    .confidence-unknown { background: #adb5bd; }
                    .section-container { margin-top: 12px; }
                    .detail-title { font-weight: bold; margin-bottom: 4px; }
                    .empty { color: #777; padding: 6px; }
                  </style>
                </head>
                <body>
                  <h1>LIDE Migration Report</h1>
                  <label for="difficultyFilter">Filter by difficulty:</label>
                  <select id="difficultyFilter">
                    <option value="ALL">All</option>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                  <table id="reportTable">
                    <thead>
                      <tr>
                        <th>Page</th>
                        <th>Forms</th>
                        <th>Fields</th>
                        <th>Outputs</th>
                        <th>Frames</th>
                        <th>Navigation</th>
                        <th>JS Routing</th>
                        <th>Cross-Frame</th>
                        <th>Hidden Fields</th>
                        <th>Session Dependencies</th>
                        <th>Dependencies</th>
                        <th>URL Params</th>
                        <th>Complexity</th>
                        <th>Difficulty</th>
                        <th>Notes</th>
                      </tr>
                    </thead>
                    <tbody></tbody>
                  </table>
                  <div id="detailPanel" class="detail-panel">
                    <h2>Page Details</h2>
                    <div id="detailSummary" class="detail-summary">Select a page to view details.</div>
                    <div class="section-grid">
                      <div id="navTargetsSection" class="section-container"></div>
                      <div id="urlParamsSection" class="section-container"></div>
                      <div id="hiddenFieldsSection" class="section-container"></div>
                      <div id="framesSection" class="section-container"></div>
                      <div id="sessionDepsSection" class="section-container"></div>
                      <div id="jsRoutingSection" class="section-container"></div>
                      <div id="pageDepsSection" class="section-container"></div>
                    </div>
                  </div>
                  <script>
                    const data = %s;
                    const tbody = document.querySelector('#reportTable tbody');
                    const filter = document.getElementById('difficultyFilter');
                    let selectedRow = null;

                    const difficultyLabels = {
                      LOW: 'LOW',
                      MEDIUM: 'MEDIUM',
                      HIGH: 'HIGH',
                      CRITICAL: 'CRITICAL'
                    };

                    function normalizeConfidence(conf) {
                      return (conf || 'UNKNOWN').toUpperCase();
                    }

                    function confidenceClass(conf) {
                      const normalized = normalizeConfidence(conf);
                      if (normalized === 'HIGH') return 'confidence-high';
                      if (normalized === 'MEDIUM') return 'confidence-medium';
                      if (normalized === 'LOW') return 'confidence-low';
                      return 'confidence-unknown';
                    }

                    function shouldOpenSection(items) {
                      return !!(items && items.length && items.some(item => normalizeConfidence(item.confidence) !== 'LOW'));
                    }

                    function createDetailItem(title, source, snippet, confidence) {
                      const wrapper = document.createElement('div');
                      wrapper.className = 'detail-item';

                      const header = document.createElement('div');
                      header.className = 'detail-title';
                      header.textContent = title || 'Unknown';

                      const meta = document.createElement('div');
                      meta.className = 'detail-meta';
                      const sourceSpan = document.createElement('span');
                      sourceSpan.textContent = `Source: ${source || 'N/A'}`;
                      const confSpan = document.createElement('span');
                      const normalized = normalizeConfidence(confidence);
                      confSpan.className = `confidence-pill ${confidenceClass(confidence)}`;
                      confSpan.textContent = normalized;
                      meta.appendChild(sourceSpan);
                      meta.appendChild(confSpan);

                      const snippetEl = document.createElement('pre');
                      snippetEl.className = 'detail-snippet';
                      snippetEl.textContent = snippet || 'N/A';

                      wrapper.appendChild(header);
                      wrapper.appendChild(meta);
                      wrapper.appendChild(snippetEl);

                      return wrapper;
                    }

                    function renderSection(containerId, title, items, builder) {
                      const container = document.getElementById(containerId);
                      container.innerHTML = '';

                      const details = document.createElement('details');
                      if (shouldOpenSection(items)) {
                        details.open = true;
                      }

                      const summary = document.createElement('summary');
                      summary.textContent = `${title} (${(items && items.length) || 0})`;
                      details.appendChild(summary);

                      if (!items || items.length === 0) {
                        const empty = document.createElement('div');
                        empty.className = 'detail-item empty';
                        empty.textContent = 'No entries detected.';
                        details.appendChild(empty);
                      } else {
                        items.forEach(item => details.appendChild(builder(item)));
                      }

                      container.appendChild(details);
                    }

                    function renderDetails(entry) {
                      const summaryEl = document.getElementById('detailSummary');
                      if (!entry) {
                        summaryEl.textContent = 'Select a page to view details.';
                        return;
                      }

                      summaryEl.innerHTML = `<strong>${entry.pageId}</strong> — Difficulty: <span class=\"badge ${entry.difficulty}\">${difficultyLabels[entry.difficulty] || entry.difficulty}</span>, Complexity: ${entry.complexityScore.toFixed(1)}`;

                      renderSection('navTargetsSection', 'Navigation Targets', entry.navigationTargetsDetail,
                        (nav) => createDetailItem(nav.targetPage || 'Target', nav.sourcePattern || 'href/script', nav.snippet, nav.confidence));

                      renderSection('urlParamsSection', 'URL Parameter Usage', entry.urlParametersDetail,
                        (param) => createDetailItem(param.name || 'Parameter', param.source || 'link/script', param.snippet, param.confidence));

                      renderSection('hiddenFieldsSection', 'Hidden Fields', entry.hiddenFieldsDetail,
                        (field) => createDetailItem(field.name || 'Hidden Field', field.expression || field.defaultValue || 'hidden', field.snippet, field.confidence));

                      renderSection('framesSection', 'Frame Layout', entry.frameDefinitionsDetail,
                        (frame) => createDetailItem(frame.frameName || 'Frame', frame.source || frame.tag || 'frame', frame.tag || frame.source, frame.confidence));

                      renderSection('sessionDepsSection', 'Session Dependencies', entry.sessionDependenciesDetail,
                        (dep) => createDetailItem(dep.key || 'Session Key', dep.source || 'session', dep.snippet, dep.confidence));

                      renderSection('jsRoutingSection', 'JS Routing Hints', entry.jsRoutingHintsDetail,
                        (hint) => createDetailItem(hint.targetPage || 'Target', hint.sourcePattern || 'script', hint.snippet, hint.confidence));

                      renderSection('pageDepsSection', 'Page Dependencies', entry.pageDependenciesDetail,
                        (dep) => createDetailItem(`${dep.from || 'source'} -> ${dep.to || 'target'}`, dep.type || 'dependency', dep.type || `${dep.from || ''} => ${dep.to || ''}`, dep.confidence));
                    }

                    function render() {
                      const target = filter.value;
                      tbody.innerHTML = '';
                      let firstRendered = null;
                      data.filter(entry => target === 'ALL' || entry.difficulty === target)
                          .sort((a, b) => b.complexityScore - a.complexityScore)
                          .forEach(entry => {
                            const row = document.createElement('tr');
                            row.className = 'clickable-row';
                            row.innerHTML = `
                              <td>${entry.pageId}</td>
                              <td>${entry.formCount}</td>
                              <td>${entry.fieldCount}</td>
                              <td>${entry.outputCount}</td>
                              <td>${entry.frameCount} ${entry.framesetPage ? '(layout)' : ''}</td>
                              <td>${entry.navigationTargets}</td>
                              <td>${entry.jsRoutingHints}</td>
                              <td>${entry.crossFrameInteractions}</td>
                              <td>${entry.hiddenFields}</td>
                              <td>${entry.sessionDependencies}</td>
                              <td>${entry.pageDependencies}</td>
                              <td>${entry.urlParameters}</td>
                              <td>${entry.complexityScore.toFixed(1)}</td>
                              <td><span class=\"badge ${entry.difficulty}\">${entry.difficulty}</span></td>
                              <td>${(entry.notes || []).join('<br/>')}</td>`;
                            row.addEventListener('click', () => selectEntry(entry, row));
                            tbody.appendChild(row);
                            if (!firstRendered) {
                              firstRendered = { entry, row };
                            }
                          });
                      if (firstRendered) {
                        selectEntry(firstRendered.entry, firstRendered.row);
                      } else {
                        renderDetails(null);
                      }
                    }

                    function selectEntry(entry, row) {
                      if (selectedRow) {
                        selectedRow.classList.remove('selected');
                      }
                      selectedRow = row;
                      if (selectedRow) {
                        selectedRow.classList.add('selected');
                      }
                      renderDetails(entry);
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
                                   int jsRoutingHints,
                                   int urlParameters,
                                   int crossFrameInteractions,
                                   int hiddenFields,
                                   int sessionDependencies,
                                   int pageDependencies,
                                   int dynamicExpressions,
                                   boolean scriptlets,
                                   boolean sessionUsage,
                                   boolean framesPresent,
                                   int frameCount,
                                   boolean framesetPage,
                                   boolean missingMappings,
                                   double complexityScore,
                                   String difficulty,
                                   String confidenceLabel,
                                   List<String> controllerCandidates,
                                   List<String> backingBeanCandidates,
                                   List<String> notes,
                                   List<NavigationTarget> navigationTargetsDetail,
                                   List<UrlParameter> urlParametersDetail,
                                   List<HiddenField> hiddenFieldsDetail,
                                   List<FrameDefinition> frameDefinitionsDetail,
                                   List<SessionDependency> sessionDependenciesDetail,
                                   List<JsRoutingHint> jsRoutingHintsDetail,
                                   List<PageDependency> pageDependenciesDetail) {
    }
}
