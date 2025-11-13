package com.lide.core.fs;

import com.lide.core.CodebaseScanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default {@link CodebaseScanner} implementation backed by {@link Files#walkFileTree(Path, java.nio.file.FileVisitor)}.
 */
public class DefaultCodebaseScanner implements CodebaseScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCodebaseScanner.class);

    private final Path rootDir;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    public DefaultCodebaseScanner(Path rootDir, List<String> includePatterns, List<String> excludePatterns) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        this.includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
        this.excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
    }

    @Override
    public CodebaseIndex scan(Path outputDirectory) {
        validateRootDirectory();
        validateOutputDirectory(outputDirectory);

        CodebaseIndex index = new CodebaseIndex();

        List<PathMatcher> includeMatchers = compileMatchers(includePatterns);
        List<PathMatcher> excludeMatchers = compileMatchers(excludePatterns);

        try {
            Files.walkFileTree(rootDir, new ScanningFileVisitor(rootDir, index, includeMatchers, excludeMatchers));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to traverse codebase at " + rootDir, e);
        }

        LOGGER.info("Scanned {} files from {}", index.totalDiscoveredFiles(), rootDir);
        LOGGER.info("Discovered {} JSP, {} HTML, {} Java files", index.getJspFiles().size(),
                index.getHtmlFiles().size(), index.getJavaFiles().size());

        return index;
    }

    private void validateRootDirectory() {
        if (!Files.exists(rootDir)) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootDir);
        }
        if (!Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("Root path is not a directory: " + rootDir);
        }
    }

    private void validateOutputDirectory(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Output directory must be provided");
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare output directory: " + outputDirectory, e);
        }
    }

    private List<PathMatcher> compileMatchers(List<String> patterns) {
        if (patterns.isEmpty()) {
            return List.of();
        }
        return patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(pattern -> rootDir.getFileSystem().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static final class ScanningFileVisitor extends SimpleFileVisitor<Path> {

        private final Path rootDir;
        private final CodebaseIndex index;
        private final List<PathMatcher> includeMatchers;
        private final List<PathMatcher> excludeMatchers;

        private ScanningFileVisitor(Path rootDir,
                                    CodebaseIndex index,
                                    List<PathMatcher> includeMatchers,
                                    List<PathMatcher> excludeMatchers) {
            this.rootDir = rootDir;
            this.index = index;
            this.includeMatchers = includeMatchers;
            this.excludeMatchers = excludeMatchers;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (shouldExclude(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!shouldInclude(file)) {
                return FileVisitResult.CONTINUE;
            }

            String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".jsp") || lowerName.endsWith(".jspf")) {
                index.addJspFile(file);
            } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
                index.addHtmlFile(file);
            } else if (lowerName.endsWith(".java")) {
                index.addJavaFile(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            LOGGER.warn("Failed to access {}: {}", file, exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (exc != null) {
                LOGGER.warn("I/O error after visiting {}: {}", dir, exc.getMessage());
            }
            return FileVisitResult.CONTINUE;
        }

        private boolean shouldInclude(Path path) {
            Path relative = rootDir.relativize(path);
            if (matches(relative, excludeMatchers, false)) {
                return false;
            }
            if (includeMatchers.isEmpty()) {
                return true;
            }
            return matches(relative, includeMatchers, false);
        }

        private boolean shouldExclude(Path dir) {
            Path relative = rootDir.relativize(dir);
            return matches(relative, excludeMatchers, true);
        }

        private boolean matches(Path relative, List<PathMatcher> matchers, boolean directory) {
            for (PathMatcher matcher : matchers) {
                if (matcher.matches(relative)) {
                    return true;
                }
                if (directory && matcher.matches(relative.resolve("placeholder"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
