package com.lide.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@link AnalyzerConfig} instances from YAML or JSON documents.
 */
public final class AnalyzerConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerConfigLoader.class);

    private AnalyzerConfigLoader() {
        // utility
    }

    public static AnalyzerConfig load(Path path) {
        AnalyzerConfig base = AnalyzerConfig.defaultConfig();
        if (path == null) {
            base.normalize();
            return base;
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Configuration file does not exist: " + path);
        }
        try {
            ObjectMapper mapper = createMapper(path);
            AnalyzerConfig fileConfig = mapper.readValue(path.toFile(), AnalyzerConfig.class);
            AnalyzerConfig merged = base.merge(fileConfig);
            merged.normalize();
            LOGGER.info("Loaded analyzer configuration from {}", path);
            return merged;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read configuration from " + path, ex);
        }
    }

    private static ObjectMapper createMapper(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
            return new ObjectMapper(new YAMLFactory());
        }
        return new ObjectMapper();
    }
}
