package com.lide.core.cli;

import com.lide.core.CodebaseScanner;
import com.lide.core.config.AnalyzerConfig;
import com.lide.core.config.AnalyzerConfigLoader;
import com.lide.core.extractors.FrameAnalyzer;
import com.lide.core.extractors.CrossFrameInteractionExtractor;
import com.lide.core.extractors.HiddenFieldStateExtractor;
import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.extractors.SessionUsageExtractor;
import com.lide.core.extractors.UrlParameterExtractor;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.fs.DefaultCodebaseScanner;
import com.lide.core.java.DefaultJavaUsageAnalyzer;
import com.lide.core.java.JavaMetadataIndex;
import com.lide.core.java.JavaUsageAnalyzer;
import com.lide.core.jsp.DefaultFrameAnalyzer;
import com.lide.core.jsp.DefaultCrossFrameInteractionExtractor;
import com.lide.core.jsp.DefaultHiddenFieldStateExtractor;
import com.lide.core.jsp.DefaultJspAnalyzer;
import com.lide.core.jsp.DefaultNavigationTargetExtractor;
import com.lide.core.jsp.DefaultSessionUsageExtractor;
import com.lide.core.jsp.DefaultUrlParameterExtractor;
import com.lide.core.jsp.JspAnalyzer;
import com.lide.core.model.PageDescriptor;
import com.lide.core.report.DefaultJsonSchemaGenerator;
import com.lide.core.report.JsonSchemaGenerator;
import com.lide.core.report.DefaultMigrationReportGenerator;
import com.lide.core.report.MigrationReportGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command-line entry point for the Legacy Intelligence Discovery Engine.
 */
public final class LideCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(LideCli.class);

    private LideCli() {
        // Utility class
    }

    public static void main(String[] args) {
        try {
            CliOptions options = parseArgs(args);
            AnalyzerConfig config = loadConfiguration(options);

            CodebaseScanner scanner = new DefaultCodebaseScanner(config.getRootDir(),
                    config.getIncludePatterns(), config.getExcludePatterns());
            JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
            FrameAnalyzer frameAnalyzer = new DefaultFrameAnalyzer();
            CrossFrameInteractionExtractor crossFrameInteractionExtractor = new DefaultCrossFrameInteractionExtractor();
            NavigationTargetExtractor navigationTargetExtractor = new DefaultNavigationTargetExtractor();
            HiddenFieldStateExtractor hiddenFieldStateExtractor = new DefaultHiddenFieldStateExtractor();
            SessionUsageExtractor sessionUsageExtractor = new DefaultSessionUsageExtractor();
            UrlParameterExtractor urlParameterExtractor = new DefaultUrlParameterExtractor();
            JavaUsageAnalyzer javaUsageAnalyzer = new DefaultJavaUsageAnalyzer();
            JsonSchemaGenerator jsonSchemaGenerator = new DefaultJsonSchemaGenerator(config);
            MigrationReportGenerator migrationReportGenerator = new DefaultMigrationReportGenerator();

            LOGGER.info("Starting scan from {} with output {}", config.getRootDir(), config.getOutputDir());
            LOGGER.info("Include patterns: {}", config.getIncludePatterns());
            LOGGER.info("Exclude patterns: {}", config.getExcludePatterns());

            CodebaseIndex index = scanner.scan(config.getOutputDir());
            LOGGER.info("Scan complete. Total relevant files: {}", index.totalDiscoveredFiles());
            LOGGER.info("JSP/JSPF: {}, HTML/HTM: {}, Java: {}",
                    index.getJspFiles().size(), index.getHtmlFiles().size(), index.getJavaFiles().size());

            List<PageDescriptor> pages = jspAnalyzer.analyze(config.getRootDir(), index);
            LOGGER.info("JSP analysis generated {} page descriptors", pages.size());

            frameAnalyzer.extract(config.getRootDir(), pages);
            LOGGER.info("Frame extraction complete for {} pages", pages.size());

            navigationTargetExtractor.extract(config.getRootDir(), pages);
            LOGGER.info("Navigation extraction complete for {} pages", pages.size());

            crossFrameInteractionExtractor.extract(config.getRootDir(), pages);
            LOGGER.info("Cross-frame interaction extraction complete for {} pages", pages.size());

            hiddenFieldStateExtractor.extract(config.getRootDir(), pages);
            LOGGER.info("Hidden field extraction complete for {} pages", pages.size());

            sessionUsageExtractor.extract(config.getRootDir(), pages);
            LOGGER.info("Session usage extraction complete for {} pages", pages.size());

            urlParameterExtractor.extract(config.getRootDir(), pages);
            LOGGER.info("URL parameter extraction complete for {} pages", pages.size());

            JavaMetadataIndex javaMetadata = javaUsageAnalyzer.analyze(index);
            LOGGER.info("Java metadata classes: {}", javaMetadata.getFieldsByClass().size());
            LOGGER.info("Struts forms: {}, Struts actions: {}, Spring controllers: {}",
                    javaMetadata.getStrutsFormClasses().size(),
                    javaMetadata.getStrutsActionClasses().size(),
                    javaMetadata.getControllerClasses().size());

            jsonSchemaGenerator.generate(config.getRootDir(), config.getOutputDir(), pages, javaMetadata);
            LOGGER.info("JSON generation complete: artifacts available under {}", config.getOutputDir());

            migrationReportGenerator.generate(config.getRootDir(), config.getOutputDir(), pages, javaMetadata);
            LOGGER.info("Migration report complete: dashboard available under {}", config.getOutputDir());
        } catch (Exception ex) {
            LOGGER.error("Scan failed: {}", ex.getMessage(), ex);
            System.exit(1);
        }
    }

    private static AnalyzerConfig loadConfiguration(CliOptions options) {
        AnalyzerConfig config = AnalyzerConfigLoader.load(options.configPath());
        config = config.applyCliOverrides(options.rootDir(), options.outputDir(),
                options.includePatterns(), options.excludePatterns());
        config.normalize();
        return config;
    }

    static CliOptions parseArgs(String[] args) {
        Path rootDir = null;
        Path outputDir = null;
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        Path configPath = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String[] split = splitArg(arg, args, i);
                if (split != null) {
                    String name = split[0];
                    String value = split[1];
                    if ("rootDir".equals(name)) {
                        rootDir = Paths.get(value);
                    } else if ("outputDir".equals(name)) {
                        outputDir = Paths.get(value);
                    } else if ("include".equals(name)) {
                        include.addAll(parsePatterns(value));
                    } else if ("exclude".equals(name)) {
                        exclude.addAll(parsePatterns(value));
                    } else if ("config".equals(name)) {
                        configPath = Paths.get(value);
                    } else {
                        throw new IllegalArgumentException("Unknown option --" + name);
                    }

                    if (!arg.contains("=")) {
                        i++; // consume value that was read from the next argument slot
                    }
                } else {
                    String name = arg.substring(2);
                    if ("help".equals(name) || "h".equals(name)) {
                        printUsage();
                        System.exit(0);
                    }
                    throw new IllegalArgumentException("Option " + arg + " requires a value");
                }
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
        }

        Path normalizedRoot = rootDir == null ? null : rootDir.normalize();
        Path normalizedOutput = outputDir == null ? null : outputDir.normalize();
        return new CliOptions(normalizedRoot, normalizedOutput, List.copyOf(include), List.copyOf(exclude), configPath);
    }

    private static List<String> parsePatterns(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
    }

    private static String[] splitArg(String arg, String[] args, int index) {
        if (arg.contains("=")) {
            String[] pieces = arg.substring(2).split("=", 2);
            if (pieces.length != 2 || pieces[1].isBlank()) {
                throw new IllegalArgumentException("Option " + arg + " is missing a value");
            }
            return new String[]{pieces[0], pieces[1]};
        }
        if (index + 1 < args.length) {
            return new String[]{arg.substring(2), args[index + 1]};
        }
        return null;
    }

    private static void printUsage() {
        String usage = "Usage: java -jar lide-analyzer-core.jar [--config=<file>] "
                + "[--rootDir=<path>] [--outputDir=<path>] "
                + "[--include=glob1,glob2] [--exclude=glob3,glob4]";
        LOGGER.info(usage);
    }

    record CliOptions(Path rootDir,
                      Path outputDir,
                      List<String> includePatterns,
                      List<String> excludePatterns,
                      Path configPath) {
        CliOptions {
            includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
            excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
            this.rootDir = rootDir;
            this.outputDir = outputDir;
            this.includePatterns = includePatterns;
            this.excludePatterns = excludePatterns;
            this.configPath = configPath == null ? null : configPath.normalize();
        }
    }
}
