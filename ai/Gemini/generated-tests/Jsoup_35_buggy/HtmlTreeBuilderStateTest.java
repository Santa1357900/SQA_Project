package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.helper.DescendableLinkedList;

public class HtmlTreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.Initial.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!--comment-->", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("comment");
        boolean result = HtmlTreeBuilderState.Initial.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        doctypeToken.name.append("html");
        boolean result = HtmlTreeBuilderState.Initial.process(doctypeToken, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.BeforeHtml, tb.state());
    }

    @Test
    public void testInitialStateOtherToken() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.Initial.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.BeforeHtml, tb.state());
    }

    @Test
    public void testBeforeHtmlDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(doctypeToken, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("c");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.BeforeHead, tb.state());
    }

    @Test
    public void testBeforeHtmlEndTagIgnored() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</head>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("head");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</div >", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("div");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.BeforeHtml.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.BeforeHead, tb.state());
    }

    @Test
    public void testBeforeHeadWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("c");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.BeforeHead.process(doctypeToken, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadStartHead() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InHead, tb.state());
    }

    @Test
    public void testBeforeHeadEndTag() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</head>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("head");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.BeforeHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.InHead.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("c");
        boolean result = HtmlTreeBuilderState.InHead.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InHead.process(doctypeToken, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadStartTagHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagBase() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<base href='http://example.com'>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("base");
        startTag.attributes.put("href", "http://example.com");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagMeta() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<meta>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("meta");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagTitle() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<title>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("title");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagStyle() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<style>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("style");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagNoscript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<noscript>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("noscript");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InHeadNoscript, tb.state());
    }

    @Test
    public void testInHeadStartTagScript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<script>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("script");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.Text, tb.state());
    }

    @Test
    public void testInHeadStartTagHeadError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("head");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadEndTagHead() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head></head>", "http://example.com", new ParseErrorList(0));
        Element head = tb.insert(new Token.StartTag("head"));
        tb.setHeadElement(head);
        tb.transition(HtmlTreeBuilderState.InHead);
        Token.EndTag endTag = new Token.EndTag("head");
        boolean result = HtmlTreeBuilderState.InHead.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.AfterHead, tb.state());
    }

    @Test
    public void testInHeadEndTagBody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</body>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("body");
        boolean result = HtmlTreeBuilderState.InHead.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadEndTagError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("div");
        boolean result = HtmlTreeBuilderState.InHead.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.InHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadNoscriptDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(doctypeToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadNoscriptStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadNoscriptEndNoscript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<noscript></noscript>", "http://example.com", new ParseErrorList(0));
        tb.transition(HtmlTreeBuilderState.InHeadNoscript);
        Token.EndTag endTag = new Token.EndTag("noscript");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InHead, tb.state());
    }

    @Test
    public void testInHeadNoscriptWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadNoscriptEndBr() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<br>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("br");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadNoscriptError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("head");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(startTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadNoscriptAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.InHeadNoscript.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0));
        Token.Character spaceToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.AfterHead.process(spaceToken, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("c");
        boolean result = HtmlTreeBuilderState.AfterHead.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.AfterHead.process(doctypeToken, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartBody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<body>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("body");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());
    }

    @Test
    public void testAfterHeadStartFrameset() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("frameset");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InFrameset, tb.state());
    }

    @Test
    public void testAfterHeadStartMeta() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<meta>", "http://example.com", new ParseErrorList(0));
        Element head = tb.insert(new Token.StartTag("head"));
        tb.setHeadElement(head);
        Token.StartTag startTag = new Token.StartTag("meta");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartHeadError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("head");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterHeadStartOther() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.AfterHead.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadEndBody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</body>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("body");
        boolean result = HtmlTreeBuilderState.AfterHead.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadEndError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0));
        Token.EndTag endTag = new Token.EndTag("div");
        boolean result = HtmlTreeBuilderState.AfterHead.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterHeadAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("text", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("text");
        boolean result = HtmlTreeBuilderState.AfterHead.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testTextProcessCharacter() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("text", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("text");
        boolean result = HtmlTreeBuilderState.Text.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testTextProcessEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "http://example.com", new ParseErrorList(0));
        tb.transition(HtmlTreeBuilderState.Text);
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.Text.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testTextProcessEndTag() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("</script>", "http://example.com", new ParseErrorList(0));
        tb.transition(HtmlTreeBuilderState.Text);
        Token.EndTag endTag = new Token.EndTag("script");
        boolean result = HtmlTreeBuilderState.Text.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><!-- c --></table>", "http://example.com", new ParseErrorList(0));
        Token.Comment commentToken = new Token.Comment();
        commentToken.setData("c");
        boolean result = HtmlTreeBuilderState.InTable.process(commentToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><!DOCTYPE html></table>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctypeToken = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InTable.process(doctypeToken, tb);
        assertFalse(result);
    }

    @Test
    public void testInTableStartCaption() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><caption></caption></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("caption");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InCaption, tb.state());
    }

    @Test
    public void testInTableStartColgroup() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><colgroup></colgroup></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("colgroup");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InColumnGroup, tb.state());
    }

    @Test
    public void testInTableStartCol() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><col></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("col");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartTbody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tbody></tbody></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("tbody");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InTableBody, tb.state());
    }

    @Test
    public void testInTableStartTd() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><td></td></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("td");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartTable() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><table></table></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("table");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartStyleScript() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><style></style></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("style");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartInput() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><input type='hidden'></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("input");
        startTag.attributes.put("type", "hidden");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartForm() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><form></form></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.StartTag startTag = new Token.StartTag("form");
        boolean result = HtmlTreeBuilderState.InTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableEndTable() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.EndTag endTag = new Token.EndTag("table");
        boolean result = HtmlTreeBuilderState.InTable.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableEndOtherError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table></caption></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        Token.EndTag endTag = new Token.EndTag("caption");
        boolean result = HtmlTreeBuilderState.InTable.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInTableEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("html"));
        tb.insert(new Token.StartTag("table"));
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.InTable.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableTextCharacter() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("text", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("text");
        boolean result = HtmlTreeBuilderState.InTableText.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableTextOther() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table></div>", "http://example.com", new ParseErrorList(0));
        tb.transition(HtmlTreeBuilderState.InTableText);
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.InTableText.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInCaptionEndCaption() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><caption></caption></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("caption"));
        Token.EndTag endTag = new Token.EndTag("caption");
        boolean result = HtmlTreeBuilderState.InCaption.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
    }

    @Test
    public void testInCaptionEndError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><caption></body></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("caption"));
        Token.EndTag endTag = new Token.EndTag("body");
        boolean result = HtmlTreeBuilderState.InCaption.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInColumnGroupWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup>   </colgroup>", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup><!-- c --></colgroup>", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup><!DOCTYPE html></colgroup>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(doctype, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupStartTagHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup><html></html></colgroup>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("html");
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupStartTagCol() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup><col></colgroup>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("col");
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupEndColgroup() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup></colgroup>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("colgroup"));
        Token.EndTag endTag = new Token.EndTag("colgroup");
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
    }

    @Test
    public void testInColumnGroupEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<colgroup>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("html"));
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.InColumnGroup.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableBodyStartTr() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tbody><tr></tr></tbody></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        Token.StartTag startTag = new Token.StartTag("tr");
        boolean result = HtmlTreeBuilderState.InTableBody.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InRow, tb.state());
    }

    @Test
    public void testInTableBodyStartTd() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tbody><td></td></tbody></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        Token.StartTag startTag = new Token.StartTag("td");
        boolean result = HtmlTreeBuilderState.InTableBody.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableBodyEndTbody() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tbody></tbody></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        Token.EndTag endTag = new Token.EndTag("tbody");
        boolean result = HtmlTreeBuilderState.InTableBody.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
    }

    @Test
    public void testInTableBodyEndError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tbody></div></tbody></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        Token.EndTag endTag = new Token.EndTag("div");
        boolean result = HtmlTreeBuilderState.InTableBody.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInRowStartTd() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tr><td></td></tr></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        tb.insert(new Token.StartTag("tr"));
        Token.StartTag startTag = new Token.StartTag("td");
        boolean result = HtmlTreeBuilderState.InRow.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InCell, tb.state());
    }

    @Test
    public void testInRowEndTr() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tr></tr></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        tb.insert(new Token.StartTag("tr"));
        Token.EndTag endTag = new Token.EndTag("tr");
        boolean result = HtmlTreeBuilderState.InRow.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InTableBody, tb.state());
    }

    @Test
    public void testInRowEndError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tr></body></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        tb.insert(new Token.StartTag("tr"));
        Token.EndTag endTag = new Token.EndTag("body");
        boolean result = HtmlTreeBuilderState.InRow.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInCellEndTd() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tr><td></td></tr></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        tb.insert(new Token.StartTag("tr"));
        tb.insert(new Token.StartTag("td"));
        Token.EndTag endTag = new Token.EndTag("td");
        boolean result = HtmlTreeBuilderState.InCell.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InRow, tb.state());
    }

    @Test
    public void testInCellEndError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><tr><td></body></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("tbody"));
        tb.insert(new Token.StartTag("tr"));
        tb.insert(new Token.StartTag("td"));
        Token.EndTag endTag = new Token.EndTag("body");
        boolean result = HtmlTreeBuilderState.InCell.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testInSelectComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<select><!-- c --></select>", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.InSelect.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<select><!DOCTYPE html></select>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InSelect.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testInSelectStartOption() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<select><option></select>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("select"));
        Token.StartTag startTag = new Token.StartTag("option");
        boolean result = HtmlTreeBuilderState.InSelect.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectStartOptgroup() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<select><optgroup></select>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("select"));
        Token.StartTag startTag = new Token.StartTag("optgroup");
        boolean result = HtmlTreeBuilderState.InSelect.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectEndSelect() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<select></select>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("select"));
        Token.EndTag endTag = new Token.EndTag("select");
        boolean result = HtmlTreeBuilderState.InSelect.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectInTableStart() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><select><caption></caption></select></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("select"));
        Token.StartTag startTag = new Token.StartTag("caption");
        boolean result = HtmlTreeBuilderState.InSelectInTable.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectInTableEnd() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<table><select></caption></select></table>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("table"));
        tb.insert(new Token.StartTag("select"));
        Token.EndTag endTag = new Token.EndTag("caption");
        boolean result = HtmlTreeBuilderState.InSelectInTable.process(endTag, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterBodyWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>   ", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.AfterBody.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html><!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterBody.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html><!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.AfterBody.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterBodyEndHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("html"));
        Token.EndTag endTag = new Token.EndTag("html");
        boolean result = HtmlTreeBuilderState.AfterBody.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.AfterAfterBody, tb.state());
    }

    @Test
    public void testAfterBodyEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", new ParseErrorList(0));
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.AfterBody.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyAnythingElse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<html><body></body></html><div>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("div");
        boolean result = HtmlTreeBuilderState.AfterBody.process(startTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());
    }

    @Test
    public void testInFramesetWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset>   </frameset>", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.InFrameset.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset><!-- c --></frameset>", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.InFrameset.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset><!DOCTYPE html></frameset>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.InFrameset.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testInFramesetStartFrame() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset><frame></frameset>", "http://example.com", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag("frame");
        boolean result = HtmlTreeBuilderState.InFrameset.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetEndFrameset() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset></frameset>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("html"));
        tb.insert(new Token.StartTag("frameset"));
        Token.EndTag endTag = new Token.EndTag("frameset");
        boolean result = HtmlTreeBuilderState.InFrameset.process(endTag, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetWhitespace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset></frameset>   ", "http://example.com", new ParseErrorList(0));
        Token.Character charToken = new Token.Character("   ");
        boolean result = HtmlTreeBuilderState.AfterFrameset.process(charToken, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset></frameset><!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterFrameset.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset></frameset><!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.AfterFrameset.process(doctype, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterFramesetEndHtml() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<frameset></frameset>", "http://example.com", new ParseErrorList(0));
        tb.insert(new Token.StartTag("html"));
        Token.EndTag endTag = new Token.EndTag("html");
        boolean result = HtmlTreeBuilderState.AfterFrameset.process(endTag, tb);
        assertTrue(result);
        assertEquals(HtmlTreeBuilderState.AfterAfterFrameset, tb.state());
    }

    @Test
    public void testAfterAfterBodyComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterBodyDoctype() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(doctype, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterBodyEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "http://example.com", new ParseErrorList(0));
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.AfterAfterBody.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetComment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.AfterAfterFrameset.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetEOF() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("", "http://example.com", new ParseErrorList(0));
        Token.EOF eof = new Token.EOF();
        boolean result = HtmlTreeBuilderState.AfterAfterFrameset.process(eof, tb);
        assertTrue(result);
    }

    @Test
    public void testForeignContentProcess() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse("<math></math>", "http://example.com", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        boolean result = HtmlTreeBuilderState.ForeignContent.process(comment, tb);
        assertTrue(result);
    }
}