package com.lide.core.jsp;

import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultSessionUsageExtractorTest {

    @Test
    void detectsSessionAttributesFromExpressionsAndApiCalls() {
        Path root = Path.of("src/test/resources/fixtures");
        PageDescriptor page = new PageDescriptor();
        page.setPageId("admin/audit.jsp");
        page.setSourcePath(root.resolve("admin/audit.jsp"));

        List<PageDescriptor> pages = new ArrayList<>();
        pages.add(page);

        DefaultSessionUsageExtractor extractor = new DefaultSessionUsageExtractor();
        extractor.extract(root, pages);

        assertNotNull(page.getSessionDependencies());
        assertEquals(2, page.getSessionDependencies().size());
    }
}
