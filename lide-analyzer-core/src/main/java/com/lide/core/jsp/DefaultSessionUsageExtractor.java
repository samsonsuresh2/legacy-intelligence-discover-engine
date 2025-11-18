package com.lide.core.jsp;

import com.lide.core.extractors.SessionUsageExtractor;
import com.lide.core.model.PageDescriptor;
import com.lide.core.model.SessionDependency;
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
 * Extracts session attribute usage from JSP/HTML sources.
 */
public class DefaultSessionUsageExtractor implements SessionUsageExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSessionUsageExtractor.class);

    private static final Pattern EL_SESSION_PATTERN = Pattern.compile("\\$\\{sessionScope\\.([A-Za-z0-9_]+)}");
    private static final Pattern SESSION_GET_PATTERN = Pattern.compile("session\\.getAttribute\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern REQUEST_SESSION_GET_PATTERN = Pattern.compile("request\\.getSession\\(\\)\\.getAttribute\\(\\s*\"([^\"]+)\"\\s*\\)");

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setSessionDependencies(List.of());
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
                LOGGER.warn("Unable to read page {} for session usage extraction: {}", sourcePath, ex.getMessage());
                page.setSessionDependencies(List.of());
                continue;
            }

            Set<String> seenKeys = new LinkedHashSet<>();
            List<SessionDependency> dependencies = new ArrayList<>();

            collect(raw, EL_SESSION_PATTERN, "EL", dependencies, seenKeys);
            collect(raw, SESSION_GET_PATTERN, "session.getAttribute", dependencies, seenKeys);
            collect(raw, REQUEST_SESSION_GET_PATTERN, "request.getSession().getAttribute", dependencies, seenKeys);

            page.setSessionDependencies(dependencies);
            LOGGER.info("Page {} - session dependencies detected: {}", page.getPageId(), dependencies.size());
        }
    }

    private void collect(String raw,
                         Pattern pattern,
                         String source,
                         List<SessionDependency> dependencies,
                         Set<String> seenKeys) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) {
            String key = matcher.group(1);
            String signature = key + "|" + source;
            if (!seenKeys.add(signature)) {
                continue;
            }

            SessionDependency dependency = new SessionDependency();
            dependency.setKey(key);
            dependency.setSource(source);
            dependency.setSnippet(snippet(raw, matcher.start(0), matcher.end(0)));
            dependency.setConfidence(SessionDependency.CONFIDENCE_HIGH);
            dependencies.add(dependency);
        }
    }

    private String snippet(String raw, int start, int end) {
        int from = Math.max(0, start - 40);
        int to = Math.min(raw.length(), end + 40);
        return raw.substring(from, to).replaceAll("\n", " ");
    }
}
