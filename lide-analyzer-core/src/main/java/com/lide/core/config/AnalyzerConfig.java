package com.lide.core.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration object controlling how the analyzer scans the filesystem and maps pages to Java assets.
 */
public class AnalyzerConfig {

    private Path rootDir;
    private Path outputDir;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private List<String> strutsActionPackages;
    private List<String> springControllerPackages;
    private NamingConventions namingConventions;

    public AnalyzerConfig() {
        // default constructor for Jackson
    }

    public static AnalyzerConfig defaultConfig() {
        AnalyzerConfig config = new AnalyzerConfig();
        config.setRootDir(Paths.get("."));
        config.setOutputDir(Paths.get("build", "jsp-json"));
        config.setIncludePatterns(List.of());
        config.setExcludePatterns(List.of("**/target/**", "**/.git/**", "**/node_modules/**"));
        config.setStrutsActionPackages(List.of());
        config.setSpringControllerPackages(List.of());
        config.setNamingConventions(NamingConventions.defaultConfig());
        return config;
    }

    public AnalyzerConfig merge(AnalyzerConfig override) {
        if (override == null) {
            return copy();
        }
        AnalyzerConfig merged = copy();
        if (override.getRootDir() != null) {
            merged.setRootDir(override.getRootDir());
        }
        if (override.getOutputDir() != null) {
            merged.setOutputDir(override.getOutputDir());
        }
        if (override.getIncludePatterns() != null) {
            merged.setIncludePatterns(override.getIncludePatterns());
        }
        if (override.getExcludePatterns() != null) {
            merged.setExcludePatterns(override.getExcludePatterns());
        }
        if (override.getStrutsActionPackages() != null) {
            merged.setStrutsActionPackages(override.getStrutsActionPackages());
        }
        if (override.getSpringControllerPackages() != null) {
            merged.setSpringControllerPackages(override.getSpringControllerPackages());
        }
        if (override.getNamingConventions() != null) {
            merged.setNamingConventions(merged.getNamingConventions().merge(override.getNamingConventions()));
        }
        return merged;
    }

    public AnalyzerConfig applyCliOverrides(Path rootDirOverride,
                                            Path outputDirOverride,
                                            List<String> includeOverride,
                                            List<String> excludeOverride) {
        AnalyzerConfig merged = copy();
        if (rootDirOverride != null) {
            merged.setRootDir(rootDirOverride);
        }
        if (outputDirOverride != null) {
            merged.setOutputDir(outputDirOverride);
        }
        if (includeOverride != null && !includeOverride.isEmpty()) {
            merged.setIncludePatterns(includeOverride);
        }
        if (excludeOverride != null && !excludeOverride.isEmpty()) {
            merged.setExcludePatterns(excludeOverride);
        }
        return merged;
    }

    public void normalize() {
        if (rootDir == null) {
            rootDir = Paths.get(".");
        }
        if (outputDir == null) {
            outputDir = Paths.get("build", "jsp-json");
        }
        includePatterns = immutableList(includePatterns);
        excludePatterns = immutableList(excludePatterns);
        strutsActionPackages = immutableList(strutsActionPackages);
        springControllerPackages = immutableList(springControllerPackages);
        if (namingConventions == null) {
            namingConventions = NamingConventions.defaultConfig();
        } else {
            namingConventions = namingConventions.normalize();
        }
    }

    private AnalyzerConfig copy() {
        AnalyzerConfig copy = new AnalyzerConfig();
        copy.setRootDir(rootDir);
        copy.setOutputDir(outputDir);
        copy.setIncludePatterns(includePatterns);
        copy.setExcludePatterns(excludePatterns);
        copy.setStrutsActionPackages(strutsActionPackages);
        copy.setSpringControllerPackages(springControllerPackages);
        copy.setNamingConventions(namingConventions);
        return copy;
    }

    private List<String> immutableList(List<String> source) {
        if (source == null) {
            return List.of();
        }
        List<String> mutable = new ArrayList<>();
        for (String value : source) {
            if (value != null && !value.isBlank()) {
                mutable.add(value.trim());
            }
        }
        return Collections.unmodifiableList(mutable);
    }

    public Path getRootDir() {
        return rootDir;
    }

    public void setRootDir(Path rootDir) {
        this.rootDir = rootDir == null ? null : rootDir.normalize();
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir == null ? null : outputDir.normalize();
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public List<String> getStrutsActionPackages() {
        return strutsActionPackages;
    }

    public void setStrutsActionPackages(List<String> strutsActionPackages) {
        this.strutsActionPackages = strutsActionPackages;
    }

    public List<String> getSpringControllerPackages() {
        return springControllerPackages;
    }

    public void setSpringControllerPackages(List<String> springControllerPackages) {
        this.springControllerPackages = springControllerPackages;
    }

    public NamingConventions getNamingConventions() {
        if (namingConventions == null) {
            namingConventions = NamingConventions.defaultConfig();
        }
        return namingConventions;
    }

    public void setNamingConventions(NamingConventions namingConventions) {
        this.namingConventions = namingConventions;
    }

    public static final class NamingConventions {
        private List<String> jspToControllerPatterns;
        private List<String> formBeanSuffixes;

        public NamingConventions() {
            // default constructor
        }

        public static NamingConventions defaultConfig() {
            NamingConventions conventions = new NamingConventions();
            conventions.setJspToControllerPatterns(List.of("%sAction", "%sController", "%sDispatchAction"));
            conventions.setFormBeanSuffixes(List.of("Form", "Command", "Request", "Model", "Dto"));
            return conventions.normalize();
        }

        public NamingConventions merge(NamingConventions override) {
            if (override == null) {
                return normalize();
            }
            NamingConventions merged = new NamingConventions();
            merged.setJspToControllerPatterns(
                    override.getJspToControllerPatterns() == null || override.getJspToControllerPatterns().isEmpty()
                            ? this.getJspToControllerPatterns()
                            : override.getJspToControllerPatterns());
            merged.setFormBeanSuffixes(
                    override.getFormBeanSuffixes() == null || override.getFormBeanSuffixes().isEmpty()
                            ? this.getFormBeanSuffixes()
                            : override.getFormBeanSuffixes());
            return merged.normalize();
        }

        public NamingConventions normalize() {
            jspToControllerPatterns = sanitize(jspToControllerPatterns);
            formBeanSuffixes = sanitize(formBeanSuffixes);
            if (jspToControllerPatterns.isEmpty()) {
                jspToControllerPatterns = List.of("%sAction");
            }
            if (formBeanSuffixes.isEmpty()) {
                formBeanSuffixes = List.of("Form");
            }
            return this;
        }

        private List<String> sanitize(List<String> values) {
            if (values == null) {
                return List.of();
            }
            List<String> cleaned = new ArrayList<>();
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    cleaned.add(value.trim());
                }
            }
            return Collections.unmodifiableList(cleaned);
        }

        public List<String> getJspToControllerPatterns() {
            return jspToControllerPatterns;
        }

        public void setJspToControllerPatterns(List<String> jspToControllerPatterns) {
            this.jspToControllerPatterns = jspToControllerPatterns;
        }

        public List<String> getFormBeanSuffixes() {
            return formBeanSuffixes;
        }

        public void setFormBeanSuffixes(List<String> formBeanSuffixes) {
            this.formBeanSuffixes = formBeanSuffixes;
        }
    }
}
