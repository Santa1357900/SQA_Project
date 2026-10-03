package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentTest {

    @Test
    public void testConstructorAndNodeName() throws Throwable {
        Document doc = new Document("http://example.com");
        assertEquals("#document", doc.nodeName());
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test
    public void testCreateShell() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertNotNull(doc.select("html").first());
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateShellNullBaseUri() throws Throwable {
        Document.createShell(null);
    }

    @Test
    public void testTitleGetAndSet() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        assertEquals("", doc.title());

        doc.title("  Hello World!  ");
        assertEquals("Hello World!", doc.title());

        // Test updating existing title
        doc.title("New Title");
        assertEquals("New Title", doc.title());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTitleNull() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.title(null);
    }

    @Test
    public void testCreateElement() throws Throwable {
        Document doc = new Document("http://example.com");
        Element el = doc.createElement("div");
        assertNotNull(el);
        assertEquals("div", el.tagName());
        assertEquals("http://example.com", el.baseUri());
    }

    @Test
    public void testNormaliseMissingStructure() throws Throwable {
        // Document without html, head, body
        Document doc = new Document("http://example.com");
        doc.normalise();

        assertNotNull(doc.select("html").first());
        assertNotNull(doc.head());
        assertNotNull(doc.body());
    }

    @Test
    public void testNormaliseTextNodes() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        // Add a non-blank text node to root or head which should be moved to body
        doc.head().appendChild(new TextNode("Move me", "http://example.com"));
        // Add a blank text node which should be ignored
        doc.head().appendChild(new TextNode("   ", "http://example.com"));

        doc.normalise();

        String bodyText = doc.body().text();
        assertTrue(bodyText.contains("Move me"));
    }

    @Test
    public void testOuterHtml() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        doc.body().text("Test Body");
        String html = doc.outerHtml();
        assertNotNull(html);
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("Test Body"));
    }

    @Test
    public void testTextMethod() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        doc.text("Override Body Text");
        assertEquals("Override Body Text", doc.body().text());
    }
}