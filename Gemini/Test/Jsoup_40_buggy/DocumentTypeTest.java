package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentTypeTest {

    @Test
    public void testConstructorAndNodeName() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD XHTML 1.0 Transitional//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd", "http://example.com");
        
        assertEquals("#doctype", doctype.nodeName());
        assertEquals("html", doctype.attr("name"));
        assertEquals("-//W3C//DTD XHTML 1.0 Transitional//EN", doctype.attr("publicId"));
        assertEquals("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd", doctype.attr("systemId"));
        assertEquals("http://example.com", doctype.baseUri());
    }

    @Test
    public void testEmptyNameThrowsException() throws Throwable {
        try {
            new DocumentType("", "pubId", "sysId", "baseUri");
            fail("Expected exception for empty name");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testNullNameThrowsException() throws Throwable {
        try {
            new DocumentType(null, "pubId", "sysId", "baseUri");
            fail("Expected exception for null name");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testOuterHtmlHeadAllAttributes() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "http://www.w3.org/TR/html4/strict.dtd", "");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();

        doctype.outerHtmlHead(accum, 0, out);
        
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadOnlyName() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();

        doctype.outerHtmlHead(accum, 0, out);
        
        assertEquals("<!DOCTYPE html>", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadNameAndSystemId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "http://www.w3.org/TR/html4/strict.dtd", "");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();

        doctype.outerHtmlHead(accum, 0, out);
        
        assertEquals("<!DOCTYPE html \"http://www.w3.org/TR/html4/strict.dtd\">", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadNameAndPublicId() throws Throwable {
        DocumentType doctype = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "", "");
        StringBuilder accum = new StringBuilder();
        Document.OutputSettings out = new Document.OutputSettings();

        doctype.outerHtmlHead(accum, 0, out);
        
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">", accum.toString());
    }

    @Test
    public void testOuterHtmlTailDoesNothing() throws Throwable {
        DocumentType doctype = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder("initial");
        Document.OutputSettings out = new Document.OutputSettings();

        doctype.outerHtmlTail(accum, 0, out);
        
        assertEquals("initial", accum.toString());
    }
}