package com.lide.core.jsp;

import com.lide.core.extractors.NavigationTargetExtractor;
import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.NavigationTarget;
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

class DefaultNavigationTargetExtractorTest {

    @TempDir
    Path tempRoot;

    @Test
    void detectsAnchorsAndScriptNavigationTargets() throws Exception {
        Path jsp = copyFixture("customer/searchCustomer.jsp");

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jsp);

        JspAnalyzer analyzer = new DefaultJspAnalyzer();
        List<PageDescriptor> pages = analyzer.analyze(tempRoot, index);

        NavigationTargetExtractor extractor = new DefaultNavigationTargetExtractor();
        extractor.extract(tempRoot, pages);

        PageDescriptor page = pages.get(0);
        List<NavigationTarget> targets = page.getNavigationTargets();

        assertEquals(3, targets.size(), "Anchor, window.location, and JS string should be captured");
        assertTrue(targets.stream().anyMatch(t -> t.getTargetPage().contains("customerDetail.jsp")));
        assertTrue(targets.stream().anyMatch(t -> t.getTargetPage().contains("home.jsp")));
        assertTrue(targets.stream().anyMatch(t -> t.getTargetPage().contains("reports.jsp")));
        assertTrue(targets.stream().allMatch(t -> NavigationTarget.CONFIDENCE_HIGH.equals(t.getConfidence())));
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
