package com.lide.core.java;

import com.lide.core.fs.CodebaseIndex;

/**
 * Analyzer for Java controller and backend usage within JSP flows.
 */
public interface JavaUsageAnalyzer {

    /**
     * Analyze the Java sources collected within the {@link CodebaseIndex} and produce
     * metadata describing forms, controllers, and validation constraints.
     *
     * @param index codebase index containing Java source locations
     * @return aggregated metadata for downstream enrichment
     */
    JavaMetadataIndex analyze(CodebaseIndex index);
}
