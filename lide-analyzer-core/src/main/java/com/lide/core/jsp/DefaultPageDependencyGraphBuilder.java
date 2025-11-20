package com.lide.core.jsp;

import com.lide.core.extractors.PageDependencyGraphBuilder;
import com.lide.core.model.FrameDefinition;
import com.lide.core.model.JsRoutingHint;
import com.lide.core.model.NavigationTarget;
import com.lide.core.model.PageDependency;
import com.lide.core.model.PageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds static page dependencies derived from navigation, frames, and JavaScript routing hints.
 */
public class DefaultPageDependencyGraphBuilder implements PageDependencyGraphBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPageDependencyGraphBuilder.class);

    @Override
    public void build(Path rootDir, List<PageDescriptor> pages) {
        Objects.requireNonNull(pages, "pages");

        for (PageDescriptor page : pages) {
            String from = page.getPageId() != null ? page.getPageId() : deriveFromSource(rootDir, page);
            Set<String> seen = new LinkedHashSet<>();
            List<PageDependency> dependencies = new ArrayList<>();

            for (NavigationTarget target : ensureList(page.getNavigationTargets())) {
                registerDependency(dependencies, seen, from, target.getTargetPage(), "navigationTarget");
            }

            for (FrameDefinition frame : ensureList(page.getFrameDefinitions())) {
                registerDependency(dependencies, seen, from, frame.getSource(), "frameSource");
            }

            for (JsRoutingHint hint : ensureList(page.getJsRoutingHints())) {
                registerDependency(dependencies, seen, from, hint.getTargetPage(), "jsRoutingHint");
            }

            page.setPageDependencies(dependencies);
            LOGGER.info("Page {} - dependencies recorded: {}", from, dependencies.size());
        }
    }

    private String deriveFromSource(Path rootDir, PageDescriptor page) {
        Path sourcePath = page.getSourcePath();
        if (sourcePath == null) {
            return "unknown";
        }
        if (rootDir != null) {
            Path normalizedRoot = rootDir.toAbsolutePath().normalize();
            Path absolute = sourcePath.toAbsolutePath().normalize();
            if (absolute.startsWith(normalizedRoot)) {
                return normalizedRoot.relativize(absolute).toString().replace('\\', '/');
            }
        }
        return sourcePath.toString();
    }

    private void registerDependency(List<PageDependency> dependencies, Set<String> seen, String from, String to, String type) {
        if (to == null || to.isBlank()) {
            return;
        }
        String key = from + "|" + to + "|" + type;
        if (!seen.add(key)) {
            return;
        }
        dependencies.add(new PageDependency(from, to, type));
    }

    private <T> List<T> ensureList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
