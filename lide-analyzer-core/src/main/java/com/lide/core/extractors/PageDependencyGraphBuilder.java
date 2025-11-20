package com.lide.core.extractors;

import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds dependency graphs between legacy pages and backend components.
 */
public interface PageDependencyGraphBuilder {

    /**
     * Builds and assigns dependency relationships for the provided pages.
     *
     * @param rootDir codebase root used to resolve relative paths
     * @param pages   analyzed page descriptors to enrich
     */
    void build(Path rootDir, List<PageDescriptor> pages);
}
