package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlTreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", parser);
        Token.Character c = new Token.Character();
        c.data("   ");
        boolean result = HtmlTreeBuilderState.Initial.process(c, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!--comment-->", "http://example.com", parser);
        Token.Comment comment = new Token.Comment();
        comment.data("comment");
        boolean result = HtmlTreeBuilderState.Initial.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", parser);
        Token.Doctype doctype = new Token.Doctype();
        doctype.name("html");
        boolean result = HtmlTreeBuilderState.Initial.process(doctype, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateOther() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div></div>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("div");
        boolean result = HtmlTreeBuilderState.Initial.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlDoctype() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", parser);
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlComment() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", parser);
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlWhitespace() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", parser);
        Token.Character c = new Token.Character();
        c.data("   ");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(c, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlStartTagHtml() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("html");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagAnythingElse() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", parser);
        Token.EndTag end = new Token.EndTag();
        end.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(end, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagError() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div/>", "http://example.com", parser);
        Token.EndTag end = new Token.EndTag();
        end.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(end, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadWhitespace() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", parser);
        Token.Character c = new Token.Character();
        c.data("   ");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(c, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadComment() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", parser);
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.BeforeHead.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadDoctype() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", parser);
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHead.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadStartTagHtml() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("html");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadStartTagHead() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTag() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", parser);
        Token.EndTag end = new Token.EndTag();
        end.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(end, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagError() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div/>", "http://example.com", parser);
        Token.EndTag end = new Token.EndTag();
        end.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(end, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadAnythingElse() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateCharacter() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("text", "http://example.com", parser);
        Token.Character c = new Token.Character();
        c.data("text");
        boolean result = HtmlTreeBuilderState.Text.process(c, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEOF() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "http://example.com", parser);
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.Text.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEndTag() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</script>", "http://example.com", parser);
        Token.EndTag end = new Token.EndTag();
        end.name("script");
        boolean result = HtmlTreeBuilderState.Text.process(end, tb);
        assertTrue(result);
    }

    @Test
    public void testForeignContent() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<math></math>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("math");
        boolean result = HtmlTreeBuilderState.ForeignContent.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetComment() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", parser);
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterAfterFrameset.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetNoframes() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<noframes>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("noframes");
        boolean result = HtmlTreeBuilderState.AfterAfterFrameset.process(start, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetError() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("div");
        boolean result = HtmlTreeBuilderState.AfterAfterFrameset.process(start, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterAfterBodyComment() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", parser);
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterBodyEOF() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "http://example.com", parser);
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterBodyError() throws Throwable {
        Parser parser = new Parser(new HtmlTreeBuilder());
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", parser);
        Token.StartTag start = new Token.StartTag();
        start.name("div");
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(start, tb);
        assertTrue(result);
    }
}