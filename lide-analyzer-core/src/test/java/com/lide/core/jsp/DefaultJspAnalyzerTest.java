package com.lide.core.jsp;

import com.lide.core.fs.CodebaseIndex;
import com.lide.core.model.OutputFieldDescriptor;
import com.lide.core.model.OutputSectionDescriptor;
import com.lide.core.model.PageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultJspAnalyzerTest {

    private final JspAnalyzer analyzer = new DefaultJspAnalyzer();

    @TempDir
    Path tempDir;

    @Test
    void capturesTableOutputsFromIteratingTables() throws Exception {
        String jsp = """
                <html>
                  <body>
                    <table id='loanTable'>
                      <thead>
                        <tr><th>Loan ID</th><th>Amount</th></tr>
                      </thead>
                      <tbody>
                        <c:forEach items='${loanList}' var='loan'>
                          <tr>
                            <td>${loan.id}</td>
                            <td>${loan.amount}</td>
                          </tr>
                        </c:forEach>
                      </tbody>
                    </table>
                  </body>
                </html>
                """;

        Path jspPath = tempDir.resolve("loan.jsp");
        Files.writeString(jspPath, jsp, StandardCharsets.UTF_8);

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jspPath);

        List<PageDescriptor> descriptors = analyzer.analyze(tempDir, index);
        assertEquals(1, descriptors.size());

        PageDescriptor descriptor = descriptors.get(0);
        assertNotNull(descriptor.getOutputs());
        assertEquals(1, descriptor.getOutputs().size());

        OutputSectionDescriptor section = descriptor.getOutputs().get(0);
        assertEquals("TABLE", section.getType());
        assertEquals("loan", section.getItemVariable());
        assertEquals("loanList", section.getItemsExpression());

        List<OutputFieldDescriptor> fields = section.getFields();
        assertNotNull(fields);
        assertEquals(2, fields.size());
        assertEquals("loan.id", fields.get(0).getBindingExpression());
        assertEquals("Loan ID", fields.get(0).getLabel());
        assertEquals("id", fields.get(0).getName());
        assertEquals("loan.amount", fields.get(1).getBindingExpression());
        assertEquals("Amount", fields.get(1).getLabel());
        assertEquals("amount", fields.get(1).getName());
    }

    @Test
    void capturesInlineTextOutputs() throws Exception {
        String jsp = """
                <html>
                  <body>
                    <div id='customerName'>Customer: ${customer.name}</div>
                  </body>
                </html>
                """;

        Path jspPath = tempDir.resolve("customer.jsp");
        Files.writeString(jspPath, jsp, StandardCharsets.UTF_8);

        CodebaseIndex index = new CodebaseIndex();
        index.addJspFile(jspPath);

        List<PageDescriptor> descriptors = analyzer.analyze(tempDir, index);
        assertEquals(1, descriptors.size());

        PageDescriptor descriptor = descriptors.get(0);
        assertNotNull(descriptor.getOutputs());
        assertEquals(1, descriptor.getOutputs().size());

        OutputSectionDescriptor section = descriptor.getOutputs().get(0);
        assertEquals("TEXT_BLOCK", section.getType());
        assertEquals("customerName", section.getSectionId());

        List<OutputFieldDescriptor> fields = section.getFields();
        assertNotNull(fields);
        assertEquals(1, fields.size());
        OutputFieldDescriptor field = fields.get(0);
        assertEquals("customer.name", field.getBindingExpression());
        assertEquals("name", field.getName());
        assertEquals("Customer:", field.getLabel());
        assertNull(field.getNotes());
    }
}
