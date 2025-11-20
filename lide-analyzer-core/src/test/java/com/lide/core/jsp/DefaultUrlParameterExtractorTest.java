package com.lide.core.jsp;

import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.extractors.UrlParameterExtractor;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.PageDescriptor;
import com.lide.core.jsp.DefaultNavigationTargetExtractor;
import com.lide.core.jsp.DefaultUrlParameterExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUrlParameterExtractorTest {

    private Path tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("lide-url-params");
    }

    @Test
    void detectsParametersFromAnchorsAndScripts() throws Exception {
        Path jsp = copyFixture("navigation/urlParams.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jsp);

        JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = jspAnalyzer.analyze(tempRoot, index);

        NavigationTargetExtractor navigationTargetExtractor = new DefaultNavigationTargetExtractor();
        navigationTargetExtractor.extract(tempRoot, pages);

        UrlParameterExtractor extractor = new DefaultUrlParameterExtractor();
        extractor.extract(tempRoot, pages);

        PageDescriptor page = pages.get(0);
        assertEquals(3, page.getNavigationTargets().size(), "Navigation targets should still be captured separately");
        assertEquals(6, page.getUrlParameterCandidates().size());

        Set<String> names = page.getUrlParameterCandidates().stream()
                .map(param -> param.getName().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(names.contains("mode"));
        assertTrue(names.contains("customerid"));
        assertTrue(names.contains("id"));
        assertTrue(names.contains("token"));
        assertTrue(names.contains("refresh"));
        assertTrue(names.contains("simple"));
    }

    private Path copyFixture(String relativePath) throws Exception {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/" + relativePath)) {
            assertTrue(in != null, "Missing fixture: " + relativePath);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
