# LIDE - Legacy Intelligence Discovery Engine

LIDE is a static analysis engine for legacy JSP-based enterprise applications.

## Overview

The analyzer scans JSP/HTML/Java sources, extracts form and navigation metadata, and emits structured JSON descriptors alongside a migration summary.

## Modules

- `lide-analyzer-core`: Java 17 Maven module that performs filesystem scanning, JSP/Java analysis, and JSON report generation.
- `lide-dashboard`: Static HTML placeholder for future reporting.
- `schema-browser`: React + Vite UI (P9) for browsing generated JSON schemas locally or from a hosted folder.
- `prompts/`, `docs/`: Planning collateral and design documentation.

## Getting Started

1. Run the analyzer (requires Java + Maven):
   ```bash
   mvn -pl lide-analyzer-core -am package
   java -jar lide-analyzer-core/target/lide-analyzer-core-*.jar --rootDir=/path/to/app --outputDir=./output
   ```
2. Explore the JSON output with the schema browser:
   ```bash
   cd schema-browser
   npm install
   npm run dev
   ```

## Contributing

Please open issues or pull requests for bugs, enhancements, or documentation updates.

