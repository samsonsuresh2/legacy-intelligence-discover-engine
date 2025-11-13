package com.lide.core.jsp;

import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.PageDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Analyzer for JSP artifacts within the legacy application.
 */
public interface JspAnalyzer {

    /**
     * Parse the JSP and HTML assets contained in the supplied {@link CodebaseIndex} to produce
     * structured {@link PageDescriptor} instances describing discovered forms, fields, and output
     * sections.
     *
     * @param rootDir root directory that was scanned; used for computing relative page identifiers
     * @param index   discovered codebase artifacts
     * @return ordered list of page descriptors mirroring the JSON output structure
     */
    List<PageDescriptor> analyze(Path rootDir, CodebaseIndex index);
}
