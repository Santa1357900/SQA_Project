package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommentClaudeTest {

    // covers Comment(String) constructor assigning value, used by getData()
    @Test
    public void testConstructorSingleArg_storesData_getDataReturnsSameValue() throws Throwable {
        Comment c = new Comment("hello world");
        assertEquals("hello world", c.getData());
    }

    // covers deprecated Comment(String,String) which delegates to Comment(String); baseUri must be ignored per javadoc
    @Test
    public void testConstructorDeprecatedTwoArg_baseUriIgnored_perJavadoc() throws Throwable {
        Comment c = new Comment("note", "http://example.com/base/");
        assertEquals("note", c.getData());
        assertFalse("http://example.com/base/".equals(c.baseUri()));
    }

    // covers constructor + getData with empty-string edge case
    @Test
    public void testConstructorSingleArg_emptyString_getDataReturnsEmpty() throws Throwable {
        Comment c = new Comment("");
        assertEquals("", c.getData());
    }

    // covers nodeName(), DOM-standard comment node name
    @Test
    public void testNodeName_alwaysReturnsHashComment() throws Throwable {
        Comment c = new Comment("x");
        assertEquals("#comment", c.nodeName());
    }

    // covers getData()/coreValue() with unicode characters, preserved exactly
    @Test
    public void testGetData_unicodeContentPreservedExactly() throws Throwable {
        Comment c = new Comment("caf\u00e9 \u4e2d\u6587");
        assertEquals("caf\u00e9 \u4e2d\u6587", c.getData());
    }

    // covers getData() with whitespace-only content, no trimming expected
    @Test
    public void testGetData_whitespaceOnlyPreserved() throws Throwable {
        Comment c = new Comment("   ");
        assertEquals("   ", c.getData());
    }

    // covers outerHtmlHead/outerHtmlTail via toString(): "<!--" + data + "-->"
    @Test
    public void testToString_basicComment_wrappedWithCommentMarkers() throws Throwable {
        Comment c = new Comment("hi");
        assertEquals("<!--hi-->", c.toString());
    }

    // covers outerHtmlHead with empty data branch
    @Test
    public void testToString_emptyData_producesEmptyComment() throws Throwable {
        Comment c = new Comment("");
        assertEquals("<!---->", c.toString());
    }

    // covers that comment content is appended verbatim, not HTML-escaped
    @Test
    public void testToString_angleBracketsNotEscaped() throws Throwable {
        Comment c = new Comment("<b>&amp;</b>");
        assertEquals("<!--<b>&amp;</b>-->", c.toString());
    }

    // covers verbatim append of "--" inside data (no sanitization logic in this class)
    @Test
    public void testToString_doubleHyphenContentNotEscaped() throws Throwable {
        Comment c = new Comment("a--b");
        assertEquals("<!--a--b-->", c.toString());
    }

    // covers data.length() > 1 failing for empty string
    @Test
    public void testIsXmlDeclaration_emptyString_false() throws Throwable {
        assertFalse(new Comment("").isXmlDeclaration());
    }

    // covers length==1 boundary (not > 1) even though "!" marker present
    @Test
    public void testIsXmlDeclaration_singleBang_falseBoundary() throws Throwable {
        assertFalse(new Comment("!").isXmlDeclaration());
    }

    // covers length==1 boundary for "?" marker
    @Test
    public void testIsXmlDeclaration_singleQuestion_falseBoundary() throws Throwable {
        assertFalse(new Comment("?").isXmlDeclaration());
    }

    // covers length==2, startsWith("!") true branch
    @Test
    public void testIsXmlDeclaration_twoCharsBang_true() throws Throwable {
        assertTrue(new Comment("!x").isXmlDeclaration());
    }

    // covers length==2, startsWith("?") true branch
    @Test
    public void testIsXmlDeclaration_twoCharsQuestion_true() throws Throwable {
        assertTrue(new Comment("?x").isXmlDeclaration());
    }

    // covers startsWith checking position: marker present but not at index 0
    @Test
    public void testIsXmlDeclaration_markerNotFirstChar_false() throws Throwable {
        assertFalse(new Comment("x!").isXmlDeclaration());
    }

    // covers both startsWith checks false for ordinary comment text
    @Test
    public void testIsXmlDeclaration_noMarker_false() throws Throwable {
        assertFalse(new Comment("just a comment").isXmlDeclaration());
    }

    // covers realistic longer bang-prefixed bogus-comment data
    @Test
    public void testIsXmlDeclaration_realisticDoctypeLikeData_true() throws Throwable {
        assertTrue(new Comment("!DOCTYPE html").isXmlDeclaration());
    }

    // covers "?" branch: substring strips symmetric leading/trailing markers, parses as element via xml parser
    @Test
    public void testAsXmlDeclaration_processingInstructionLikeData_returnsNonNull() throws Throwable {
        Comment c = new Comment("?xml version=\"1.0\"?");
        XmlDeclaration decl = c.asXmlDeclaration();
        assertNotNull(decl);
    }

    // covers "!" branch passed as second ctor arg to XmlDeclaration; symmetric markers stripped by substring
    @Test
    public void testAsXmlDeclaration_bangPrefixedData_returnsNonNull() throws Throwable {
        Comment c = new Comment("!tag!");
        XmlDeclaration decl = c.asXmlDeclaration();
        assertNotNull(decl);
    }

    // covers decl.attributes().addAll(el.attributes()) executing without error and returning usable attributes
    @Test
    public void testAsXmlDeclaration_resultHasNonNullAttributes() throws Throwable {
        Comment c = new Comment("?xml version=\"1.0\" encoding=\"UTF-8\"?");
        XmlDeclaration decl = c.asXmlDeclaration();
        assertNotNull(decl.attributes());
    }

    // covers outerHtml() (inherited) delegating through outerHtmlHead/Tail for a standalone comment
    @Test
    public void testOuterHtml_viaDirectConstruction_containsCommentData() throws Throwable {
        Comment c = new Comment("standalone");
        assertTrue(c.outerHtml().contains("standalone"));
    }

    // covers outerHtmlHead's prettyPrint indent condition: a comment that is NOT the first child
    // of a block element must not get extra indentation whitespace inserted between it and prior text
    @Test
    public void testPrettyPrint_commentNotFirstChildOfBlock_adjacentTextPreserved() throws Throwable {
        Document doc = Jsoup.parse("<div>Hello<!--c-->World</div>");
        String html = doc.toString();
        assertTrue(html.contains("Hello<!--c-->World"));
    }

    // covers outerHtmlHead when comment IS the first child of a block element (indent branch may apply)
    @Test
    public void testPrettyPrint_commentFirstChildOfBlock_commentPresentInOutput() throws Throwable {
        Document doc = Jsoup.parse("<div><!--c-->Hello</div>");
        String html = doc.toString();
        assertTrue(html.contains("<!--c-->"));
    }

    // covers Comment rendering through Jsoup.parseBodyFragment entry point
    @Test
    public void testParseBodyFragment_commentPreservedInOutput() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>Para</p><!--note-->");
        assertTrue(doc.toString().contains("<!--note-->"));
    }

    // covers toString()/outerHtml() determinism across repeated calls
    @Test
    public void testToString_calledTwice_consistentResult() throws Throwable {
        Comment c = new Comment("repeat");
        assertEquals(c.toString(), c.toString());
    }

    // covers getData()/coreValue() with a longer string, no truncation
    @Test
    public void testGetData_longData_preservedFully() throws Throwable {
        String longText = "this is a fairly long comment body used for testing data integrity";
        Comment c = new Comment(longText);
        assertEquals(longText, c.getData());
    }
}
