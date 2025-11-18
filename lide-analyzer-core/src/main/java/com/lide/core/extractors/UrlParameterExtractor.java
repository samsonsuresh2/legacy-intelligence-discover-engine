package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts potential URL parameters and hints from JSP/HTML content and embedded scripts.
 */
public interface UrlParameterExtractor {

    /**
     * Enriches the provided page descriptors with URL parameter candidates discovered in each page.
     *
     * @param rootDir the codebase root used for resolving relative paths
     * @param pages   the analyzed pages to enrich
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
