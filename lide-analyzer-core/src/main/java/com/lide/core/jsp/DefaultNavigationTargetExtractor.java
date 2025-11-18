package com.lide.core.jsp;

import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.model.NavigationTarget;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default navigation target extractor that scans JSP/HTML content for links to other JSPs.
 */
public class DefaultNavigationTargetExtractor implements NavigationTargetExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNavigationTargetExtractor.class);

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?i)(window\\.location|location|parent\\.frame\\.location|parent\\.location)\\s*[:=]\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]");
    private static final Pattern JSP_STRING_PATTERN = Pattern.compile("(['\"])([^'\"]+\\.jspf?[^'\"]*)\\1", Pattern.CASE_INSENSITIVE);

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setNavigationTargets(List.of());
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
                LOGGER.warn("Unable to read page {} for navigation extraction: {}", sourcePath, ex.getMessage());
                page.setNavigationTargets(List.of());
                continue;
            }

            Document document = Jsoup.parse(raw, "", Parser.htmlParser());
            Set<String> seen = new LinkedHashSet<>();
            List<NavigationTarget> targets = new ArrayList<>();

            collectFromAnchors(document, targets, seen);
            collectFromScripts(raw, targets, seen);

            page.setNavigationTargets(targets);
            LOGGER.info("Page {} - navigation targets detected: {}", page.getPageId(), targets.size());
        }
    }

    private void collectFromAnchors(Document document, List<NavigationTarget> targets, Set<String> seen) {
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("href");
            if (!containsJspReference(href)) {
                continue;
            }
            addTarget(targets, seen, href, "href", truncate(anchor.outerHtml(), 160));
        }
    }

    private void collectFromScripts(String raw, List<NavigationTarget> targets, Set<String> seen) {
        Matcher locationMatcher = LOCATION_PATTERN.matcher(raw);
        while (locationMatcher.find()) {
            String target = locationMatcher.group(2);
            addTarget(targets, seen, target, "script-location", snippet(raw, locationMatcher.start(0), locationMatcher.end(0)));
        }

        Matcher jspStringMatcher = JSP_STRING_PATTERN.matcher(raw);
        while (jspStringMatcher.find()) {
            String candidate = jspStringMatcher.group(2);
            if (!containsJspReference(candidate)) {
                continue;
            }
            addTarget(targets, seen, candidate, "js-string", snippet(raw, jspStringMatcher.start(0), jspStringMatcher.end(0)));
        }
    }

    private boolean containsJspReference(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains(".jsp") || lower.contains(".jspf");
    }

    private void addTarget(List<NavigationTarget> targets, Set<String> seen, String target, String sourcePattern, String snippet) {
        if (target == null || target.isBlank()) {
            return;
        }
        String key = target + "|" + sourcePattern + "|" + snippet;
        if (!seen.add(key)) {
            return;
        }

        NavigationTarget navigationTarget = new NavigationTarget();
        navigationTarget.setTargetPage(target);
        navigationTarget.setSourcePattern(sourcePattern);
        navigationTarget.setSnippet(snippet);
        navigationTarget.setConfidence(NavigationTarget.CONFIDENCE_HIGH);
        targets.add(navigationTarget);
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
