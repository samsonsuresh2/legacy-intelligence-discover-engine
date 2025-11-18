package com.lide.core.extractors;

/**
 * Analyzes frame structures and relationships across legacy pages.
 */
import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

public interface FrameAnalyzer {

    /**
     * Populate frame metadata for the provided pages.
     *
     * @param rootDir project root used for resolving relative paths
     * @param pages   page descriptors to enrich with frame definitions
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
