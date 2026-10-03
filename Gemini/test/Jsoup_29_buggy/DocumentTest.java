package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;

public class DocumentTest {

    @Test
    public void testDocumentConstructorAndBasics() throws Throwable {
        Document doc = new Document("http://example.com");
        assertEquals("http://example.com", doc.baseUri());
        assertEquals("#document", doc.nodeName());
        assertNotNull(doc.outputSettings());
        assertNotNull(doc.quirksMode());
        
        doc.quirksMode(Document.QuirksMode.quirks);
        assertEquals(Document.QuirksMode.quirks, doc.quirksMode());
    }

    @Test
    public void testCreateShell() throws Throwable {
        Document doc = Document.createShell("http://example.com");
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertEquals("html", doc.child(0).nodeName());
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateShellNullBaseUri() throws Throwable {
        Document.createShell(null);
    }

    @Test
    public void testTitleHandling() throws Throwable {
        Document doc = new Document("http://example.com");
        assertEquals("", doc.title());

        doc.title("  My Title  ");
        assertEquals("My Title", doc.title());

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
        assertEquals("div", el.nodeName());
        assertEquals("http://example.com", el.baseUri());
    }

    @Test
    public void testNormalise() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.appendChild(new TextNode("Root text", ""));
        
        doc.normalise();
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertTrue(doc.body().text().contains("Root text"));
    }

    @Test
    public void testNormaliseDuplicateStructures() throws Throwable {
        Document doc = new Document("http://example.com");
        Element html = doc.appendElement("html");
        html.appendElement("head");
        html.appendElement("body");
        
        // Add duplicate head and body
        html.appendElement("head").text("Extra Head");
        html.appendElement("body").text("Extra Body");

        doc.normalise();
        assertEquals(1, doc.getElementsByTag("head").size());
        assertEquals(1, doc.getElementsByTag("body").size());
    }

    @Test
    public void testOuterHtmlAndText() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.text("Hello World");
        assertEquals("Hello World", doc.body().text());
        assertEquals("Hello World", doc.outerHtml());
    }

    @Test
    public void testClone() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.title("Test Title");
        Document clone = doc.clone();
        
        assertNotNull(clone);
        assertEquals(doc.title(), clone.title());
        assertEquals(doc.baseUri(), clone.baseUri());
        assertNotSame(doc.outputSettings(), clone.outputSettings());
    }

    @Test
    public void testOutputSettings() throws Throwable {
        Document doc = new Document("http://example.com");
        Document.OutputSettings settings = new Document.OutputSettings();
        
        settings.escapeMode(Entities.EscapeMode.extended);
        assertEquals(Entities.EscapeMode.extended, settings.escapeMode());

        settings.charset(Charset.forName("UTF-8"));
        assertEquals(Charset.forName("UTF-8"), settings.charset());

        settings.charset("ISO-8859-1");
        assertEquals(Charset.forName("ISO-8859-1"), settings.charset());
        assertNotNull(settings.encoder());

        settings.prettyPrint(false);
        assertFalse(settings.prettyPrint());

        settings.indentAmount(4);
        assertEquals(4, settings.indentAmount());

        Document.OutputSettings clonedSettings = settings.clone();
        assertNotNull(clonedSettings);

        doc.outputSettings(settings);
        assertEquals(settings, doc.outputSettings());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutputSettingsInvalidIndent() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.indentAmount(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutputSettingsNull() throws Throwable {
        Document doc = new Document("http://example.com");
        doc.outputSettings(null);
    }
}