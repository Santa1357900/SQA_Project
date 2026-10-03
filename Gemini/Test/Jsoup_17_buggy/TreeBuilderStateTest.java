package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- comment -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        token.data("comment");
        boolean result = TreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        token.name("html");
        boolean result = TreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateOtherToken() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("html");
        boolean result = TreeBuilderState.Initial.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- comment -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlStartHtml() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("html");
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagValid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</head>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("head");
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlEndTagInvalid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("div");
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHtmlAnythingElse() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("div");
        boolean result = TreeBuilderState.BeforeHtml.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadStartHtml() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("html");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadStartHead() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("head");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagValid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</head>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("head");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHeadEndTagInvalid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("div");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testBeforeHeadAnythingElse() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("div");
        boolean result = TreeBuilderState.BeforeHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadStartTagBase() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<base href='http://example.com'>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("base");
        token.attributes.put("href", "http://example.com");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagMeta() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<meta>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("meta");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagTitle() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<title>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("title");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagStyle() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<style>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("style");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagNoscript() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<noscript>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("noscript");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagScript() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<script>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("script");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadStartTagHead() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("head");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInHeadEndTagHead() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<head></head>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("head"));
        Token.EndTag token = new Token.EndTag("head");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInHeadEndTagInvalid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("div");
        boolean result = TreeBuilderState.InHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterHeadWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartBody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<body>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("body");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartFrameset() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("frameset");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadStartHead() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<head>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("head");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterHeadEndTagBody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</body>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("body");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterHeadEndTagInvalid() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</div>", "http://example.com", new ParseErrorList(0, 0));
        Token.EndTag token = new Token.EndTag("div");
        boolean result = TreeBuilderState.AfterHead.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInBodyNullCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("\u0000", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character(String.valueOf((char) 0x0000));
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInBodyWhitespaceCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyNormalCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("abc", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.Character token = new Token.Character("abc");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInBodyStartHtml() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "http://example.com", new ParseErrorList(0, 0));
        Token.StartTag token = new Token.StartTag("html");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartBody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<body>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("body");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartBlock() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<div>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("div");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartHeading() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<h1>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("h1");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartForm() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<form>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("form");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartLi() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<li>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("li");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartButton() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<button>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("button");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartAnchor() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<a>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("a");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartTable() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("table");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartImage() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<image>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("image");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyStartSelect() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.StartTag token = new Token.StartTag("select");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyEndBody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<body></body>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        Token.EndTag token = new Token.EndTag("body");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyEndHtml() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html></html>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("html"));
        tb.process(new Token.StartTag("body"));
        Token.EndTag token = new Token.EndTag("html");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyEndParagraph() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</p>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.StartTag("p"));
        Token.EndTag token = new Token.EndTag("p");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInBodyEndFormatting() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</b>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.StartTag("b"));
        Token.EndTag token = new Token.EndTag("b");
        boolean result = TreeBuilderState.InBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("abc", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("abc");
        boolean result = TreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEOF() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.Text);
        Token.EOF token = new Token.EOF();
        boolean result = TreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testTextStateEndTag() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("</script>", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.Text);
        Token.EndTag token = new Token.EndTag("script");
        boolean result = TreeBuilderState.Text.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("abc", "http://example.com", new ParseErrorList(0, 0));
        Token.Character token = new Token.Character("abc");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInTableStartCaption() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><caption></caption></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.StartTag token = new Token.StartTag("caption");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartColgroup() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup></colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.StartTag token = new Token.StartTag("colgroup");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartCol() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><col></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.StartTag token = new Token.StartTag("col");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartTbody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.StartTag token = new Token.StartTag("tbody");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableStartTr() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tr></tr></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.StartTag token = new Token.StartTag("tr");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableEndTable() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.EndTag token = new Token.EndTag("table");
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableEOF() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        Token.EOF token = new Token.EOF();
        boolean result = TreeBuilderState.InTable.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableTextCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("abc", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.InTableText);
        Token.Character token = new Token.Character("abc");
        boolean result = TreeBuilderState.InTableText.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableTextNonCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table>abc</table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.transition(TreeBuilderState.InTableText);
        Token.StartTag token = new Token.StartTag("div");
        boolean result = TreeBuilderState.InTableText.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInCaptionEndCaption() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><caption></caption></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("caption"));
        Token.EndTag token = new Token.EndTag("caption");
        boolean result = TreeBuilderState.InCaption.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInCaptionAnythingElse() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><caption><div></div></caption></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("caption"));
        Token.StartTag token = new Token.StartTag("div");
        boolean result = TreeBuilderState.InCaption.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup>   </colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("colgroup"));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.InColumnGroup.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup><!-- c --></colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("colgroup"));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.InColumnGroup.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup><DOCTYPE></colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("colgroup"));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InColumnGroup.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupStartCol() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup><col></colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("colgroup"));
        Token.StartTag token = new Token.StartTag("col");
        boolean result = TreeBuilderState.InColumnGroup.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInColumnGroupEndColgroup() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><colgroup></colgroup></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("colgroup"));
        Token.EndTag token = new Token.EndTag("colgroup");
        boolean result = TreeBuilderState.InColumnGroup.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableBodyStartTr() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody><tr></tr></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("tbody"));
        Token.StartTag token = new Token.StartTag("tr");
        boolean result = TreeBuilderState.InTableBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInTableBodyEndTbody() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("tbody"));
        Token.EndTag token = new Token.EndTag("tbody");
        boolean result = TreeBuilderState.InTableBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInRowStartTd() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody><tr><td></td></tr></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("tbody"));
        tb.process(new Token.StartTag("tr"));
        Token.StartTag token = new Token.StartTag("td");
        boolean result = TreeBuilderState.InRow.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInRowEndTr() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody><tr></tr></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("tbody"));
        tb.process(new Token.StartTag("tr"));
        Token.EndTag token = new Token.EndTag("tr");
        boolean result = TreeBuilderState.InRow.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInCellEndTd() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<table><tbody><tr><td></td></tr></tbody></table>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("table"));
        tb.process(new Token.StartTag("tbody"));
        tb.process(new Token.StartTag("tr"));
        tb.process(new Token.StartTag("td"));
        Token.EndTag token = new Token.EndTag("td");
        boolean result = TreeBuilderState.InCell.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectCharacter() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select>abc</select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.Character token = new Token.Character("abc");
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select><!-- c --></select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select><DOCTYPE></select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInSelectStartOption() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select><option></select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.StartTag token = new Token.StartTag("option");
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectStartOptgroup() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select><optgroup></optgroup></select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.StartTag token = new Token.StartTag("optgroup");
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInSelectEndSelect() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<select></select>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("select"));
        Token.EndTag token = new Token.EndTag("select");
        boolean result = TreeBuilderState.InSelect.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html>   ", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("html"));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.EndTag("body"));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.AfterBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html><!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("html"));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.EndTag("body"));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.AfterBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterBodyDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html><!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("html"));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.EndTag("body"));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.AfterBody.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterBodyEndHtml() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("html"));
        tb.process(new Token.StartTag("body"));
        tb.process(new Token.EndTag("body"));
        Token.EndTag token = new Token.EndTag("html");
        boolean result = TreeBuilderState.AfterBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset>   </frameset>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("frameset"));
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.InFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset><!-- c --></frameset>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("frameset"));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.InFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset><DOCTYPE></frameset>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("frameset"));
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.InFrameset.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testInFramesetStartFrame() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset><frame></frameset>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("frameset"));
        Token.StartTag token = new Token.StartTag("frame");
        boolean result = TreeBuilderState.InFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testInFramesetEndFrameset() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset></frameset>", "http://example.com", new ParseErrorList(0, 0));
        tb.process(new Token.StartTag("frameset"));
        Token.EndTag token = new Token.EndTag("frameset");
        boolean result = TreeBuilderState.InFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset></frameset>   ", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.AfterFrameset);
        Token.Character token = new Token.Character("   ");
        boolean result = TreeBuilderState.AfterFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset></frameset><!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.AfterFrameset);
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.AfterFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterFramesetDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset></frameset><!DOCTYPE html>", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.AfterFrameset);
        Token.Doctype token = new Token.Doctype();
        boolean result = TreeBuilderState.AfterFrameset.process(token, tb);
        assertFalse(result);
    }

    @Test
    public void testAfterAfterBodyComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html><!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.AfterAfterBody);
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.AfterAfterBody.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testAfterAfterFramesetComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<frameset></frameset><!-- c -->", "http://example.com", new ParseErrorList(0, 0));
        tb.transition(TreeBuilderState.AfterAfterFrameset);
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.AfterAfterFrameset.process(token, tb);
        assertTrue(result);
    }

    @Test
    public void testForeignContentProcess() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<math></math>", "http://example.com", new ParseErrorList(0, 0));
        Token.Comment token = new Token.Comment();
        boolean result = TreeBuilderState.ForeignContent.process(token, tb);
        assertTrue(result);
    }
}