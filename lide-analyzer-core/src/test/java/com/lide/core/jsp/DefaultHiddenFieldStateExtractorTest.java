package com.lide.core.jsp;

import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultHiddenFieldStateExtractorTest {

    @Test
    void extractsHiddenFieldsFromJsp() {
        Path root = Path.of("src/test/resources/fixtures");
        PageDescriptor page = new PageDescriptor();
        page.setPageId("customer/searchCustomer.jsp");
        page.setSourcePath(root.resolve("customer/searchCustomer.jsp"));

        List<PageDescriptor> pages = new ArrayList<>();
        pages.add(page);

        DefaultHiddenFieldStateExtractor extractor = new DefaultHiddenFieldStateExtractor();
        extractor.extract(root, pages);

        assertNotNull(page.getHiddenFields());
        assertEquals(1, page.getHiddenFields().size());
        assertEquals("mode", page.getHiddenFields().get(0).getName());
        assertEquals("${sessionScope.mode}", page.getHiddenFields().get(0).getExpression());
        assertTrue(page.getHiddenFields().get(0).getSnippet().contains("input"));
    }
}
