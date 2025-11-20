package com.lide.core.jsp;

import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.FrameDefinition;
import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFrameAnalyzerTest {

    private Path tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("frames-fixture");
    }

    @Test
    void detectsFramesetsAndNestedFrames() throws Exception {
        Path frameset = copyFixture("layout/frameset.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(frameset);

        JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = jspAnalyzer.analyze(tempRoot, index);

        DefaultFrameAnalyzer frameAnalyzer = new DefaultFrameAnalyzer();
        frameAnalyzer.extract(tempRoot, pages);

        assertEquals(1, pages.size());
        PageDescriptor page = pages.get(0);
        assertTrue(Boolean.TRUE.equals(page.getFramesetPage()));
        assertEquals(3, page.getFrameDefinitions().size());

        FrameDefinition menu = page.getFrameDefinitions().stream()
                .filter(def -> "menu".equals(def.getFrameName()))
                .findFirst()
                .orElseThrow();
        assertEquals("/legacy/menu.jsp", menu.getSource());
        assertEquals(0, menu.getDepth());

        FrameDefinition bottom = page.getFrameDefinitions().stream()
                .filter(def -> "bottom".equals(def.getFrameName()))
                .findFirst()
                .orElseThrow();
        assertEquals("details.jsp", bottom.getSource());
        assertEquals(FrameDefinition.CONFIDENCE_HIGH, bottom.getConfidence());
        assertNotNull(bottom.getParentFrameName());
    }

    @Test
    void detectsIframesInContentPages() throws Exception {
        Path audit = copyFixture("admin/audit.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(audit);

        JspAnalyzer jspAnalyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = jspAnalyzer.analyze(tempRoot, index);

        DefaultFrameAnalyzer frameAnalyzer = new DefaultFrameAnalyzer();
        frameAnalyzer.extract(tempRoot, pages);

        PageDescriptor page = pages.get(0);
        assertFalse(Boolean.TRUE.equals(page.getFramesetPage()));
        assertEquals(1, page.getFrameDefinitions().size());
        FrameDefinition frame = page.getFrameDefinitions().get(0);
        assertEquals("/legacy/navFrame.jsp", frame.getSource());
        assertEquals("IFRAME", frame.getTag());
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
