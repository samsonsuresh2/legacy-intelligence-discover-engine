package com.lide.core.fs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable index of files discovered within a legacy codebase scan.
 */
public final class CodebaseIndex {

    private final List<Path> jspFiles = new ArrayList<>();
    private final List<Path> htmlFiles = new ArrayList<>();
    private final List<Path> javaFiles = new ArrayList<>();

    public void addJspFile(Path path) {
        jspFiles.add(path);
    }

    public void addHtmlFile(Path path) {
        htmlFiles.add(path);
    }

    public void addJavaFile(Path path) {
        javaFiles.add(path);
    }

    public List<Path> getJspFiles() {
        return Collections.unmodifiableList(jspFiles);
    }

    public List<Path> getHtmlFiles() {
        return Collections.unmodifiableList(htmlFiles);
    }

    public List<Path> getJavaFiles() {
        return Collections.unmodifiableList(javaFiles);
    }

    public int totalDiscoveredFiles() {
        return jspFiles.size() + htmlFiles.size() + javaFiles.size();
    }
}
