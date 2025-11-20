package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts cross-frame interactions such as parent/child frame navigation calls.
 */
public interface CrossFrameInteractionExtractor {

    /**
     * Populate cross-frame interaction candidates for the provided pages.
     *
     * @param rootDir project root used for resolving relative paths
     * @param pages   page descriptors to enrich
     */
    void extract(Path rootDir, List<PageDescriptor> pages);
}
