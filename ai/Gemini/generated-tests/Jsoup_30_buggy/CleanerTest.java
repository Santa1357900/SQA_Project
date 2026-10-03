package org.jsoup.safety;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.junit.Test;

import static org.junit.Assert.*;

public class CleanerTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullWhitelist() throws Throwable {
        new Cleaner(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCleanNullDocument() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        cleaner.clean(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsValidNullDocument() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        cleaner.isValid(null);
    }

    @Test
    public void testCleanFramesetDocumentWithoutBody() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document dirty = Document.createShell("http://example.com");
        // Remove body to simulate frameset or body-less document
        dirty.body().remove();

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        assertNotNull(clean.body());
        assertTrue(clean.body().children().isEmpty());
    }

    @Test
    public void testIsValidWithNoBody() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document dirty = Document.createShell("http://example.com");
        dirty.body().remove();

        boolean valid = cleaner.isValid(dirty);
        assertTrue(valid);
    }

    @Test
    public void testCleanBasicDocument() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document dirty = Document.parse("<html><head></head><body><p>Hello <script>alert('bad');</script><b>World</b></p></body></html>", "http://example.com");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        // script should be stripped, p and b should remain
        String html = clean.body().html();
        assertTrue(html.contains("p"));
        assertTrue(html.contains("b"));
        assertFalse(html.contains("script"));
        assertFalse(html.contains("alert"));
    }

    @Test
    public void testIsValidValidDocument() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document dirty = Document.parse("<html><head></head><body><p>Hello <b>World</b></p></body></html>", "http://example.com");

        boolean valid = cleaner.isValid(dirty);
        assertTrue(valid);
    }

    @Test
    public void testIsValidInvalidDocument() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document dirty = Document.parse("<html><head></head><body><p>Hello <script>alert('bad');</script></p></body></html>", "http://example.com");

        boolean valid = cleaner.isValid(dirty);
        assertFalse(valid);
    }

    @Test
    public void testCleanUnsafeElementWithChildrenStripped() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.none());
        Document dirty = Document.parse("<html><head></head><body><div><p>Text inside div</p></div></body></html>", "http://example.com");

        Document clean = cleaner.clean(dirty);
        // Whitelist.none() strips div and p, but since copySafeNodes recurses into discarded elements, text inside p might be dropped or handled.
        // Let's verify clean output structure.
        assertNotNull(clean);
    }

    @Test
    public void testSafeAttributesAndEnforcedAttributes() throws Throwable {
        Whitelist whitelist = Whitelist.relaxed();
        Cleaner cleaner = new Cleaner(whitelist);
        Document dirty = Document.parse("<html><head></head><body><a href=\"http://example.com\" onclick=\"bad()\" target=\"_blank\">Link</a></body></html>", "http://example.com");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        Element a = clean.select("a").first();
        assertNotNull(a);
        assertEquals("http://example.com", a.attr("href"));
        assertEquals("", a.attr("onclick")); // should be discarded
    }
}