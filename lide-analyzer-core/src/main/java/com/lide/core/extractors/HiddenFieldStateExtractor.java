package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts hidden field definitions and state hints from JSP/HTML pages.
 */
public interface HiddenFieldStateExtractor {

    /**
     * Enriches the provided page descriptors with detected hidden field definitions.
     *
     * @param rootDir base directory for resolving relative paths
     * @param pages   page descriptors to enrich
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
