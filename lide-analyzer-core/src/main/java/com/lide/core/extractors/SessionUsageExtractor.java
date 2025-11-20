package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts session variable usage and dependencies.
 */
public interface SessionUsageExtractor {

    /**
     * Enriches page descriptors with detected session dependencies.
     *
     * @param rootDir base directory for resolving relative paths
     * @param pages   page descriptors to update
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
