package org.jsoup.nodes;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class DocumentTypeTest {

    @Test
    public void testConstructorAndNodeName() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "http://www.w3.org/TR/html4/strict.dtd", "http://example.com");
        assertEquals("#doctype", doctype.nodeName());
        assertEquals("html", doctype.attr("name"));
        assertEquals("-//W3C//DTD HTML 4.01//EN", doctype.attr("publicId"));
        assertEquals("http://www.w3.org/TR/html4/strict.dtd", doctype.attr("systemId"));
        assertEquals("http://example.com", doctype.baseUri());
    }

    @Test
    public void testOuterHtmlHeadHtml5Syntax() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!doctype html>", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadHtml5WithPublicId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadXmlSyntax() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!DOCTYPE html>", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadSystemIdOnly() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "http://www.w3.org/TR/html4/strict.dtd", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!DOCTYPE html \"http://www.w3.org/TR/html4/strict.dtd\">", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadBothPublicAndSystemId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "http://www.w3.org/TR/html4/strict.dtd", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadNoName() throws Throwable {
        DocumentType doctype = new DocumentType("", "", "", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!doctype>", accum.toString());
    }

    @Test
    public void testOuterHtmlTailDoesNothing() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "");
        Document doc = new Document("");
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlTail(accum, 0, doc.outputSettings());
        assertEquals("", accum.toString());
    }

    @Test
    public void testBlankAttributesHandling() throws Throwable {
        DocumentType doctype = new DocumentType("html", "   ", "\t", "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!doctype html>", accum.toString());
    }

    @Test
    public void testNullAttributesHandling() throws Throwable {
        DocumentType doctype = new DocumentType("html", null, null, "");
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        StringBuilder accum = new StringBuilder();
        doctype.outerHtmlHead(accum, 0, doc.outputSettings());
        assertEquals("<!doctype html>", accum.toString());
    }
}