package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;

public class HtmlTreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("   ", "");
        assertNotNull(doc);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<!-- comment -->", "");
        assertNotNull(doc);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<!DOCTYPE html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testBeforeHtmlDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<!DOCTYPE html><html></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testInHeadBasicTags() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head><title>Hello</title><base href=\"http://example.com\"><meta charset=\"UTF-8\"></head><body></body></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testInBodyFormattingAndElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head></head><body><p>Hello <b>world</b>! <span>Span text</span><ul><li>Item 1</li></ul></p></body></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testTableParsing() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head></head><body><table><caption>Caption</caption>colgroup><col></colgroup><thead><tr><th>Header</th></tr></thead><tbody><tr><td>Cell</td></tr></tbody></table></body></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testSelectParsing() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head></head><body><select><optgroup label=\"Group\"><option value=\"1\">One</option></optgroup></select></body></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testFramesetParsing() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head></head><frameset><frame src=\"about:blank\"></frameset></html>", "");
        assertNotNull(doc);
    }

    @Test
    public void testForeignContentAndConstants() throws Throwable {
        boolean result = HtmlTreeBuilderState.ForeignContent.process(new Token.Comment(), new HtmlTreeBuilder());
        assertTrue(result);
    }
}