package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.nodes.Document;

public class HtmlTreeBuilderStateTest {

    @Test
    public void testEnumValuesAndProcess() throws Throwable {
        HtmlTreeBuilderState state = HtmlTreeBuilderState.Initial;
        assertNotNull(state);

        HtmlTreeBuilderState.valueOf("Initial");
        HtmlTreeBuilderState.valueOf("BeforeHtml");
        HtmlTreeBuilderState.valueOf("BeforeHead");
        HtmlTreeBuilderState.valueOf("InHead");
        HtmlTreeBuilderState.valueOf("InHeadNoscript");
        HtmlTreeBuilderState.valueOf("AfterHead");
        HtmlTreeBuilderState.valueOf("InBody");
        HtmlTreeBuilderState.valueOf("Text");
        HtmlTreeBuilderState.valueOf("InTable");
        HtmlTreeBuilderState.valueOf("InTableText");
        HtmlTreeBuilderState.valueOf("InCaption");
        HtmlTreeBuilderState.valueOf("InColumnGroup");
        HtmlTreeBuilderState.valueOf("InTableBody");
        HtmlTreeBuilderState.valueOf("InRow");
        HtmlTreeBuilderState.valueOf("InCell");
        HtmlTreeBuilderState.valueOf("InSelect");
        HtmlTreeBuilderState.valueOf("InSelectInTable");
        HtmlTreeBuilderState.valueOf("AfterBody");
        HtmlTreeBuilderState.valueOf("InFrameset");
        HtmlTreeBuilderState.valueOf("AfterFrameset");
        HtmlTreeBuilderState.valueOf("AfterAfterBody");
        HtmlTreeBuilderState.valueOf("AfterAfterFrameset");
        HtmlTreeBuilderState.valueOf("ForeignContent");

        HtmlTreeBuilderState[] states = HtmlTreeBuilderState.values();
        assertTrue(states.length > 0);

        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><head></head><body></body></html>", "http://example.com", parser);

        Token.Character whitespaceChar = new Token.Character();
        whitespaceChar.data("   ");

        boolean result = HtmlTreeBuilderState.Initial.process(whitespaceChar, tb);
        assertTrue(result);

        Token.Comment comment = new Token.Comment();
        comment.data("test comment");
        boolean resultComment = HtmlTreeBuilderState.Initial.process(comment, tb);
        assertTrue(resultComment);

        Token.Doctype doctype = new Token.Doctype();
        doctype.name("html");
        boolean resultDoctype = HtmlTreeBuilderState.Initial.process(doctype, tb);
        assertTrue(resultDoctype);
    }

    @Test
    public void testBeforeHtmlState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html></html>", "http://example.com", parser);

        Token.Doctype doctype = new Token.Doctype();
        boolean res1 = HtmlTreeBuilderState.BeforeHtml.process(doctype, tb);
        assertFalse(res1);

        Token.Comment comment = new Token.Comment();
        boolean res2 = HtmlTreeBuilderState.BeforeHtml.process(comment, tb);
        assertTrue(res2);

        Token.Character whitespace = new Token.Character();
        whitespace.data("\n");
        boolean res3 = HtmlTreeBuilderState.BeforeHtml.process(whitespace, tb);
        assertTrue(res3);

        Token.StartTag htmlStart = new Token.StartTag();
        htmlStart.name("html");
        boolean res4 = HtmlTreeBuilderState.BeforeHtml.process(htmlStart, tb);
        assertTrue(res4);

        Token.EndTag headEnd = new Token.EndTag();
        headEnd.name("head");
        boolean res5 = HtmlTreeBuilderState.BeforeHtml.process(headEnd, tb);
        assertTrue(res5);

        Token.EndTag randomEnd = new Token.EndTag();
        randomEnd.name("span");
        boolean res6 = HtmlTreeBuilderState.BeforeHtml.process(randomEnd, tb);
        assertFalse(res6);
    }

    @Test
    public void testBeforeHeadState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", parser);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(HtmlTreeBuilderState.BeforeHead.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.BeforeHead.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(HtmlTreeBuilderState.BeforeHead.process(doctype, tb));

        Token.StartTag htmlStart = new Token.StartTag();
        htmlStart.name("html");
        assertTrue(HtmlTreeBuilderState.BeforeHead.process(htmlStart, tb));

        Token.StartTag headStart = new Token.StartTag();
        headStart.name("head");
        assertTrue(HtmlTreeBuilderState.BeforeHead.process(headStart, tb));

        Token.EndTag bodyEnd = new Token.EndTag();
        bodyEnd.name("body");
        assertTrue(HtmlTreeBuilderState.BeforeHead.process(bodyEnd, tb));

        Token.EndTag randomEnd = new Token.EndTag();
        randomEnd.name("div");
        assertFalse(HtmlTreeBuilderState.BeforeHead.process(randomEnd, tb));
    }

    @Test
    public void testInHeadState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><head></head></html>", "http://example.com", parser);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(HtmlTreeBuilderState.InHead.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.InHead.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(HtmlTreeBuilderState.InHead.process(doctype, tb));

        Token.StartTag baseTag = new Token.StartTag();
        baseTag.name("base");
        baseTag.attributes.put("href", "http://foo.bar");
        assertTrue(HtmlTreeBuilderState.InHead.process(baseTag, tb));

        Token.StartTag metaTag = new Token.StartTag();
        metaTag.name("meta");
        assertTrue(HtmlTreeBuilderState.InHead.process(metaTag, tb));

        Token.StartTag titleTag = new Token.StartTag();
        titleTag.name("title");
        assertTrue(HtmlTreeBuilderState.InHead.process(titleTag, tb));

        Token.StartTag styleTag = new Token.StartTag();
        styleTag.name("style");
        assertTrue(HtmlTreeBuilderState.InHead.process(styleTag, tb));

        Token.StartTag noscriptTag = new Token.StartTag();
        noscriptTag.name("noscript");
        assertTrue(HtmlTreeBuilderState.InHead.process(noscriptTag, tb));

        Token.StartTag scriptTag = new Token.StartTag();
        scriptTag.name("script");
        assertTrue(HtmlTreeBuilderState.InHead.process(scriptTag, tb));

        Token.StartTag headTag = new Token.StartTag();
        headTag.name("head");
        assertFalse(HtmlTreeBuilderState.InHead.process(headTag, tb));

        Token.EndTag headEndTag = new Token.EndTag();
        headEndTag.name("head");
        assertTrue(HtmlTreeBuilderState.InHead.process(headEndTag, tb));
    }

    @Test
    public void testInBodyState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body><p>Hello</p></body></html>", "http://example.com", parser);

        Token.Character c = new Token.Character();
        c.data("Test text");
        assertTrue(HtmlTreeBuilderState.InBody.process(c, tb));

        Token.Character nullC = new Token.Character();
        nullC.data("\u0000");
        assertFalse(HtmlTreeBuilderState.InBody.process(nullC, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.InBody.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(HtmlTreeBuilderState.InBody.process(doctype, tb));

        Token.StartTag htmlTag = new Token.StartTag();
        htmlTag.name("html");
        assertTrue(HtmlTreeBuilderState.InBody.process(htmlTag, tb));

        Token.StartTag divTag = new Token.StartTag();
        divTag.name("div");
        assertTrue(HtmlTreeBuilderState.InBody.process(divTag, tb));

        Token.EndTag divEndTag = new Token.EndTag();
        divEndTag.name("div");
        assertTrue(HtmlTreeBuilderState.InBody.process(divEndTag, tb));

        Token.StartTag pTag = new Token.StartTag();
        pTag.name("p");
        assertTrue(HtmlTreeBuilderState.InBody.process(pTag, tb));

        Token.EndTag pEndTag = new Token.EndTag();
        pEndTag.name("p");
        assertTrue(HtmlTreeBuilderState.InBody.process(pEndTag, tb));

        Token.StartTag aTag = new Token.StartTag();
        aTag.name("a");
        assertTrue(HtmlTreeBuilderState.InBody.process(aTag, tb));

        Token.EndTag aEndTag = new Token.EndTag();
        aEndTag.name("a");
        assertTrue(HtmlTreeBuilderState.InBody.process(aEndTag, tb));
    }

    @Test
    public void testTextState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body><script>code</script></body></html>", "http://example.com", parser);

        Token.Character c = new Token.Character();
        c.data("var x = 1;");
        assertTrue(HtmlTreeBuilderState.Text.process(c, tb));

        Token.EndTag endTag = new Token.EndTag();
        endTag.name("script");
        assertTrue(HtmlTreeBuilderState.Text.process(endTag, tb));

        Token.EOF eof = new Token.EOF();
        assertTrue(HtmlTreeBuilderState.Text.process(eof, tb));
    }

    @Test
    public void testInTableState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body><table></table></body></html>", "http://example.com", parser);

        Token.Character c = new Token.Character();
        c.data("table text");
        assertTrue(HtmlTreeBuilderState.InTable.process(c, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.InTable.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(HtmlTreeBuilderState.InTable.process(doctype, tb));

        Token.StartTag caption = new Token.StartTag();
        caption.name("caption");
        assertTrue(HtmlTreeBuilderState.InTable.process(caption, tb));

        Token.EndTag tableEnd = new Token.EndTag();
        tableEnd.name("table");
        assertTrue(HtmlTreeBuilderState.InTable.process(tableEnd, tb));
    }

    @Test
    public void testAfterBodyState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", parser);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(HtmlTreeBuilderState.AfterBody.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.AfterBody.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(HtmlTreeBuilderState.AfterBody.process(doctype, tb));

        Token.EOF eof = new Token.EOF();
        assertTrue(HtmlTreeBuilderState.AfterBody.process(eof, tb));
    }

    @Test
    public void testForeignContentState() throws Throwable {
        Parser parser = Parser.htmlParser();
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", parser);

        Token.Comment comment = new Token.Comment();
        assertTrue(HtmlTreeBuilderState.ForeignContent.process(comment, tb));
    }
}