package com.lide.core.jsp;

import com.lide.core.model.FrameDefinition;
import com.lide.core.model.JsRoutingHint;
import com.lide.core.model.NavigationTarget;
import com.lide.core.model.PageDependency;
import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultPageDependencyGraphBuilderTest {

    @Test
    void buildsDependenciesFromNavigationFramesAndJsRouting() {
        PageDescriptor page = new PageDescriptor();
        page.setPageId("menu.jsp");
        page.setSourcePath(Path.of("menu.jsp"));

        NavigationTarget navigationTarget = new NavigationTarget();
        navigationTarget.setTargetPage("orders.jsp");
        FrameDefinition frameDefinition = new FrameDefinition();
        frameDefinition.setSource("layout.jsp");
        JsRoutingHint routingHint = new JsRoutingHint();
        routingHint.setTargetPage("dashboard.jsp");

        page.setNavigationTargets(List.of(navigationTarget));
        page.setFrameDefinitions(List.of(frameDefinition));
        page.setJsRoutingHints(List.of(routingHint));

        DefaultPageDependencyGraphBuilder builder = new DefaultPageDependencyGraphBuilder();
        builder.build(Path.of("."), List.of(page));

        List<PageDependency> dependencies = page.getPageDependencies();
        assertNotNull(dependencies);
        assertEquals(3, dependencies.size());
    }
}
