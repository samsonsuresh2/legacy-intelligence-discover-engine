package com.lide.core.jsp;

import com.lide.core.extractors.HiddenFieldStateExtractor;
import com.lide.core.model.HiddenField;
import com.lide.core.model.PageDescriptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts hidden field state hints from JSP/HTML content.
 */
public class DefaultHiddenFieldStateExtractor implements HiddenFieldStateExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHiddenFieldStateExtractor.class);

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{[^}]+}");

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setHiddenFields(List.of());
                continue;
            }

            Path absolute = sourcePath;
            if (!absolute.isAbsolute() && normalizedRoot != null) {
                absolute = normalizedRoot.resolve(sourcePath);
            }

            String raw;
            try {
                raw = Files.readString(absolute, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOGGER.warn("Unable to read page {} for hidden field extraction: {}", sourcePath, ex.getMessage());
                page.setHiddenFields(List.of());
                continue;
            }

            Document document = Jsoup.parse(raw, "", Parser.htmlParser());
            List<HiddenField> hiddenFields = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            for (Element element : document.select("input[type=hidden], s\\:hidden, form\\:hidden, html\\:hidden")) {
                HiddenField field = toHiddenField(element);
                if (field.getName() == null || !seen.add(field.getName() + "|" + field.getSnippet())) {
                    continue;
                }
                hiddenFields.add(field);
            }

            page.setHiddenFields(hiddenFields);
            LOGGER.info("Page {} - hidden fields detected: {}", page.getPageId(), hiddenFields.size());
        }
    }

    private HiddenField toHiddenField(Element element) {
        HiddenField field = new HiddenField();
        field.setName(firstNonBlank(element.attr("name"), element.attr("id"), element.attr("property"), element.attr("path")));
        field.setDefaultValue(emptyToNull(element.attr("value")));
        field.setExpression(findExpression(element));
        field.setSnippet(truncate(element.outerHtml(), 200));
        field.setConfidence(HiddenField.CONFIDENCE_HIGH);
        return field;
    }

    private String findExpression(Element element) {
        String candidate = element.attr("value");
        Matcher matcher = EXPRESSION_PATTERN.matcher(candidate);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
