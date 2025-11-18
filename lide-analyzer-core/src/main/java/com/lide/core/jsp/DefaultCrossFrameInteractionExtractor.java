package com.lide.core.jsp;

import com.lide.core.extractors.CrossFrameInteractionExtractor;
import com.lide.core.model.CrossFrameInteraction;
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
 * Default extractor that identifies script-based cross-frame interactions such as parent frame navigation.
 */
public class DefaultCrossFrameInteractionExtractor implements CrossFrameInteractionExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCrossFrameInteractionExtractor.class);

    private static final Pattern PARENT_LOCATION_PATTERN = Pattern.compile(
            "parent\\.([A-Za-z0-9_]+)\\.location\\s*[:=]\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOW_PARENT_PATTERN = Pattern.compile(
            "window\\.parent(?:\\.([A-Za-z0-9_]+))?\\.location\\s*[:=]\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_FRAMES_PATTERN = Pattern.compile(
            "top\\.frames\\[['\"]?([A-Za-z0-9_]+)['\"]?\\]?\\.location\\s*[:=]\\s*['\"]([^'\"]+\\.jspf?[^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setCrossFrameInteractions(List.of());
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
                LOGGER.warn("Unable to read page {} for cross-frame extraction: {}", sourcePath, ex.getMessage());
                page.setCrossFrameInteractions(List.of());
                continue;
            }

            Set<String> seen = new LinkedHashSet<>();
            List<CrossFrameInteraction> interactions = new ArrayList<>();

            collectMatches(raw, PARENT_LOCATION_PATTERN, interactions, seen, "parent");
            collectMatches(raw, WINDOW_PARENT_PATTERN, interactions, seen, "window.parent");
            collectMatches(raw, TOP_FRAMES_PATTERN, interactions, seen, "top.frames");

            page.setCrossFrameInteractions(interactions);
            LOGGER.info("Page {} - cross-frame interactions detected: {}", page.getPageId(), interactions.size());
        }
    }

    private void collectMatches(String raw,
                                Pattern pattern,
                                List<CrossFrameInteraction> interactions,
                                Set<String> seen,
                                String defaultFrame) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) {
            String frame = matcher.groupCount() >= 1 ? matcher.group(1) : null;
            String target = matcher.groupCount() >= 2 ? matcher.group(2) : null;
            addInteraction(interactions, seen, frame != null ? frame : defaultFrame, target,
                    snippet(raw, matcher.start(), matcher.end()));
        }
    }

    private void addInteraction(List<CrossFrameInteraction> interactions,
                                Set<String> seen,
                                String frame,
                                String target,
                                String snippet) {
        if (target == null || target.isBlank()) {
            return;
        }
        String key = (frame == null ? "" : frame) + "|" + target + "|" + snippet;
        if (!seen.add(key)) {
            return;
        }

        CrossFrameInteraction interaction = new CrossFrameInteraction();
        interaction.setFromFrame(frame);
        interaction.setToJsp(target);
        interaction.setType(CrossFrameInteraction.TYPE_LOCATION_CHANGE);
        interaction.setSnippet(snippet);
        interaction.setConfidence(CrossFrameInteraction.CONFIDENCE_MEDIUM);
        interactions.add(interaction);
    }

    private String snippet(String raw, int start, int end) {
        int from = Math.max(0, start - 40);
        int to = Math.min(raw.length(), end + 40);
        return raw.substring(from, to).replaceAll("\n", " ");
    }
}
