package com.lide.core.jsp;

import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultJsRoutingExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsWindowLocationAndFormRouting() throws Exception {
        Path jsp = tempDir.resolve("navigation/jsRouting.jsp");
        Files.createDirectories(jsp.getParent());
        Files.copy(getClass().getClassLoader().getResourceAsStream("fixtures/navigation/jsRouting.jsp"), jsp);

        PageDescriptor page = new PageDescriptor();
        page.setPageId("navigation/jsRouting.jsp");
        page.setSourcePath(jsp);

        DefaultJsRoutingExtractor extractor = new DefaultJsRoutingExtractor();
        extractor.extract(tempDir, List.of(page));

        assertNotNull(page.getJsRoutingHints());
        assertEquals(2, page.getJsRoutingHints().size());
        assertEquals("home.jsp", page.getJsRoutingHints().get(0).getTargetPage());
    }
}
