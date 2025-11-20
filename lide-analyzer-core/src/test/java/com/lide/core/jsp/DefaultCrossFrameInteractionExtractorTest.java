package com.lide.core.jsp;

import com.lide.core.extractors.CrossFrameInteractionExtractor;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.CrossFrameInteraction;
import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCrossFrameInteractionExtractorTest {

    @TempDir
    Path tempRoot;

    @Test
    void detectsCrossFrameNavigationPatterns() throws Exception {
        Path jsp = copyFixture("navigation/crossFrame.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jsp);

        JspAnalyzer analyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = analyzer.analyze(tempRoot, index);

        CrossFrameInteractionExtractor extractor = new DefaultCrossFrameInteractionExtractor();
        extractor.extract(tempRoot, pages);

        PageDescriptor page = pages.get(0);
        List<CrossFrameInteraction> interactions = page.getCrossFrameInteractions();

        assertEquals(3, interactions.size());
        assertTrue(interactions.stream().anyMatch(i -> "menu.jsp".equals(i.getToJsp())));
        assertTrue(interactions.stream().anyMatch(i -> "content.jsp".equals(i.getToJsp())));
        assertTrue(interactions.stream().anyMatch(i -> i.getToJsp().contains("detail.jsp")));
        assertTrue(interactions.stream().allMatch(i -> CrossFrameInteraction.CONFIDENCE_MEDIUM.equals(i.getConfidence())));
    }

    private Path copyFixture(String relativePath) throws Exception {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/" + relativePath)) {
            assertNotNull(in, "Missing fixture: " + relativePath);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
