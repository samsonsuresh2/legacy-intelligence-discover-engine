package com.lide.core.jsp;

import com.lide.core.extractors.UrlParameterExtractor;
import com.lide.core.model.PageDescriptor;
import com.lide.core.model.UrlParameter;
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
 * Extracts URL parameter candidates from anchors and JavaScript snippets.
 */
public class DefaultUrlParameterExtractor implements UrlParameterExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUrlParameterExtractor.class);

    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+(?==)");
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?i)(window\\.location|location|parent\\.frame\\.location|parent\\.location)\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern STRING_WITH_QUERY = Pattern.compile("(['\"])([^'\"]*\?[^'\"]*)\\1");
    private static final Pattern QUERY_TOKEN_PATTERN = Pattern.compile("[^\\s'\"<>]*\?[^\\s'\"<>]*");

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setUrlParameterCandidates(List.of());
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
                LOGGER.warn("Unable to read page {} for URL parameter extraction: {}", sourcePath, ex.getMessage());
                page.setUrlParameterCandidates(List.of());
                continue;
            }

            Document document = Jsoup.parse(raw, "", Parser.htmlParser());
            Set<String> seen = new LinkedHashSet<>();
            List<UrlParameter> parameters = new ArrayList<>();

            collectFromAnchors(document, parameters, seen);
            collectFromForms(document, parameters, seen);
            collectFromScripts(raw, parameters, seen);

            page.setUrlParameterCandidates(parameters);
            LOGGER.info("Page {} - URL parameters detected: {}", page.getPageId(), parameters.size());
        }
    }

    private void collectFromAnchors(Document document, List<UrlParameter> parameters, Set<String> seen) {
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("href");
            extractParametersFromCandidate(href, "href", truncate(anchor.outerHtml(), 160), parameters, seen);
        }
    }

    private void collectFromForms(Document document, List<UrlParameter> parameters, Set<String> seen) {
        for (Element form : document.select("form[action]")) {
            String action = form.attr("action");
            extractParametersFromCandidate(action, "form-action", truncate(form.outerHtml(), 160), parameters, seen);
        }
    }

    private void collectFromScripts(String raw, List<UrlParameter> parameters, Set<String> seen) {
        Matcher locationMatcher = LOCATION_PATTERN.matcher(raw);
        while (locationMatcher.find()) {
            String target = locationMatcher.group(2);
            extractParametersFromCandidate(target, "script-location", snippet(raw, locationMatcher.start(0), locationMatcher.end(0)), parameters, seen);
        }

        Matcher literalMatcher = STRING_WITH_QUERY.matcher(raw);
        while (literalMatcher.find()) {
            String candidate = literalMatcher.group(2);
            extractParametersFromCandidate(candidate, "js-string", snippet(raw, literalMatcher.start(0), literalMatcher.end(0)), parameters, seen);
        }

        Matcher queryMatcher = QUERY_TOKEN_PATTERN.matcher(raw);
        while (queryMatcher.find()) {
            String candidate = queryMatcher.group();
            extractParametersFromCandidate(candidate, "inline-query", snippet(raw, queryMatcher.start(0), queryMatcher.end(0)), parameters, seen);
        }
    }

    private void extractParametersFromCandidate(String candidate,
                                                String source,
                                                String snippet,
                                                List<UrlParameter> parameters,
                                                Set<String> seen) {
        if (candidate == null || !candidate.contains("?")) {
            return;
        }

        Matcher matcher = PARAM_NAME_PATTERN.matcher(candidate);
        while (matcher.find()) {
            String param = matcher.group();
            String key = param + "|" + source + "|" + snippet;
            if (!seen.add(key)) {
                continue;
            }

            UrlParameter urlParameter = new UrlParameter();
            urlParameter.setName(param);
            urlParameter.setSource(source);
            urlParameter.setSnippet(snippet);
            urlParameter.setConfidence(UrlParameter.CONFIDENCE_HIGH);
            parameters.add(urlParameter);
        }
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
