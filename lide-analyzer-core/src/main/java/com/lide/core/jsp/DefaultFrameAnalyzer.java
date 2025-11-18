package com.lide.core.jsp;

import com.lide.core.extractors.FrameAnalyzer;
import com.lide.core.model.FrameDefinition;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Extracts frameset and iframe metadata from JSP/HTML documents.
 */
public class DefaultFrameAnalyzer implements FrameAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFrameAnalyzer.class);

    @Override
    public void extract(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        Path normalizedRoot = rootDir == null ? null : rootDir.toAbsolutePath().normalize();

        for (PageDescriptor page : pages) {
            Path sourcePath = page.getSourcePath();
            if (sourcePath == null) {
                page.setFrameDefinitions(List.of());
                page.setFramesetPage(Boolean.FALSE);
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
                LOGGER.warn("Unable to read page {} for frame extraction: {}", sourcePath, ex.getMessage());
                page.setFrameDefinitions(List.of());
                page.setFramesetPage(Boolean.FALSE);
                continue;
            }

            Document document = Jsoup.parse(raw, "", Parser.htmlParser());
            List<FrameDefinition> frames = new ArrayList<>();
            boolean framesetDetected = traverse(document, null, 0, frames);

            page.setFrameDefinitions(frames);
            page.setFramesetPage(framesetDetected || !frames.isEmpty());

            LOGGER.info("Page {} - frames detected: {} (frameset: {})", page.getPageId(), frames.size(), page.getFramesetPage());
        }
    }

    private boolean traverse(Element element, String parentName, int depth, List<FrameDefinition> frames) {
        boolean framesetDetected = false;
        for (Element child : element.children()) {
            String tag = child.tagName().toLowerCase(Locale.ROOT);
            boolean isFrame = "frame".equals(tag) || "iframe".equals(tag);
            boolean isFrameset = "frameset".equals(tag);

            if (isFrame || isFrameset) {
                FrameDefinition definition = new FrameDefinition();
                definition.setFrameName(sanitize(child.attr("name"), child.attr("id")));
                definition.setSource(sanitize(child.attr("src")));
                definition.setParentFrameName(parentName);
                definition.setDepth(depth);
                definition.setTag(tag.toUpperCase(Locale.ROOT));
                definition.setConfidence(FrameDefinition.CONFIDENCE_HIGH);
                frames.add(definition);

                framesetDetected = framesetDetected || isFrameset;
                String nextParent = definition.getFrameName();
                if (nextParent == null || nextParent.isBlank()) {
                    nextParent = definition.getTag() + "@" + depth;
                }
                framesetDetected = traverse(child, nextParent, depth + 1, frames) || framesetDetected;
            } else {
                framesetDetected = traverse(child, parentName, depth, frames) || framesetDetected;
            }
        }
        return framesetDetected;
    }

    private String sanitize(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String value : candidates) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
