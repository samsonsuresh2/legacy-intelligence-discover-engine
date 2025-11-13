package com.lide.core.report;

/**
 * Generates JSON schema representations for analyzed pages.
 */
public interface JsonSchemaGenerator {

    /**
     * Generates JSON representations for analyzed pages and writes them to the provided output directory.
     *
     * @param rootDir       root of the scanned codebase for relative path calculations
     * @param outputDir     directory where JSON artifacts should be written
     * @param pages         analyzed page descriptors containing forms/outputs metadata
     * @param javaMetadata  metadata extracted from Java sources to enrich field constraints
     */
    void generate(java.nio.file.Path rootDir,
                  java.nio.file.Path outputDir,
                  java.util.List<com.lide.core.model.PageDescriptor> pages,
                  com.lide.core.java.JavaMetadataIndex javaMetadata) throws java.io.IOException;
}
