package org.jsoup.safety;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.junit.Test;

import static org.junit.Assert.*;

public class CleanerTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullWhitelist() throws Throwable {
        new Cleaner(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCleanNullDocument() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);
        cleaner.clean(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsValidNullDocument() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);
        cleaner.isValid(null);
    }

    @Test
    public void testCleanSimpleValidDocument() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<p>Hello <b>World</b></p>");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        assertTrue(clean.body().html().contains("<p>Hello <b>World</b></p>"));
    }

    @Test
    public void testCleanUnsafeTagAndAttributes() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<script>alert('xss');</script><p class=\"unsafe\" onclick=\"evil()\">Safe Text</p>");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        String bodyHtml = clean.body().html();
        assertTrue(bodyHtml.contains("<p>Safe Text</p>"));
        assertFalse(bodyHtml.contains("script"));
        assertFalse(bodyHtml.contains("onclick"));
        assertFalse(bodyHtml.contains("unsafe"));
    }

    @Test
    public void testIsValidWithValidHtml() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<p><b>Simple text</b></p>");

        boolean valid = cleaner.isValid(dirty);
        assertTrue(valid);
    }

    @Test
    public void testIsValidWithInvalidHtml() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<script>alert('hacked');</script>");

        boolean valid = cleaner.isValid(dirty);
        assertFalse(valid);
    }

    @Test
    public void testCleanUnsafeTagWithSafeChildren() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<div><b>Nested inside div</b></div>");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        String bodyHtml = clean.body().html();
        assertFalse(bodyHtml.contains("<div>"));
        assertTrue(bodyHtml.contains("<b>Nested inside div</b>"));
    }

    @Test
    public void testCleanTextAndComments() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("Just a text node <!-- comment -->");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        assertTrue(clean.body().html().contains("Just a text node"));
        assertFalse(clean.body().html().contains("comment"));
    }

    @Test
    public void testEnforcedAttributes() throws Throwable {
        Whitelist whitelist = Whitelist.basicWithImages();
        Cleaner cleaner = new Cleaner(whitelist);

        Document dirty = Document.createShell("http://example.com");
        dirty.body().append("<a href=\"http://example.com\">Link</a>");

        Document clean = cleaner.clean(dirty);
        assertNotNull(clean);
        String bodyHtml = clean.body().html();
        assertTrue(bodyHtml.contains("rel=\"nofollow\""));
    }
}