package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class DocumentTest {

    @Test
    public void testCreateShell() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        assertNotNull(doc);
        assertEquals("http://example.com/", doc.baseUri());
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertEquals("html", doc.child(0).nodeName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateShellNullUri() throws Throwable {
        Document.createShell(null);
    }

    @Test
    public void testDocumentConstructorAndBasics() throws Throwable {
        Document doc = new Document("http://test.com");
        assertEquals("http://test.com", doc.baseUri());
        assertEquals("#document", doc.nodeName());
        assertEquals("", doc.outerHtml());
    }

    @Test
    public void testTitleGetAndSet() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        assertEquals("", doc.title());

        doc.title("Hello World");
        assertEquals("Hello World", doc.title());
        assertEquals("Hello World", doc.head().getElementsByTag("title").first().text());

        doc.title("  New Title  ");
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
        assertEquals("div", el.nodeName());
        assertEquals("http://example.com", el.baseUri());
    }

    @Test
    public void testHeadAndBodyAccessors() throws Throwable {
        Document doc = new Document("http://example.com");
        // Without shell, head/body might be null initially
        assertNull(doc.head());
        assertNull(doc.body());

        Document shell = Document.createShell("http://example.com");
        assertNotNull(shell.head());
        assertNotNull(shell.body());
    }

    @Test
    public void testNormalise() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.normalise();
        assertNotNull(doc.head());
        assertNotNull(doc.body());

        // Test normalising text nodes outside body
        Document doc2 = new Document("http://example.com");
        TextNode tn = new TextNode("orphan text", "");
        doc2.appendChild(tn);
        doc2.normalise();
        assertTrue(doc2.body().text().contains("orphan text"));
    }

    @Test
    public void testTextMethod() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        doc.text("Document body text");
        assertEquals("Document body text", doc.body().text());
        assertEquals("Document body text", doc.text());
    }

    @Test
    public void testOutputSettings() throws Throwable {
        Document doc = new Document("http://example.com");
        Document.OutputSettings settings = doc.outputSettings();
        assertNotNull(settings);

        assertEquals(Entities.EscapeMode.base, settings.escapeMode());
        settings.escapeMode(Entities.EscapeMode.extended);
        assertEquals(Entities.EscapeMode.extended, settings.escapeMode());

        assertEquals(Charset.forName("UTF-8"), settings.charset());
        settings.charset("ISO-8859-1");
        assertEquals(Charset.forName("ISO-8859-1"), settings.charset());
        
        settings.charset(Charset.forName("UTF-8"));
        assertEquals(Charset.forName("UTF-8"), settings.charset());
        assertNotNull(settings.encoder());

        assertTrue(settings.prettyPrint());
        settings.prettyPrint(false);
        assertFalse(settings.prettyPrint());

        assertEquals(1, settings.indentAmount());
        settings.indentAmount(4);
        assertEquals(4, settings.indentAmount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIndentAmount() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.outputSettings().indentAmount(-1);
    }
}