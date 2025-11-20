package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts navigation targets from analyzed pages.
 */
public interface NavigationTargetExtractor {

    /**
     * Populate navigation targets for the provided pages.
     *
     * @param rootDir project root used for resolving relative paths
     * @param pages   page descriptors to enrich with navigation targets
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
