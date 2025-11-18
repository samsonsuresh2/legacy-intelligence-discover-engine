package com.lide.core.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lide.core.config.AnalyzerConfig;
import com.lide.core.java.JavaFieldMetadata;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.FrameDefinition;
import com.lide.core.model.NavigationTarget;
import com.lide.core.model.OptionDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDescriptor;
import com.lide.core.model.UrlParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default JSON generator that merges JSP descriptors with Java metadata to produce final artifacts.
 */
public class DefaultJsonSchemaGenerator implements JsonSchemaGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJsonSchemaGenerator.class);

    private final ObjectMapper mapper;
    private final AnalyzerConfig config;

    public DefaultJsonSchemaGenerator() {
        this(AnalyzerConfig.defaultConfig());
    }

    public DefaultJsonSchemaGenerator(AnalyzerConfig config) {
        AnalyzerConfig base = AnalyzerConfig.defaultConfig();
        AnalyzerConfig working = base.merge(Objects.requireNonNull(config, "config"));
        working.normalize();
        this.config = working;
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

        List<Map<String, Object>> summaryEntries = new ArrayList<>();

        for (PageDescriptor page : pages) {
            PageAggregation aggregation = enrichPageDescriptor(page, javaMetadata);
            Map<String, Object> pageJson = buildPageJson(rootDir, page, aggregation);

            Path target = resolveOutputPath(outputDir, page.getPageId());
            Files.createDirectories(target.getParent());
            mapper.writeValue(target.toFile(), pageJson);

            summaryEntries.add(buildSummaryEntry(page, aggregation,
                    outputDir.relativize(target).toString().replace('\\', '/')));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("pageCount", summaryEntries.size());
        summary.put("pages", summaryEntries);

        Path summaryPath = outputDir.resolve("summary.json");
        mapper.writeValue(summaryPath.toFile(), summary);

        LOGGER.info("Generated {} page JSON descriptors and summary at {}", summaryEntries.size(), summaryPath);
    }

    private PageAggregation enrichPageDescriptor(PageDescriptor page, JavaMetadataIndex javaMetadata) {
        List<FormDescriptor> forms = new ArrayList<>(ensureList(page.getForms()));
        page.setForms(forms);
        List<OutputSectionDescriptor> outputs = new ArrayList<>(ensureList(page.getOutputs()));
        page.setOutputs(outputs);
        page.setFrameDefinitions(new ArrayList<>(ensureList(page.getFrameDefinitions())));
        page.setNotes(new ArrayList<>(ensureList(page.getNotes())));

        HeuristicResult heuristics = applyHeuristics(page, forms, javaMetadata);
        page.setControllerCandidates(new ArrayList<>(heuristics.controllerCandidates()));
        page.setBackingBeanCandidates(new ArrayList<>(heuristics.backingBeanCandidates()));
        page.getNotes().addAll(heuristics.notes());

        int totalFields = 0;
        int enrichedFields = 0;

        for (FormDescriptor form : forms) {
            if (form.getNotes() == null) {
                form.setNotes(new ArrayList<>());
            }
            String beanClass = resolveFormBackingBean(form, page, javaMetadata);
            form.setBackingBeanClassName(beanClass);

            List<FieldDescriptor> fields = new ArrayList<>(ensureList(form.getFields()));
            form.setFields(fields);
            for (FieldDescriptor field : fields) {
                totalFields++;
                mergeFieldMetadata(field, beanClass, javaMetadata, form.getNotes());
                if ((field.getSourceBeanClass() != null && !field.getSourceBeanClass().isBlank())
                        || (field.getConstraints() != null && !field.getConstraints().isEmpty())) {
                    enrichedFields++;
                }
            }
            if (beanClass == null && form.getNotes().isEmpty()) {
                form.getNotes().add("No backing bean candidate identified");
            }
        }

        double confidence = computeConfidenceScore(forms.size(), totalFields, outputs.size(), enrichedFields,
                heuristics.controllerCandidates().size());
        page.setConfidenceScore(confidence);
        page.setConfidenceLabel(toConfidenceLabel(confidence, heuristics.bestConfidence()));

        return new PageAggregation(forms.size(), outputs.size(), totalFields, enrichedFields,
                confidence, page.getConfidenceLabel());
    }

    private HeuristicResult applyHeuristics(PageDescriptor page,
                                            List<FormDescriptor> forms,
                                            JavaMetadataIndex javaMetadata) {
        List<ControllerCandidate> controllerCandidates = new ArrayList<>();
        LinkedHashSet<String> beanCandidates = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();

        for (String existing : ensureList(page.getControllerCandidates())) {
            addControllerCandidate(controllerCandidates, existing, classifyController(existing));
        }

        for (FormDescriptor form : forms) {
            String action = form.getAction();
            if (action == null || action.isBlank()) {
                continue;
            }
            String base = normalizeActionName(action);
            if (base == null) {
                continue;
            }
            for (String match : matchControllersByBase(base, javaMetadata)) {
                addControllerCandidate(controllerCandidates, match, classifyController(match));
                notes.add("Matched controller " + match + " from form action " + action);
            }
        }

        List<String> baseNames = deriveBaseNames(page);
        List<String> controllerPatterns = config.getNamingConventions().getJspToControllerPatterns();
        for (String base : baseNames) {
            for (String pattern : controllerPatterns) {
                String simple = applyPattern(pattern, base);
                Set<String> matches = matchControllersBySimple(simple, javaMetadata);
                if (!matches.isEmpty()) {
                    for (String match : matches) {
                        addControllerCandidate(controllerCandidates, match, classifyController(match));
                    }
                    notes.add("Matched controller candidates " + matches + " via pattern " + pattern + " for base " + base);
                } else {
                    for (String pkg : configuredControllerPackages()) {
                        String candidate = pkg.isBlank() ? simple : pkg + "." + simple;
                        if (controllerCandidates.stream().noneMatch(c -> c.name.equals(candidate))) {
                            addControllerCandidate(controllerCandidates, candidate, ConfidenceLevel.LOW);
                            notes.add("Heuristic controller candidate " + candidate + " inferred from pattern " + pattern);
                        }
                    }
                }
            }
        }

        for (FormDescriptor form : forms) {
            String bean = form.getBackingBeanClassName();
            if (bean != null && !bean.isBlank()) {
                beanCandidates.add(bean);
            }
        }

        List<String> beanSuffixes = config.getNamingConventions().getFormBeanSuffixes();
        for (String base : baseNames) {
            for (String suffix : beanSuffixes) {
                String simple = base + suffix;
                Set<String> matches = matchBeansBySimple(simple, javaMetadata);
                if (!matches.isEmpty()) {
                    beanCandidates.addAll(matches);
                } else if (!beanCandidates.contains(simple)) {
                    beanCandidates.add(simple);
                    notes.add("Heuristic bean candidate " + simple + " inferred from suffix " + suffix);
                }
            }
        }

        controllerCandidates.sort((a, b) -> {
            int cmp = b.confidence.compareTo(a.confidence);
            if (cmp != 0) {
                return cmp;
            }
            return a.name.compareToIgnoreCase(b.name);
        });

        List<String> orderedControllers = controllerCandidates.stream()
                .map(ControllerCandidate::name)
                .distinct()
                .collect(Collectors.toList());

        ConfidenceLevel best = controllerCandidates.stream()
                .map(ControllerCandidate::confidence)
                .max(ConfidenceLevel::compareTo)
                .orElse(ConfidenceLevel.LOW);

        return new HeuristicResult(orderedControllers, new ArrayList<>(beanCandidates), new ArrayList<>(notes), best);
    }

    private List<String> deriveBaseNames(PageDescriptor page) {
        List<String> baseNames = new ArrayList<>();
        String pageId = page.getPageId();
        if (pageId == null || pageId.isBlank()) {
            return List.of("page");
        }
        String normalized = pageId.replace('\\', '/');
        String[] segments = normalized.split("/");
        if (segments.length == 0) {
            return List.of("page");
        }
        String fileName = segments[segments.length - 1];
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        List<String> tokens = new ArrayList<>();
        for (String segment : segments) {
            tokens.addAll(splitTokens(segment));
        }
        if (!tokens.isEmpty()) {
            for (int i = 0; i < tokens.size(); i++) {
                baseNames.add(toPascalCase(tokens.subList(i, tokens.size())));
            }
        }
        String fileBase = toPascalCase(splitTokens(fileName));
        if (!fileBase.isBlank()) {
            baseNames.add(fileBase);
        }
        if (segments.length > 1) {
            String combined = toPascalCase(List.of(segments[segments.length - 2], fileName));
            if (!combined.isBlank()) {
                baseNames.add(combined);
            }
        }
        if (baseNames.isEmpty()) {
            baseNames.add("Page");
        }
        return baseNames.stream().distinct().collect(Collectors.toList());
    }

    private List<String> splitTokens(String value) {
        String cleaned = value.replaceAll("[\\\\._-]", " ").replaceAll("(?<!^)(?=[A-Z])", " ");
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private String toPascalCase(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String applyPattern(String pattern, String base) {
        if (pattern.contains("%s")) {
            return pattern.replace("%s", base);
        }
        return base + pattern;
    }

    private Set<String> configuredControllerPackages() {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        packages.addAll(config.getStrutsActionPackages());
        packages.addAll(config.getSpringControllerPackages());
        packages.add("");
        return packages;
    }

    private Set<String> matchControllersByBase(String baseName, JavaMetadataIndex javaMetadata) {
        Set<String> matches = new LinkedHashSet<>();
        String normalized = baseName.toLowerCase(Locale.ROOT);
        for (String controller : javaMetadata.getStrutsActionClasses()) {
            if (simpleNameMatches(controller, normalized, List.of("action", "dispatchaction"))) {
                matches.add(controller);
            }
        }
        for (String controller : javaMetadata.getControllerClasses()) {
            if (simpleNameMatches(controller, normalized, List.of("controller"))) {
                matches.add(controller);
            }
        }
        return matches;
    }

    private Set<String> matchControllersBySimple(String simpleName, JavaMetadataIndex javaMetadata) {
        Set<String> matches = new LinkedHashSet<>();
        for (String controller : javaMetadata.getStrutsActionClasses()) {
            if (simpleName(controller).equalsIgnoreCase(simpleName)) {
                matches.add(controller);
            }
        }
        for (String controller : javaMetadata.getControllerClasses()) {
            if (simpleName(controller).equalsIgnoreCase(simpleName)) {
                matches.add(controller);
            }
        }
        return matches;
    }

    private Set<String> matchBeansBySimple(String simpleName, JavaMetadataIndex javaMetadata) {
        Set<String> matches = new LinkedHashSet<>();
        for (String className : javaMetadata.getFieldsByClass().keySet()) {
            if (simpleName(className).equalsIgnoreCase(simpleName)) {
                matches.add(className);
            }
        }
        return matches;
    }

    private ConfidenceLevel classifyController(String className) {
        if (className == null) {
            return ConfidenceLevel.LOW;
        }
        String pkg = packageName(className);
        boolean hasConfiguredPackages = !config.getStrutsActionPackages().isEmpty()
                || !config.getSpringControllerPackages().isEmpty();
        if (!hasConfiguredPackages) {
            return ConfidenceLevel.MEDIUM;
        }
        for (String configured : configuredControllerPackages()) {
            if (!configured.isBlank() && pkg.startsWith(configured)) {
                return ConfidenceLevel.HIGH;
            }
        }
        return ConfidenceLevel.MEDIUM;
    }

    private void addControllerCandidate(List<ControllerCandidate> candidates,
                                        String name,
                                        ConfidenceLevel confidence) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (ControllerCandidate candidate : candidates) {
            if (candidate.name.equals(name)) {
                candidate.promote(confidence);
                return;
            }
        }
        candidates.add(new ControllerCandidate(name, confidence));
    }

    private boolean simpleNameMatches(String className, String baseName, List<String> suffixes) {
        String simple = simpleName(className).toLowerCase(Locale.ROOT);
        if (simple.equals(baseName)) {
            return true;
        }
        for (String suffix : suffixes) {
            if (simple.equals(baseName + suffix) || simple.equals(baseName + "dispatch" + suffix)) {
                return true;
            }
            if (simple.endsWith(suffix) && simple.startsWith(baseName)) {
                return true;
            }
        }
        return simple.contains(baseName);
    }

    private String resolveFormBackingBean(FormDescriptor form, PageDescriptor page, JavaMetadataIndex javaMetadata) {
        String action = form.getAction();
        String base = action != null ? normalizeActionName(action) : null;

        List<String> controllerCandidates = page.getControllerCandidates();
        if (controllerCandidates != null) {
            for (String controller : controllerCandidates) {
                String bean = deriveBeanFromController(controller, javaMetadata);
                if (bean != null) {
                    return bean;
                }
            }
        }

        if (base != null) {
            String bean = findBeanByBaseName(base, javaMetadata);
            if (bean != null) {
                return bean;
            }
        }

        return inferBeanByFieldOverlap(form, javaMetadata);
    }

    private String deriveBeanFromController(String controller, JavaMetadataIndex javaMetadata) {
        if (controller == null) {
            return null;
        }
        String simple = simpleName(controller);
        String packageName = packageName(controller);
        String base = simple;
        for (String suffix : List.of("Controller", "Action", "DispatchAction")) {
            if (simple.endsWith(suffix)) {
                base = simple.substring(0, simple.length() - suffix.length());
                break;
            }
        }
        for (String candidateSuffix : config.getNamingConventions().getFormBeanSuffixes()) {
            String candidate = packageName.isEmpty()
                    ? base + candidateSuffix
                    : packageName + "." + base + candidateSuffix;
            if (javaMetadata.getFieldsByClass().containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String findBeanByBaseName(String base, JavaMetadataIndex javaMetadata) {
        if (base == null) {
            return null;
        }
        String normalized = base.toLowerCase(Locale.ROOT);
        for (String className : javaMetadata.getFieldsByClass().keySet()) {
            String simple = simpleName(className).toLowerCase(Locale.ROOT);
            if (simple.equals(normalized) || simple.equals(normalized + "form")) {
                return className;
            }
            for (String suffix : config.getNamingConventions().getFormBeanSuffixes()) {
                if (simple.equals(normalized + suffix.toLowerCase(Locale.ROOT))) {
                    return className;
                }
            }
        }
        return null;
    }

    private String inferBeanByFieldOverlap(FormDescriptor form, JavaMetadataIndex javaMetadata) {
        Map<String, Integer> matches = new LinkedHashMap<>();
        for (Map.Entry<String, List<JavaFieldMetadata>> entry : javaMetadata.getFieldsByClass().entrySet()) {
            int overlap = 0;
            for (FieldDescriptor field : ensureList(form.getFields())) {
                String fieldName = field.getName();
                if (fieldName == null) {
                    continue;
                }
                if (entry.getValue().stream().anyMatch(metadata -> metadata.getFieldName().equalsIgnoreCase(fieldName))) {
                    overlap++;
                }
            }
            if (overlap > 0) {
                matches.put(entry.getKey(), overlap);
            }
        }
        return matches.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void mergeFieldMetadata(FieldDescriptor field,
                                    String beanClass,
                                    JavaMetadataIndex javaMetadata,
                                    List<String> formNotes) {
        if (field.getConstraints() == null) {
            field.setConstraints(new ArrayList<>());
        }
        if (field.getNotes() == null) {
            field.setNotes(new ArrayList<>());
        }

        List<JavaFieldMetadata> candidates = new ArrayList<>();
        if (beanClass != null) {
            candidates.addAll(javaMetadata.getFieldsForClass(beanClass));
        }
        if (candidates.isEmpty() && field.getName() != null) {
            candidates.addAll(findFieldMetadataByName(javaMetadata, field.getName()));
        }

        if (candidates.isEmpty()) {
            if (formNotes != null) {
                formNotes.add("No Java metadata found for field " + field.getName());
            }
            return;
        }

        JavaFieldMetadata metadata = selectBestCandidate(candidates, field.getName());
        if (metadata == null) {
            return;
        }

        field.setSourceBeanClass(metadata.getClassName());
        field.setSourceBeanProperty(metadata.getFieldName());
        field.setJavaType(metadata.getFieldType());
        field.setConstraints(new ArrayList<>(metadata.getConstraints()));

        Map<String, Object> attributes = metadata.getAttributes();
        if (attributes.containsKey("required")) {
            field.setRequired(Boolean.TRUE.equals(attributes.get("required"))
                    || "true".equalsIgnoreCase(String.valueOf(attributes.get("required"))));
        }
        if (attributes.containsKey("maxLength")) {
            parseInteger(attributes.get("maxLength")).ifPresent(field::setMaxLength);
        }
        if (attributes.containsKey("minLength")) {
            parseInteger(attributes.get("minLength")).ifPresent(field::setMinLength);
        }
        if (attributes.containsKey("pattern")) {
            field.setPattern(String.valueOf(attributes.get("pattern")));
        }
        if (attributes.containsKey("min")) {
            field.setMinValue(String.valueOf(attributes.get("min")));
        }
        if (attributes.containsKey("max")) {
            field.setMaxValue(String.valueOf(attributes.get("max")));
        }
    }

    private Optional<Integer> parseInteger(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(string.trim()));
            } catch (NumberFormatException ignored) {
                // ignore unparsable numbers
            }
        }
        return Optional.empty();
    }

    private JavaFieldMetadata selectBestCandidate(List<JavaFieldMetadata> candidates, String fieldName) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (fieldName == null) {
            return candidates.get(0);
        }
        List<JavaFieldMetadata> filtered = candidates.stream()
                .filter(candidate -> candidate.getFieldName().equalsIgnoreCase(fieldName))
                .collect(Collectors.toList());
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        if (!filtered.isEmpty()) {
            candidates = filtered;
        }
        return candidates.stream()
                .max(Comparator.comparingInt(candidate -> candidate.getConstraints().size()))
                .orElse(candidates.get(0));
    }

    private List<JavaFieldMetadata> findFieldMetadataByName(JavaMetadataIndex javaMetadata, String fieldName) {
        if (fieldName == null) {
            return List.of();
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        List<JavaFieldMetadata> results = new ArrayList<>();
        for (List<JavaFieldMetadata> metadataList : javaMetadata.getFieldsByClass().values()) {
            for (JavaFieldMetadata metadata : metadataList) {
                String candidate = metadata.getFieldName();
                if (candidate.equalsIgnoreCase(fieldName) || candidate.equalsIgnoreCase(lower)) {
                    results.add(metadata);
                }
            }
        }
        return results;
    }

    private Map<String, Object> buildPageJson(Path rootDir, PageDescriptor page, PageAggregation aggregation) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("pageId", page.getPageId());
        json.put("title", page.getTitle());
        if (page.getSourcePath() != null) {
            Path normalizedRoot = rootDir.toAbsolutePath().normalize();
            Path source = page.getSourcePath().toAbsolutePath().normalize();
            String relative = source.startsWith(normalizedRoot)
                    ? normalizedRoot.relativize(source).toString().replace('\\', '/')
                    : source.toString();
            json.put("sourcePath", relative);
        }

        json.put("forms", page.getForms().stream().map(this::toFormJson).collect(Collectors.toList()));
        json.put("outputs", page.getOutputs().stream().map(this::toOutputJson).collect(Collectors.toList()));
        json.put("frameDefinitions", ensureList(page.getFrameDefinitions()).stream()
                .map(this::toFrameJson)
                .collect(Collectors.toList()));
        json.put("navigationTargets", ensureList(page.getNavigationTargets()).stream()
                .map(this::toNavigationJson)
                .collect(Collectors.toList()));
        json.put("urlParameterCandidates", ensureList(page.getUrlParameterCandidates()).stream()
                .map(this::toUrlParameterJson)
                .collect(Collectors.toList()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("controllerCandidates", ensureList(page.getControllerCandidates()));
        metadata.put("backingBeanCandidates", ensureList(page.getBackingBeanCandidates()));
        metadata.put("notes", ensureList(page.getNotes()));
        metadata.put("confidenceScore", aggregation.confidenceScore());
        metadata.put("confidence", page.getConfidenceLabel());
        metadata.put("framesetPage", Boolean.TRUE.equals(page.getFramesetPage()));
        json.put("metadata", metadata);

        return json;
    }

    private Map<String, Object> toFrameJson(FrameDefinition frame) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("frameName", frame.getFrameName());
        map.put("source", frame.getSource());
        map.put("parentFrameName", frame.getParentFrameName());
        map.put("depth", frame.getDepth());
        map.put("tag", frame.getTag());
        map.put("confidence", frame.getConfidence());
        return map;
    }

    private Map<String, Object> toFormJson(FormDescriptor form) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("formId", form.getFormId());
        map.put("action", form.getAction());
        map.put("method", form.getMethod());
        map.put("backingBeanClass", form.getBackingBeanClassName());
        map.put("fields", ensureList(form.getFields()).stream().map(this::toFieldJson).collect(Collectors.toList()));
        if (form.getNotes() != null && !form.getNotes().isEmpty()) {
            map.put("notes", form.getNotes());
        }
        return map;
    }

    private Map<String, Object> toFieldJson(FieldDescriptor field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", field.getName());
        map.put("id", field.getId());
        map.put("label", field.getLabel());
        map.put("type", field.getType());
        map.put("required", field.isRequired());
        map.put("maxLength", field.getMaxLength());
        map.put("minLength", field.getMinLength());
        map.put("pattern", field.getPattern());
        map.put("placeholder", field.getPlaceholder());
        map.put("defaultValue", field.getDefaultValue());
        map.put("options", ensureList(field.getOptions()).stream()
                .map(this::toOptionJson)
                .collect(Collectors.toList()));
        map.put("bindingExpressions", ensureList(field.getBindingExpressions()));
        map.put("min", field.getMinValue());
        map.put("max", field.getMaxValue());
        map.put("javaType", field.getJavaType());
        map.put("constraints", ensureList(field.getConstraints()));
        map.put("sourceBeanClass", field.getSourceBeanClass());
        map.put("sourceBeanProperty", field.getSourceBeanProperty());
        if (field.getNotes() != null && !field.getNotes().isEmpty()) {
            map.put("notes", field.getNotes());
        }
        return map;
    }

    private Map<String, Object> toOptionJson(OptionDescriptor option) {
        Map<String, Object> optionJson = new LinkedHashMap<>();
        optionJson.put("label", option.getLabel());
        optionJson.put("value", option.getValue());
        optionJson.put("selected", option.isSelected());
        return optionJson;
    }

    private Map<String, Object> toNavigationJson(NavigationTarget target) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("target", target.getTargetPage());
        map.put("sourcePattern", target.getSourcePattern());
        map.put("snippet", target.getSnippet());
        map.put("confidence", target.getConfidence());
        return map;
    }

    private Map<String, Object> toUrlParameterJson(UrlParameter parameter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", parameter.getName());
        map.put("source", parameter.getSource());
        map.put("snippet", parameter.getSnippet());
        map.put("confidence", parameter.getConfidence());
        return map;
    }

    private Map<String, Object> toOutputJson(OutputSectionDescriptor section) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", section.getSectionId());
        map.put("type", section.getType());
        map.put("itemVar", section.getItemVariable());
        map.put("itemsExpression", section.getItemsExpression());
        map.put("fields", ensureList(section.getFields()).stream().map(field -> {
            Map<String, Object> fieldMap = new LinkedHashMap<>();
            fieldMap.put("name", field.getName());
            fieldMap.put("label", field.getLabel());
            fieldMap.put("bindingExpression", field.getBindingExpression());
            fieldMap.put("rawText", field.getRawText());
            if (field.getNotes() != null && !field.getNotes().isEmpty()) {
                fieldMap.put("notes", field.getNotes());
            }
            return fieldMap;
        }).collect(Collectors.toList()));
        if (section.getNotes() != null && !section.getNotes().isEmpty()) {
            map.put("notes", section.getNotes());
        }
        return map;
    }

    private Map<String, Object> buildSummaryEntry(PageDescriptor page, PageAggregation aggregation, String outputFile) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageId", page.getPageId());
        summary.put("output", outputFile);
        summary.put("forms", aggregation.formCount());
        summary.put("fields", aggregation.totalFields());
        summary.put("outputs", aggregation.outputCount());
        summary.put("frames", ensureList(page.getFrameDefinitions()).size());
        summary.put("frameset", Boolean.TRUE.equals(page.getFramesetPage()));
        summary.put("navigationTargets", ensureList(page.getNavigationTargets()).size());
        summary.put("urlParameters", ensureList(page.getUrlParameterCandidates()).size());
        summary.put("confidenceScore", aggregation.confidenceScore());
        summary.put("confidence", aggregation.confidenceLabel());
        return summary;
    }

    private Path resolveOutputPath(Path outputDir, String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return outputDir.resolve("page.json");
        }
        Path relative;
        try {
            relative = Paths.get(pageId + ".json");
        } catch (Exception ignored) {
            relative = Paths.get(pageId.replace('/', '_') + ".json");
        }
        return outputDir.resolve(relative);
    }

    private double computeConfidenceScore(int formCount,
                                          int totalFields,
                                          int outputCount,
                                          int enrichedFields,
                                          int controllerCandidates) {
        double score = 0.2; // base confidence for discovery
        if (formCount > 0) {
            score += 0.2;
        }
        if (outputCount > 0) {
            score += 0.1;
        }
        if (controllerCandidates > 0) {
            score += 0.2;
        }
        if (totalFields > 0) {
            double enrichmentRatio = enrichedFields / (double) totalFields;
            score += Math.min(0.3, enrichmentRatio * 0.3 + (enrichmentRatio > 0.6 ? 0.1 : 0.0));
        }
        return Math.min(1.0, Math.round(score * 100.0) / 100.0);
    }

    private String toConfidenceLabel(double score, ConfidenceLevel heuristicLevel) {
        if (heuristicLevel == ConfidenceLevel.HIGH || score >= 0.75) {
            return "HIGH";
        }
        if (heuristicLevel == ConfidenceLevel.MEDIUM || score >= 0.45) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private <T> List<T> ensureList(List<T> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source;
    }

    private String normalizeActionName(String action) {
        if (action == null) {
            return null;
        }
        String cleaned = action;
        int queryIndex = cleaned.indexOf('?');
        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }
        cleaned = cleaned.replaceAll("\\\\", "/");
        cleaned = cleaned.replaceAll("^/+", "");
        cleaned = cleaned.replaceAll("\\.(jsp|do|action)$", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]+", " ");
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private String simpleName(String className) {
        int idx = className.lastIndexOf('.') + 1;
        return idx > 0 ? className.substring(idx) : className;
    }

    private String packageName(String className) {
        int idx = className.lastIndexOf('.');
        return idx > 0 ? className.substring(0, idx) : "";
    }

    private static final class PageAggregation {
        private final int formCount;
        private final int outputCount;
        private final int totalFields;
        private final int enrichedFields;
        private final double confidenceScore;
        private final String confidenceLabel;

        private PageAggregation(int formCount,
                                int outputCount,
                                int totalFields,
                                int enrichedFields,
                                double confidenceScore,
                                String confidenceLabel) {
            this.formCount = formCount;
            this.outputCount = outputCount;
            this.totalFields = totalFields;
            this.enrichedFields = enrichedFields;
            this.confidenceScore = confidenceScore;
            this.confidenceLabel = confidenceLabel;
        }

        int formCount() {
            return formCount;
        }

        int outputCount() {
            return outputCount;
        }

        int totalFields() {
            return totalFields;
        }

        int enrichedFields() {
            return enrichedFields;
        }

        double confidenceScore() {
            return confidenceScore;
        }

        String confidenceLabel() {
            return confidenceLabel;
        }
    }

    private enum ConfidenceLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    private static final class ControllerCandidate {
        private final String name;
        private ConfidenceLevel confidence;

        private ControllerCandidate(String name, ConfidenceLevel confidence) {
            this.name = name;
            this.confidence = confidence;
        }

        private void promote(ConfidenceLevel other) {
            if (other.compareTo(this.confidence) > 0) {
                this.confidence = other;
            }
        }

        private String name() {
            return name;
        }

        private ConfidenceLevel confidence() {
            return confidence;
        }
    }

    private record HeuristicResult(List<String> controllerCandidates,
                                   List<String> backingBeanCandidates,
                                   List<String> notes,
                                   ConfidenceLevel bestConfidence) {
    }
}
