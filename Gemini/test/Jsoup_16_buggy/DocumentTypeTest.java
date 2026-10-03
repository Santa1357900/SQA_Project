package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentTypeTest {

    @Test
    public void testDocumentTypeCreationAndNodeName() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01 Transitional//EN", "http://www.w3.org/TR/html4/loose.dtd", "http://example.com");
        assertEquals("#doctype", doctype.nodeName());
    }

    @Test
    public void testOuterHtmlHeadWithAllAttributes() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01 Transitional//EN", "http://www.w3.org/TR/html4/loose.dtd", "http://example.com");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();
        
        doctype.outerHtmlHead(accum, 0, out);
        
        String expected = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" http://www.w3.org/TR/html4/loose.dtd\">";
        assertEquals(expected, accum.toString());
    }

    @Test
    public void testOuterHtmlHeadWithBlankPublicId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "http://www.w3.org/TR/html4/loose.dtd", "http://example.com");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();
        
        doctype.outerHtmlHead(accum, 0, out);
        
        String expected = "<!DOCTYPE html http://www.w3.org/TR/html4/loose.dtd\">";
        assertEquals(expected, accum.toString());
    }

    @Test
    public void testOuterHtmlHeadWithBlankSystemId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01 Transitional//EN", "", "http://example.com");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();
        
        doctype.outerHtmlHead(accum, 0, out);
        
        String expected = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">";
        assertEquals(expected, accum.toString());
    }

    @Test
    public void testOuterHtmlHeadWithBothBlankIds() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "http://example.com");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();
        
        doctype.outerHtmlHead(accum, 0, out);
        
        String expected = "<!DOCTYPE html>";
        assertEquals(expected, accum.toString());
    }

    @Test
    public void testOuterHtmlTailDoesNothing() throws Throwable {
        DocumentType doctype = new DocumentType("html", "public", "system", "");
        StringBuilder accum = new StringBuilder("initial");
        Document.OutputSettings out = new Document.OutputSettings();
        
        doctype.outerHtmlTail(accum, 0, out);
        
        assertEquals("initial", accum.toString());
    }
}