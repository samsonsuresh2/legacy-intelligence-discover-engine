package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts JavaScript-based routing behaviors and hints.
 */
public interface JsRoutingExtractor {

    /**
     * Enriches analyzed pages with JavaScript routing hints discovered in their source content.
     *
     * @param rootDir the codebase root for resolving source paths
     * @param pages   the pages to inspect and enrich
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
