package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlTreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Token.Character token = new Token.Character();
        token.data("   ");
        boolean result = HtmlTreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Comment token = new Token.Comment();
        token.data("test comment");
        boolean result = HtmlTreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Doctype token = new Token.Doctype();
        token.name("html");
        token.publicIdentifier("pubId");
        token.systemIdentifier("sysId");
        boolean result = HtmlTreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateOther() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Token.Character token = new Token.Character();
        token.data("\n ");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Comment token = new Token.Comment();
        token.data("comment");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Doctype token = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("html");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagValid() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagInvalid() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Token.Character token = new Token.Character();
        token.data(" ");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Comment token = new Token.Comment();
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Doctype token = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("html");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadStartHead() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagValid() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagInvalid() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Character token = new Token.Character();
        token.data(" ");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Comment token = new Token.Comment();
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Doctype token = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadStartTagHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("html");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagBase() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("base");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagMeta() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("meta");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagTitle() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("title");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagStyle() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("style");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagNoscript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("noscript");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagScript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("script");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagHead() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("head");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadStartTagAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.StartTag token = new Token.StartTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadEndTagHead() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("head");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadEndTagBody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("body");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadEndTagInvalid() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("div");
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadDefaultToken() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EOF token = new Token.EOF();
        boolean result = HtmlTreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateCharacter() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.Character token = new Token.Character();
        token.data("some text");
        boolean result = HtmlTreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EOF token = new Token.EOF();
        boolean result = HtmlTreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEndTag() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "", null);
        Token.EndTag token = new Token.EndTag();
        token.name("script");
        boolean result = HtmlTreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testForeignContentState() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Token.Comment token = new Token.Comment();
        boolean result = HtmlTreeBuilderState.ForeignContent.process(token, tb);
        assertTrue(result);
    }
}