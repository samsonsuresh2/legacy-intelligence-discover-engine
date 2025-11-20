package com.lide.core;

import com.lide.core.fs.CodebaseIndex;

import java.nio.file.Path;

/**
 * Entry point for scanning a legacy codebase.
 */
public interface CodebaseScanner {

    /**
     * Executes a scan of the configured legacy codebase and produces a {@link CodebaseIndex}
     * describing discovered JSP, HTML, and Java assets.
     *
     * @param outputDirectory directory intended to store analysis outputs; verified but not used yet
     * @return {@link CodebaseIndex} containing categorized file paths
     */
    CodebaseIndex scan(Path outputDirectory);
}
