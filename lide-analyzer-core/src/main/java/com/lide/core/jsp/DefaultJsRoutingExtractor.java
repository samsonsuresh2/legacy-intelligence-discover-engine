package com.lide.core.jsp;

import com.lide.core.extractors.JsRoutingExtractor;
import com.lide.core.model.JsRoutingHint;
import com.lide.core.model.PageDescriptor;
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
 * Default implementation that performs lightweight static detection of JavaScript routing hints.
 */
public class DefaultJsRoutingExtractor implements JsRoutingExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJsRoutingExtractor.class);

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?i)(window\\.location(?:\\.href)?|document\\.location(?:\\.href)?|location(?:\\.href)?)\\s*[:=]\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]");
    private static final Pattern FORM_ACTION_PATTERN = Pattern.compile(
            "(?i)document\\.forms\\[[^]]+].action\\s*=\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]");

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setJsRoutingHints(List.of());
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
                LOGGER.warn("Unable to read page {} for JS routing extraction: {}", sourcePath, ex.getMessage());
                page.setJsRoutingHints(List.of());
                continue;
            }

            Set<String> seen = new LinkedHashSet<>();
            List<JsRoutingHint> hints = new ArrayList<>();

            collectLocationRoutes(raw, hints, seen);
            collectFormRoutes(raw, hints, seen);

            page.setJsRoutingHints(hints);
            LOGGER.info("Page {} - JS routing hints detected: {}", page.getPageId(), hints.size());
        }
    }

    private void collectLocationRoutes(String raw, List<JsRoutingHint> hints, Set<String> seen) {
        Matcher matcher = LOCATION_PATTERN.matcher(raw);
        while (matcher.find()) {
            String pattern = matcher.group(1);
            String target = matcher.group(2);
            addHint(hints, seen, target, pattern, snippet(raw, matcher.start(0), matcher.end(0)));
        }
    }

    private void collectFormRoutes(String raw, List<JsRoutingHint> hints, Set<String> seen) {
        Matcher matcher = FORM_ACTION_PATTERN.matcher(raw);
        while (matcher.find()) {
            String target = matcher.group(1);
            addHint(hints, seen, target, "document.forms.action", snippet(raw, matcher.start(0), matcher.end(0)));
        }
    }

    private void addHint(List<JsRoutingHint> hints, Set<String> seen, String target, String sourcePattern, String snippet) {
        if (target == null || target.isBlank()) {
            return;
        }
        String key = target + "|" + sourcePattern + "|" + snippet;
        if (!seen.add(key)) {
            return;
        }

        JsRoutingHint hint = new JsRoutingHint();
        hint.setTargetPage(target);
        hint.setSourcePattern(sourcePattern);
        hint.setSnippet(snippet);
        hint.setConfidence(JsRoutingHint.CONFIDENCE_HIGH);
        hints.add(hint);
    }

    private String snippet(String raw, int start, int end) {
        int from = Math.max(0, start - 40);
        int to = Math.min(raw.length(), end + 40);
        return truncate(raw.substring(from, to).replaceAll("\n", " "), 160);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
