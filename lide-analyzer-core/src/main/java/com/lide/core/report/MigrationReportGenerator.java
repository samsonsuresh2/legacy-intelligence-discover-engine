package com.lide.core.report;

import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.model.PageDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates migration dashboards and supporting assets.
 */
public interface MigrationReportGenerator {

    /**
     * Produce migration dashboards and reports from analyzed descriptors.
     *
     * @param rootDir      project root used for relativizing page identifiers
     * @param outputDir    directory where reports should be written
     * @param pages        analyzed page descriptors
     * @param javaMetadata extracted Java metadata used for additional hints
     * @throws IOException when report generation fails
     */
    void generate(Path rootDir,
                  Path outputDir,
                  List<PageDescriptor> pages,
                  JavaMetadataIndex javaMetadata) throws IOException;
}
