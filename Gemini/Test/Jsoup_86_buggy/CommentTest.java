package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class CommentTest {

    @Test
    public void testCommentConstructorAndGetData() throws Throwable {
        Comment comment = new Comment("test comment data");
        assertEquals("test comment data", comment.getData());
        assertEquals("#comment", comment.nodeName());
    }

    @Test
    public void testCommentDeprecatedConstructor() throws Throwable {
        Comment comment = new Comment("data with baseUri", "http://example.com");
        assertEquals("data with baseUri", comment.getData());
        assertEquals("#comment", comment.nodeName());
    }

    @Test
    public void testOuterHtmlHeadPrettyPrint() throws Throwable {
        Comment comment = new Comment("my comment");
        Document.OutputSettings out = new Document.OutputSettings();
        out.prettyPrint(true);
        
        StringBuilder accum = new StringBuilder();
        comment.outerHtmlHead(accum, 0, out);
        assertEquals("<!--my comment-->", accum.toString());
    }

    @Test
    public void testOuterHtmlHeadNotPrettyPrint() throws Throwable {
        Comment comment = new Comment("my comment");
        Document.OutputSettings out = new Document.OutputSettings();
        out.prettyPrint(false);
        
        StringBuilder accum = new StringBuilder();
        comment.outerHtmlHead(accum, 2, out);
        assertEquals("<!--my comment-->", accum.toString());
    }

    @Test
    public void testOuterHtmlTail() throws Throwable {
        Comment comment = new Comment("my comment");
        Document.OutputSettings out = new Document.OutputSettings();
        StringBuilder accum = new StringBuilder();
        
        // outerHtmlTail is empty, just ensuring it doesn't throw and executes safely
        comment.outerHtmlTail(accum, 0, out);
        assertEquals("", accum.toString());
    }

    @Test
    public void testToString() throws Throwable {
        Comment comment = new Comment("hello");
        assertEquals("<!--hello-->", comment.toString());
    }

    @Test
    public void testIsXmlDeclaration() throws Throwable {
        Comment c1 = new Comment("!xml version=\"1.0\"");
        assertTrue(c1.isXmlDeclaration());

        Comment c2 = new Comment("?xml version=\"1.0\"?");
        assertTrue(c2.isXmlDeclaration());

        Comment c3 = new Comment("normal comment");
        assertFalse(c3.isXmlDeclaration());

        Comment c4 = new Comment("!");
        assertFalse(c4.isXmlDeclaration()); // length <= 1

        Comment c5 = new Comment("");
        assertFalse(c5.isXmlDeclaration());
    }

    @Test
    public void testAsXmlDeclarationValid() throws Throwable {
        Comment comment = new Comment("!xml version=\"1.0\" encoding=\"UTF-8\"");
        XmlDeclaration decl = comment.asXmlDeclaration();
        assertNotNull(decl);
        assertEquals("xml", decl.tagName());
        assertEquals("1.0", decl.attr("version"));
        assertEquals("UTF-8", decl.attr("encoding"));
    }

    @Test
    public void testAsXmlDeclarationInvalid() throws Throwable {
        Comment comment = new Comment("just a regular comment");
        // Depending on parser behavior, let's see what happens. If it doesn't parse as an element, childNodeSize might be 0 or null.
        // Let's verify it safely handles non-declaration comments or returns null/valid object without crashing.
        XmlDeclaration decl = comment.asXmlDeclaration();
        // Just verify execution finishes without exception.
    }

    @Test
    public void testNullDataHandling() throws Throwable {
        Comment comment = new Comment(null);
        assertNull(comment.getData());
        assertFalse(comment.isXmlDeclaration());
    }
}