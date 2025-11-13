package com.lide.core.jsp;

import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.FieldDescriptor;
import com.lide.core.model.FormDescriptor;
import com.lide.core.model.OptionDescriptor;
import com.lide.core.model.OutputFieldDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDescriptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation that parses JSP/HTML documents with Jsoup and extracts form metadata.
 */
public class DefaultJspAnalyzer implements JspAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJspAnalyzer.class);
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{[^}]+}|%\\{[^}]+}|#\\{[^}]+}");

    @Override
    public List<PageDescriptor> analyze(Path rootDir, CodebaseIndex index) {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(index, "index");

        List<PageDescriptor> descriptors = new ArrayList<>();
        Set<Path> pages = new LinkedHashSet<>();
        pages.addAll(index.getJspFiles());
        pages.addAll(index.getHtmlFiles());

        Path normalizedRoot = rootDir.toAbsolutePath().normalize();

        for (Path pagePath : pages) {
            try {
                PageDescriptor descriptor = analyzePage(normalizedRoot, pagePath);
                descriptors.add(descriptor);
            } catch (IOException ex) {
                LOGGER.warn("Failed to analyze {}: {}", pagePath, ex.getMessage());
            }
        }

        LOGGER.info("Completed JSP/HTML analysis for {} pages", descriptors.size());
        return descriptors;
    }

    private PageDescriptor analyzePage(Path normalizedRoot, Path pagePath) throws IOException {
        Path absolutePath = pagePath.toAbsolutePath().normalize();
        String raw = Files.readString(pagePath, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(raw, "", org.jsoup.parser.Parser.htmlParser());

        PageDescriptor descriptor = new PageDescriptor();
        descriptor.setSourcePath(pagePath);
        descriptor.setPageId(computePageId(normalizedRoot, absolutePath));
        descriptor.setTitle(extractTitle(document));

        List<FormDescriptor> forms = analyzeForms(document);
        descriptor.setForms(forms);
        List<OutputSectionDescriptor> outputs = analyzeOutputs(document);
        descriptor.setOutputs(outputs);
        descriptor.setControllerCandidates(new ArrayList<>());
        descriptor.setNotes(new ArrayList<>());

        LOGGER.info("Page {} - forms detected: {}", descriptor.getPageId(), forms.size());
        for (FormDescriptor form : forms) {
            String formId = Optional.ofNullable(form.getFormId()).filter(id -> !id.isBlank()).orElse("<unnamed>");
            int fieldCount = form.getFields() == null ? 0 : form.getFields().size();
            LOGGER.info("  Form {} - fields detected: {}", formId, fieldCount);
        }

        LOGGER.info("Page {} - output sections detected: {}", descriptor.getPageId(), outputs.size());
        for (OutputSectionDescriptor output : outputs) {
            String sectionId = Optional.ofNullable(output.getSectionId()).filter(id -> !id.isBlank()).orElse("<anonymous>");
            int fieldCount = output.getFields() == null ? 0 : output.getFields().size();
            LOGGER.info("  Output {} [{}] - fields detected: {}", sectionId, Optional.ofNullable(output.getType()).orElse("UNKNOWN"), fieldCount);
        }

        return descriptor;
    }

    private String computePageId(Path normalizedRoot, Path absolutePath) {
        if (absolutePath.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(absolutePath).toString().replace('\\', '/');
        }
        return absolutePath.toString();
    }

    private String extractTitle(Document document) {
        Element titleElement = document.selectFirst("title");
        return titleElement != null ? sanitizeText(titleElement.text()) : null;
    }

    private List<FormDescriptor> analyzeForms(Document document) {
        List<FormDescriptor> forms = new ArrayList<>();
        for (Element formElement : locateFormElements(document)) {
            forms.add(analyzeForm(document, formElement));
        }
        return forms;
    }

    private List<OutputSectionDescriptor> analyzeOutputs(Document document) {
        List<OutputSectionDescriptor> outputs = new ArrayList<>();
        outputs.addAll(analyzeTableOutputs(document));
        outputs.addAll(analyzeTextOutputs(document));
        return outputs;
    }

    private List<Element> locateFormElements(Document document) {
        Set<Element> formElements = new LinkedHashSet<>();
        formElements.addAll(document.select("form"));
        formElements.addAll(document.select("s\\\:form"));
        formElements.addAll(document.select("html\\\:form"));
        formElements.addAll(document.select("form\\\:form"));
        return new ArrayList<>(formElements);
    }

    private List<OutputSectionDescriptor> analyzeTableOutputs(Document document) {
        List<OutputSectionDescriptor> sections = new ArrayList<>();
        for (Element tableElement : document.select("table")) {
            List<OutputFieldDescriptor> fields = extractTableFields(tableElement);
            if (fields.isEmpty()) {
                continue;
            }

            IterationContext context = resolveIterationContext(tableElement);
            OutputSectionDescriptor descriptor = new OutputSectionDescriptor();
            descriptor.setSectionId(sanitizeAttribute(tableElement, "id"));
            descriptor.setType("TABLE");
            descriptor.setItemVariable(context.itemVariable);
            descriptor.setItemsExpression(context.itemsExpression);
            descriptor.setFields(fields);
            descriptor.setNotes(context.notes);

            sections.add(descriptor);
        }
        return sections;
    }

    private List<OutputSectionDescriptor> analyzeTextOutputs(Document document) {
        List<OutputSectionDescriptor> sections = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            if (element == null || element == document) {
                continue;
            }
            String tagName = element.tagName();
            if ("script".equalsIgnoreCase(tagName) || "style".equalsIgnoreCase(tagName)) {
                continue;
            }
            if (element.closest("table") != null || element.closest("form") != null) {
                continue;
            }

            String ownText = element.ownText();
            if (ownText == null || ownText.isBlank()) {
                continue;
            }
            List<String> expressions = extractExpressions(ownText);
            if (expressions.isEmpty()) {
                continue;
            }

            String normalizedBinding = normalizeExpression(expressions.get(0));
            OutputFieldDescriptor field = new OutputFieldDescriptor();
            field.setBindingExpression(normalizedBinding);
            field.setName(deriveFieldName(normalizedBinding));
            field.setLabel(sanitizeText(stripExpressions(ownText)));
            field.setRawText(sanitizeText(stripExpressions(element.text())));

            OutputSectionDescriptor descriptor = new OutputSectionDescriptor();
            descriptor.setSectionId(sanitizeAttribute(element, "id"));
            descriptor.setType("TEXT_BLOCK");
            descriptor.setItemVariable(null);
            descriptor.setItemsExpression(null);
            descriptor.setFields(Collections.singletonList(field));

            sections.add(descriptor);
        }
        return sections;
    }

    private FormDescriptor analyzeForm(Document document, Element formElement) {
        FormDescriptor descriptor = new FormDescriptor();
        descriptor.setFormId(resolveFormId(formElement));
        descriptor.setAction(sanitizeAttribute(formElement, "action"));
        descriptor.setMethod(resolveMethod(formElement));

        List<FieldDescriptor> fields = new ArrayList<>();
        for (Element element : formElement.getAllElements()) {
            if (element == formElement || !isFieldElement(element)) {
                continue;
            }
            FieldDescriptor field = analyzeField(document, element);
            if (field != null) {
                fields.add(field);
            }
        }
        descriptor.setFields(fields);
        descriptor.setNotes(new ArrayList<>());
        return descriptor;
    }

    private String resolveFormId(Element formElement) {
        String id = sanitizeAttribute(formElement, "id");
        if (id != null) {
            return id;
        }
        id = sanitizeAttribute(formElement, "name");
        if (id != null) {
            return id;
        }
        String tagName = formElement.tagName();
        if (tagName.contains(":")) {
            String localName = tagName.substring(tagName.indexOf(':') + 1);
            String namespace = tagName.substring(0, tagName.indexOf(':'));
            return namespace + ":" + localName;
        }
        return null;
    }

    private String resolveMethod(Element formElement) {
        String method = sanitizeAttribute(formElement, "method");
        if (method != null && !method.isBlank()) {
            return method.toUpperCase(Locale.ROOT);
        }
        String typeAttr = sanitizeAttribute(formElement, "type");
        if (typeAttr != null && !typeAttr.isBlank()) {
            return typeAttr.toUpperCase(Locale.ROOT);
        }
        return "GET";
    }

    private boolean isFieldElement(Element element) {
        String tagName = element.tagName();
        if (tagName == null) {
            return false;
        }
        switch (tagName) {
            case "input":
            case "select":
            case "textarea":
            case "button":
                return true;
            default:
                break;
        }

        if (!tagName.contains(":")) {
            return false;
        }

        String localName = tagName.substring(tagName.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        String prefix = tagName.substring(0, tagName.indexOf(':')).toLowerCase(Locale.ROOT);

        Set<String> supportedPrefixes = Set.of("s", "form", "html");
        if (!supportedPrefixes.contains(prefix)) {
            return false;
        }

        Set<String> supportedLocalNames = Set.of(
                "input", "textfield", "textarea", "password", "checkbox", "radio",
                "radiobutton", "select", "option", "button", "submit", "hidden", "file"
        );
        return supportedLocalNames.contains(localName);
    }

    private FieldDescriptor analyzeField(Document document, Element element) {
        FieldDescriptor descriptor = new FieldDescriptor();
        descriptor.setSourceTagName(element.tagName());
        descriptor.setId(sanitizeAttribute(element, "id"));
        descriptor.setName(resolveFieldName(element));
        descriptor.setType(resolveFieldType(element));
        descriptor.setLabel(resolveFieldLabel(document, element));
        descriptor.setRequired(resolveRequired(element));
        descriptor.setMaxLength(resolveIntegerAttribute(element, "maxlength", "maxLength"));
        descriptor.setMinLength(resolveIntegerAttribute(element, "minlength", "minLength"));
        descriptor.setPattern(sanitizeAttribute(element, "pattern"));
        descriptor.setPlaceholder(sanitizeAttribute(element, "placeholder"));
        descriptor.setDefaultValue(sanitizeAttribute(element, "value"));
        descriptor.setMinValue(sanitizeAttribute(element, "min"));
        descriptor.setMaxValue(sanitizeAttribute(element, "max"));
        descriptor.setOptions(resolveOptions(element));
        descriptor.setBindingExpressions(resolveBindingExpressions(element));
        descriptor.setConstraints(new ArrayList<>());
        descriptor.setNotes(new ArrayList<>());

        return descriptor;
    }

    private List<OutputFieldDescriptor> extractTableFields(Element tableElement) {
        Element headerRow = findHeaderRow(tableElement);
        List<String> headerLabels = headerRow != null ? extractHeaderLabels(headerRow) : Collections.emptyList();
        Element dataRow = findFirstDataRow(tableElement);
        if (dataRow == null) {
            return Collections.emptyList();
        }

        List<OutputFieldDescriptor> fields = new ArrayList<>();
        int columnIndex = 0;
        for (Element cell : dataRow.children()) {
            String tagName = cell.tagName();
            if (!"td".equalsIgnoreCase(tagName) && !"th".equalsIgnoreCase(tagName)) {
                continue;
            }

            String binding = firstBinding(cell);
            String rawText = sanitizeText(stripExpressions(cell.text()));
            if (binding == null && (rawText == null || rawText.isBlank())) {
                columnIndex++;
                continue;
            }

            OutputFieldDescriptor field = new OutputFieldDescriptor();
            field.setLabel(columnIndex < headerLabels.size() ? headerLabels.get(columnIndex) : null);
            field.setBindingExpression(binding);
            field.setName(deriveFieldName(binding));
            field.setRawText(rawText);

            List<String> notes = new ArrayList<>();
            if (binding == null) {
                notes.add("No binding expression detected");
            }
            if (!notes.isEmpty()) {
                field.setNotes(notes);
            }

            fields.add(field);
            columnIndex++;
        }

        return fields;
    }

    private Element findHeaderRow(Element tableElement) {
        Element headerRow = tableElement.selectFirst("thead tr");
        if (headerRow != null && !headerRow.select("th").isEmpty()) {
            return headerRow;
        }
        for (Element row : tableElement.select("tr")) {
            if (!row.select("th").isEmpty()) {
                return row;
            }
        }
        return null;
    }

    private List<String> extractHeaderLabels(Element headerRow) {
        List<String> labels = new ArrayList<>();
        for (Element headerCell : headerRow.children()) {
            if (!"th".equalsIgnoreCase(headerCell.tagName())) {
                continue;
            }
            labels.add(sanitizeText(stripExpressions(headerCell.text())));
        }
        return labels;
    }

    private Element findFirstDataRow(Element tableElement) {
        for (Element row : tableElement.select("tbody tr")) {
            if (!row.select("td").isEmpty()) {
                return row;
            }
        }
        for (Element row : tableElement.select("tr")) {
            if (!row.select("td").isEmpty()) {
                return row;
            }
        }
        return null;
    }

    private String firstBinding(Element element) {
        List<String> expressions = findBindingExpressions(element);
        if (expressions.isEmpty()) {
            return null;
        }
        return normalizeExpression(expressions.get(0));
    }

    private List<String> findBindingExpressions(Element element) {
        Set<String> expressions = new LinkedHashSet<>(resolveBindingExpressions(element));
        String text = element.text();
        expressions.addAll(extractExpressions(text));
        if (expressions.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(expressions);
    }

    private IterationContext resolveIterationContext(Element element) {
        Element current = element;
        while (current != null) {
            String tagName = current.tagName();
            if (tagName != null && tagName.contains(":")) {
                String localName = tagName.substring(tagName.indexOf(':') + 1).toLowerCase(Locale.ROOT);
                if ("foreach".equals(localName) || "iterate".equals(localName) || "fortokens".equals(localName)) {
                    String itemVariable = firstNonBlank(
                            getRawAttribute(current, "var"),
                            getRawAttribute(current, "id"),
                            getRawAttribute(current, "item")
                    );
                    String itemsExpression = firstNonBlank(
                            getRawAttribute(current, "items"),
                            getRawAttribute(current, "collection"),
                            getRawAttribute(current, "list"),
                            getRawAttribute(current, "value"),
                            getRawAttribute(current, "name")
                    );

                    List<String> notes = new ArrayList<>();
                    if (itemsExpression == null) {
                        notes.add("Iteration tag detected without collection binding");
                    }

                    return new IterationContext(
                            sanitizeText(itemVariable),
                            normalizeExpression(itemsExpression),
                            notes.isEmpty() ? Collections.emptyList() : notes
                    );
                }
            }
            current = current.parent();
        }
        return IterationContext.empty();
    }

    private List<String> extractExpressions(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> matches = new LinkedHashSet<>();
        Matcher matcher = EXPRESSION_PATTERN.matcher(raw);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(matches);
    }

    private String normalizeExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ((trimmed.startsWith("${") || trimmed.startsWith("%{") || trimmed.startsWith("#{")) && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String deriveFieldName(String bindingExpression) {
        if (bindingExpression == null || bindingExpression.isBlank()) {
            return null;
        }
        String normalized = bindingExpression.trim();
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < normalized.length() - 1) {
            return normalized.substring(lastDot + 1);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String getRawAttribute(Element element, String attribute) {
        if (element == null || attribute == null || !element.hasAttr(attribute)) {
            return null;
        }
        String value = element.attr(attribute);
        return value != null ? value.trim() : null;
    }

    private static final class IterationContext {
        private final String itemVariable;
        private final String itemsExpression;
        private final List<String> notes;

        private IterationContext(String itemVariable, String itemsExpression, List<String> notes) {
            this.itemVariable = itemVariable;
            this.itemsExpression = itemsExpression;
            this.notes = notes;
        }

        private static IterationContext empty() {
            return new IterationContext(null, null, Collections.emptyList());
        }
    }

    private String resolveFieldName(Element element) {
        String name = sanitizeAttribute(element, "name");
        if (name != null) {
            return name;
        }
        if (element.tagName().contains(":")) {
            String localName = element.tagName().substring(element.tagName().indexOf(':') + 1);
            switch (localName) {
                case "input":
                case "textfield":
                case "textarea":
                case "password":
                case "checkbox":
                case "radiobutton":
                case "radio":
                case "select":
                case "hidden":
                    String path = sanitizeAttribute(element, "path");
                    if (path != null) {
                        return path;
                    }
                    String property = sanitizeAttribute(element, "property");
                    if (property != null) {
                        return property;
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    private String resolveFieldType(Element element) {
        String tagName = element.tagName();
        String localName = tagName;
        if (tagName.contains(":")) {
            localName = tagName.substring(tagName.indexOf(':') + 1);
        }
        localName = localName.toLowerCase(Locale.ROOT);

        switch (localName) {
            case "input":
                String htmlType = sanitizeAttribute(element, "type");
                return htmlType != null ? htmlType.toLowerCase(Locale.ROOT) : "text";
            case "textfield":
                return "text";
            case "password":
                return "password";
            case "textarea":
                return "textarea";
            case "checkbox":
                return "checkbox";
            case "radio":
            case "radiobutton":
                return "radio";
            case "select":
                return "select";
            case "hidden":
                return "hidden";
            case "file":
                return "file";
            case "button":
            case "submit":
                String buttonType = sanitizeAttribute(element, "type");
                return buttonType != null ? buttonType.toLowerCase(Locale.ROOT) : "button";
            default:
                break;
        }

        if ("button".equals(tagName)) {
            String buttonType = sanitizeAttribute(element, "type");
            return buttonType != null ? buttonType.toLowerCase(Locale.ROOT) : "button";
        }

        return localName;
    }

    private String resolveFieldLabel(Document document, Element element) {
        String label = sanitizeAttribute(element, "label");
        if (label != null) {
            return label;
        }
        label = sanitizeAttribute(element, "title");
        if (label != null) {
            return label;
        }
        label = sanitizeAttribute(element, "aria-label");
        if (label != null) {
            return label;
        }
        if (element.parent() != null && "label".equalsIgnoreCase(element.parent().tagName())) {
            return sanitizeText(element.parent().text());
        }
        String id = sanitizeAttribute(element, "id");
        if (id != null) {
            Element forLabel = document.selectFirst("label[for='" + id + "']");
            if (forLabel != null) {
                return sanitizeText(forLabel.text());
            }
        }
        return null;
    }

    private boolean resolveRequired(Element element) {
        if (element.hasAttr("required")) {
            String raw = element.attr("required");
            if (raw == null || raw.isBlank()) {
                return true;
            }
            return parseBoolean(raw);
        }
        String requiredAttr = element.hasAttr("data-required") ? element.attr("data-required") : null;
        if (requiredAttr != null && !requiredAttr.isBlank()) {
            return parseBoolean(requiredAttr);
        }
        String strutsRequired = element.hasAttr("required") ? element.attr("required") : element.attr("validate");
        if (strutsRequired != null && !strutsRequired.isBlank()) {
            return parseBoolean(strutsRequired);
        }
        return false;
    }

    private Integer resolveIntegerAttribute(Element element, String... names) {
        for (String name : names) {
            String value = sanitizeAttribute(element, name);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    LOGGER.debug("Unable to parse integer attribute {}='{}'", name, value);
                }
            }
        }
        return null;
    }

    private List<OptionDescriptor> resolveOptions(Element element) {
        if (!"select".equalsIgnoreCase(element.tagName())
                && !element.tagName().toLowerCase(Locale.ROOT).endsWith(":select")) {
            return Collections.emptyList();
        }

        Elements optionElements = element.select("option");
        if (optionElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<OptionDescriptor> options = new ArrayList<>();
        for (Element optionElement : optionElements) {
            OptionDescriptor option = new OptionDescriptor();
            option.setValue(sanitizeAttribute(optionElement, "value"));
            option.setLabel(sanitizeText(optionElement.text()));
            option.setSelected(optionElement.hasAttr("selected"));
            options.add(option);
        }
        return options;
    }

    private List<String> resolveBindingExpressions(Element element) {
        Set<String> expressions = new LinkedHashSet<>();
        for (Attribute attribute : element.attributes()) {
            String value = attribute.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            Matcher matcher = EXPRESSION_PATTERN.matcher(value);
            while (matcher.find()) {
                expressions.add(matcher.group());
            }
        }
        if (expressions.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(expressions);
    }

    private String sanitizeAttribute(Element element, String attribute) {
        if (element == null || attribute == null || !element.hasAttr(attribute)) {
            return null;
        }
        String raw = element.attr(attribute);
        return sanitizeText(stripExpressions(raw));
    }

    private String sanitizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stripExpressions(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String cleaned = EXPRESSION_PATTERN.matcher(raw).replaceAll("");
        cleaned = cleaned.replace("<%=", "").replace("<%", "").replace("%>", "");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("y");
    }
}
