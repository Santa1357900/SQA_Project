package org.jsoup.parser;

import org.junit.Test;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Attribute;

import static org.junit.Assert.*;

public class TreeBuilderStateTest {

    @Test
    public void testInitialStateWhitespace() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("   ", "", new ParseErrorList(0));
        Token.Character t = new Token.Character();
        t.data("   ");
        boolean result = TreeBuilderState.Initial.process(t, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        comment.data("test comment");
        boolean result = TreeBuilderState.Initial.process(comment, tb);
        assertTrue(result);
    }

    @Test
    public void testInitialStateDoctype() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        doctype.name("html");
        doctype.forceQuirks(true);
        boolean result = TreeBuilderState.Initial.process(doctype, tb);
        assertTrue(result);
        assertEquals(Document.QuirksMode.quirks, tb.getDocument().quirksMode());
    }

    @Test
    public void testInitialStateOtherToken() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("html");
        boolean result = TreeBuilderState.Initial.process(startTag, tb);
        assertTrue(result);
    }

    @Test
    public void testBeforeHtmlWhitespaceAndComments() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.Character whitespace = new Token.Character();
        whitespace.data(" \n\r\t");
        assertTrue(TreeBuilderState.BeforeHtml.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        comment.data("comment");
        assertTrue(TreeBuilderState.BeforeHtml.process(comment, tb));
    }

    @Test
    public void testBeforeHtmlDoctypeAndBadEndTag() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.Doctype doctype = new Token.Doctype();
        doctype.name("html");
        assertFalse(TreeBuilderState.BeforeHtml.process(doctype, tb));

        Token.EndTag badEnd = new Token.EndTag();
        badEnd.name("p");
        assertFalse(TreeBuilderState.BeforeHtml.process(badEnd, tb));
    }

    @Test
    public void testBeforeHtmlStartHtmlAndIgnoredEndTag() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.StartTag startHtml = new Token.StartTag();
        startHtml.name("html");
        assertTrue(TreeBuilderState.BeforeHtml.process(startHtml, tb));

        TreeBuilder tb2 = new TreeBuilder();
        tb2.initialiseParse("", "", new ParseErrorList(0));
        Token.EndTag ignoredEnd = new Token.EndTag();
        ignoredEnd.name("br");
        assertTrue(TreeBuilderState.BeforeHtml.process(ignoredEnd, tb2));
    }

    @Test
    public void testBeforeHeadVariousTokens() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.BeforeHead);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.BeforeHead.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        comment.data("c");
        assertTrue(TreeBuilderState.BeforeHead.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(TreeBuilderState.BeforeHead.process(doctype, tb));

        Token.StartTag startHtml = new Token.StartTag();
        startHtml.name("html");
        assertTrue(TreeBuilderState.BeforeHead.process(startHtml, tb));

        Token.StartTag startHead = new Token.StartTag();
        startHead.name("head");
        assertTrue(TreeBuilderState.BeforeHead.process(startHead, tb));
    }

    @Test
    public void testBeforeHeadEndAndOtherTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.BeforeHead);

        Token.EndTag endHead = new Token.EndTag();
        endHead.name("head");
        assertTrue(TreeBuilderState.BeforeHead.process(endHead, tb));

        TreeBuilder tb2 = new TreeBuilder();
        tb2.initialiseParse("<html>", "", new ParseErrorList(0));
        tb2.transition(TreeBuilderState.BeforeHead);
        Token.EndTag badEnd = new Token.EndTag();
        badEnd.name("unknown");
        assertFalse(TreeBuilderState.BeforeHead.process(badEnd, tb2));

        TreeBuilder tb3 = new TreeBuilder();
        tb3.initialiseParse("<html>", "", new ParseErrorList(0));
        tb3.transition(TreeBuilderState.BeforeHead);
        Token.StartTag other = new Token.StartTag();
        other.name("div");
        assertTrue(TreeBuilderState.BeforeHead.process(other, tb3));
    }

    @Test
    public void testInHeadTokens() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><head>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InHead);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.InHead.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InHead.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(TreeBuilderState.InHead.process(doctype, tb));

        Token.StartTag baseTag = new Token.StartTag();
        baseTag.name("base");
        baseTag.attributes = new Attributes();
        baseTag.attributes.put("href", "http://example.com");
        assertTrue(TreeBuilderState.InHead.process(baseTag, tb));

        Token.StartTag metaTag = new Token.StartTag();
        metaTag.name("meta");
        assertTrue(TreeBuilderState.InHead.process(metaTag, tb));

        Token.StartTag titleTag = new Token.StartTag();
        titleTag.name("title");
        assertTrue(TreeBuilderState.InHead.process(titleTag, tb));

        Token.StartTag styleTag = new Token.StartTag();
        styleTag.name("style");
        assertTrue(TreeBuilderState.InHead.process(styleTag, tb));

        Token.StartTag noscriptTag = new Token.StartTag();
        noscriptTag.name("noscript");
        assertTrue(TreeBuilderState.InHead.process(noscriptTag, tb));

        Token.StartTag scriptTag = new Token.StartTag();
        scriptTag.name("script");
        assertTrue(TreeBuilderState.InHead.process(scriptTag, tb));

        Token.StartTag headTag = new Token.StartTag();
        headTag.name("head");
        assertFalse(TreeBuilderState.InHead.process(headTag, tb));

        Token.EndTag endHead = new Token.EndTag();
        endHead.name("head");
        assertTrue(TreeBuilderState.InHead.process(endHead, tb));

        TreeBuilder tb2 = new TreeBuilder();
        tb2.initialiseParse("<html><head>", "", new ParseErrorList(0));
        tb2.transition(TreeBuilderState.InHead);
        Token.EndTag badEnd = new Token.EndTag();
        badEnd.name("unknown");
        assertFalse(TreeBuilderState.InHead.process(badEnd, tb2));
    }

    @Test
    public void testInHeadNoscriptState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><head><noscript>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InHeadNoscript);

        Token.Doctype doctype = new Token.Doctype();
        assertTrue(TreeBuilderState.InHeadNoscript.process(doctype, tb));

        Token.StartTag htmlTag = new Token.StartTag();
        htmlTag.name("html");
        assertTrue(TreeBuilderState.InHeadNoscript.process(htmlTag, tb));

        Token.EndTag endNoscript = new Token.EndTag();
        endNoscript.name("noscript");
        assertTrue(TreeBuilderState.InHeadNoscript.process(endNoscript, tb));

        TreeBuilder tb2 = new TreeBuilder();
        tb2.initialiseParse("<html><head><noscript>", "", new ParseErrorList(0));
        tb2.transition(TreeBuilderState.InHeadNoscript);
        Token.EndTag brTag = new Token.EndTag();
        brTag.name("br");
        assertTrue(TreeBuilderState.InHeadNoscript.process(brTag, tb2));

        TreeBuilder tb3 = new TreeBuilder();
        tb3.initialiseParse("<html><head><noscript>", "", new ParseErrorList(0));
        tb3.transition(TreeBuilderState.InHeadNoscript);
        Token.StartTag headTag = new Token.StartTag();
        headTag.name("head");
        assertFalse(TreeBuilderState.InHeadNoscript.process(headTag, tb3));
    }

    @Test
    public void testAfterHeadState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><head></head>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.AfterHead);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.AfterHead.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.AfterHead.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertTrue(TreeBuilderState.AfterHead.process(doctype, tb));

        Token.StartTag bodyTag = new Token.StartTag();
        bodyTag.name("body");
        assertTrue(TreeBuilderState.AfterHead.process(bodyTag, tb));

        TreeBuilder tbFrameset = new TreeBuilder();
        tbFrameset.initialiseParse("<html><head></head>", "", new ParseErrorList(0));
        tbFrameset.transition(TreeBuilderState.AfterHead);
        Token.StartTag framesetTag = new Token.StartTag();
        framesetTag.name("frameset");
        assertTrue(TreeBuilderState.AfterHead.process(framesetTag, tbFrameset));

        TreeBuilder tbHeadStart = new TreeBuilder();
        tbHeadStart.initialiseParse("<html><head></head>", "", new ParseErrorList(0));
        tbHeadStart.transition(TreeBuilderState.AfterHead);
        Token.StartTag headStart = new Token.StartTag();
        headStart.name("head");
        assertFalse(TreeBuilderState.AfterHead.process(headStart, tbHeadStart));

        TreeBuilder tbEnd = new TreeBuilder();
        tbEnd.initialiseParse("<html><head></head>", "", new ParseErrorList(0));
        tbEnd.transition(TreeBuilderState.AfterHead);
        Token.EndTag badEnd = new Token.EndTag();
        badEnd.name("div");
        assertFalse(TreeBuilderState.AfterHead.process(badEnd, tbEnd));
    }

    @Test
    public void testInBodyCharacterAndComment() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.Character nullChar = new Token.Character();
        nullChar.data(String.valueOf(0x0000));
        assertFalse(TreeBuilderState.InBody.process(nullChar, tb));

        Token.Character validChar = new Token.Character();
        validChar.data("Hello");
        assertTrue(TreeBuilderState.InBody.process(validChar, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InBody.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(TreeBuilderState.InBody.process(doctype, tb));
    }

    @Test
    public void testInBodyStartTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.StartTag htmlTag = new Token.StartTag();
        htmlTag.name("html");
        assertTrue(TreeBuilderState.InBody.process(htmlTag, tb));

        Token.StartTag headTag = new Token.StartTag();
        headTag.name("base");
        assertTrue(TreeBuilderState.InBody.process(headTag, tb));

        Token.StartTag bodyTag = new Token.StartTag();
        bodyTag.name("body");
        assertTrue(TreeBuilderState.InBody.process(bodyTag, tb));

        Token.StartTag pTag = new Token.StartTag();
        pTag.name("p");
        assertTrue(TreeBuilderState.InBody.process(pTag, tb));

        Token.StartTag h1Tag = new Token.StartTag();
        h1Tag.name("h1");
        assertTrue(TreeBuilderState.InBody.process(h1Tag, tb));

        Token.StartTag preTag = new Token.StartTag();
        preTag.name("pre");
        assertTrue(TreeBuilderState.InBody.process(preTag, tb));

        Token.StartTag formTag = new Token.StartTag();
        formTag.name("form");
        assertTrue(TreeBuilderState.InBody.process(formTag, tb));

        Token.StartTag liTag = new Token.StartTag();
        liTag.name("li");
        assertTrue(TreeBuilderState.InBody.process(liTag, tb));

        Token.StartTag ddTag = new Token.StartTag();
        ddTag.name("dd");
        assertTrue(TreeBuilderState.InBody.process(ddTag, tb));

        Token.StartTag plaintextTag = new Token.StartTag();
        plaintextTag.name("plaintext");
        assertTrue(TreeBuilderState.InBody.process(plaintextTag, tb));
    }

    @Test
    public void testInBodyMoreStartTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.StartTag buttonTag = new Token.StartTag();
        buttonTag.name("button");
        assertTrue(TreeBuilderState.InBody.process(buttonTag, tb));
        assertTrue(TreeBuilderState.InBody.process(buttonTag, tb)); // Second time triggers button in scope error branch

        Token.StartTag aTag = new Token.StartTag();
        aTag.name("a");
        assertTrue(TreeBuilderState.InBody.process(aTag, tb));
        assertTrue(TreeBuilderState.InBody.process(aTag, tb)); // Second 'a' triggers formatting element cleanup

        Token.StartTag formattingTag = new Token.StartTag();
        formattingTag.name("b");
        assertTrue(TreeBuilderState.InBody.process(formattingTag, tb));

        Token.StartTag nobrTag = new Token.StartTag();
        nobrTag.name("nobr");
        assertTrue(TreeBuilderState.InBody.process(nobrTag, tb));
        assertTrue(TreeBuilderState.InBody.process(nobrTag, tb));

        Token.StartTag appletTag = new Token.StartTag();
        appletTag.name("applet");
        assertTrue(TreeBuilderState.InBody.process(appletTag, tb));

        Token.StartTag tableTag = new Token.StartTag();
        tableTag.name("table");
        assertTrue(TreeBuilderState.InBody.process(tableTag, tb));
    }

    @Test
    public void testInBodyEmptyAndSpecialTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.StartTag brTag = new Token.StartTag();
        brTag.name("br");
        assertTrue(TreeBuilderState.InBody.process(brTag, tb));

        Token.StartTag inputTag = new Token.StartTag();
        inputTag.name("input");
        inputTag.attributes = new Attributes();
        inputTag.attributes.put("type", "text");
        assertTrue(TreeBuilderState.InBody.process(inputTag, tb));

        Token.StartTag hrTag = new Token.StartTag();
        hrTag.name("hr");
        assertTrue(TreeBuilderState.InBody.process(hrTag, tb));

        Token.StartTag imageTag = new Token.StartTag();
        imageTag.name("image");
        assertTrue(TreeBuilderState.InBody.process(imageTag, tb));

        Token.StartTag isindexTag = new Token.StartTag();
        isindexTag.name("isindex");
        isindexTag.attributes = new Attributes();
        isindexTag.attributes.put("action", "test");
        isindexTag.attributes.put("prompt", "Search:");
        assertTrue(TreeBuilderState.InBody.process(isindexTag, tb));

        Token.StartTag textareaTag = new Token.StartTag();
        textareaTag.name("textarea");
        assertTrue(TreeBuilderState.InBody.process(textareaTag, tb));
    }

    @Test
    public void testInBodyEndTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><p>Text", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.EndTag bodyEnd = new Token.EndTag();
        bodyEnd.name("body");
        assertTrue(TreeBuilderState.InBody.process(bodyEnd, tb));

        Token.EndTag htmlEnd = new Token.EndTag();
        htmlEnd.name("html");
        assertTrue(TreeBuilderState.InBody.process(htmlEnd, tb));

        Token.EndTag blockEnd = new Token.EndTag();
        blockEnd.name("div");
        assertFalse(TreeBuilderState.InBody.process(blockEnd, tb)); // div not in scope

        Token.EndTag pEnd = new Token.EndTag();
        pEnd.name("p");
        assertTrue(TreeBuilderState.InBody.process(pEnd, tb));
    }

    @Test
    public void testInBodyFormattingEndTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><b>bold", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InBody);

        Token.StartTag bTag = new Token.StartTag();
        bTag.name("b");
        TreeBuilderState.InBody.process(bTag, tb);

        Token.EndTag bEnd = new Token.EndTag();
        bEnd.name("b");
        assertTrue(TreeBuilderState.InBody.process(bEnd, tb));

        Token.EndTag sarcasmEnd = new Token.EndTag();
        sarcasmEnd.name("sarcasm");
        assertTrue(TreeBuilderState.InBody.process(sarcasmEnd, tb));

        Token.EndTag brEnd = new Token.EndTag();
        brEnd.name("br");
        assertFalse(TreeBuilderState.InBody.process(brEnd, tb));
    }

    @Test
    public void testTextState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><head><script>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.Text);

        Token.Character c = new Token.Character();
        c.data("var x = 1;");
        assertTrue(TreeBuilderState.Text.process(c, tb));

        Token.EndTag endTag = new Token.EndTag();
        endTag.name("script");
        assertTrue(TreeBuilderState.Text.process(endTag, tb));
    }

    @Test
    public void testInTableState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InTable);

        Token.Character c = new Token.Character();
        c.data("abc");
        assertTrue(TreeBuilderState.InTable.process(c, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InTable.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertFalse(TreeBuilderState.InTable.process(doctype, tb));

        Token.StartTag caption = new Token.StartTag();
        caption.name("caption");
        assertTrue(TreeBuilderState.InTable.process(caption, tb));

        Token.StartTag colgroup = new Token.StartTag();
        colgroup.name("colgroup");
        assertTrue(TreeBuilderState.InTable.process(colgroup, tb));

        Token.StartTag col = new Token.StartTag();
        col.name("col");
        assertTrue(TreeBuilderState.InTable.process(col, tb));

        Token.StartTag tbody = new Token.StartTag();
        tbody.name("tbody");
        assertTrue(TreeBuilderState.InTable.process(tbody, tb));
    }

    @Test
    public void testInTableMoreStartAndEndTags() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InTable);

        Token.StartTag tr = new Token.StartTag();
        tr.name("tr");
        assertTrue(TreeBuilderState.InTable.process(tr, tb));

        Token.StartTag table = new Token.StartTag();
        table.name("table");
        assertTrue(TreeBuilderState.InTable.process(table, tb));

        Token.StartTag input = new Token.StartTag();
        input.name("input");
        input.attributes = new Attributes();
        input.attributes.put("type", "hidden");
        assertTrue(TreeBuilderState.InTable.process(input, tb));

        Token.EndTag endTable = new Token.EndTag();
        endTable.name("table");
        assertTrue(TreeBuilderState.InTable.process(endTable, tb));
    }

    @Test
    public void testInTableTextState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InTableText);

        Token.Character c = new Token.Character();
        c.data("   ");
        assertTrue(TreeBuilderState.InTableText.process(c, tb));

        Token.StartTag start = new Token.StartTag();
        start.name("div");
        assertTrue(TreeBuilderState.InTableText.process(start, tb));
    }

    @Test
    public void testInCaptionState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table><caption>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InCaption);

        Token.EndTag captionEnd = new Token.EndTag();
        captionEnd.name("caption");
        assertTrue(TreeBuilderState.InCaption.process(captionEnd, tb));

        TreeBuilder tb2 = new TreeBuilder();
        tb2.initialiseParse("<html><body><table><caption>", "", new ParseErrorList(0));
        tb2.transition(TreeBuilderState.InCaption);
        Token.StartTag captionStart = new Token.StartTag();
        captionStart.name("caption");
        assertTrue(TreeBuilderState.InCaption.process(captionStart, tb2));
    }

    @Test
    public void testInColumnGroupState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table><colgroup>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InColumnGroup);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.InColumnGroup.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InColumnGroup.process(comment, tb));

        Token.Doctype doctype = new Token.Doctype();
        assertTrue(TreeBuilderState.InColumnGroup.process(doctype, tb));

        Token.StartTag col = new Token.StartTag();
        col.name("col");
        assertTrue(TreeBuilderState.InColumnGroup.process(col, tb));

        Token.EndTag colgroupEnd = new Token.EndTag();
        colgroupEnd.name("colgroup");
        assertTrue(TreeBuilderState.InColumnGroup.process(colgroupEnd, tb));
    }

    @Test
    public void testInTableBodyState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table><tbody>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InTableBody);

        Token.StartTag tr = new Token.StartTag();
        tr.name("tr");
        assertTrue(TreeBuilderState.InTableBody.process(tr, tb));

        Token.StartTag td = new Token.StartTag();
        td.name("td");
        assertTrue(TreeBuilderState.InTableBody.process(td, tb));

        Token.EndTag tbodyEnd = new Token.EndTag();
        tbodyEnd.name("tbody");
        assertTrue(TreeBuilderState.InTableBody.process(tbodyEnd, tb));
    }

    @Test
    public void testInRowState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table><tbody><tr>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InRow);

        Token.StartTag td = new Token.StartTag();
        td.name("td");
        assertTrue(TreeBuilderState.InRow.process(td, tb));

        Token.EndTag trEnd = new Token.EndTag();
        trEnd.name("tr");
        assertTrue(TreeBuilderState.InRow.process(trEnd, tb));
    }

    @Test
    public void testInCellState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><table><tbody><tr><td>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InCell);

        Token.EndTag tdEnd = new Token.EndTag();
        tdEnd.name("td");
        assertTrue(TreeBuilderState.InCell.process(tdEnd, tb));
    }

    @Test
    public void testInSelectState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body><select>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InSelect);

        Token.Character c = new Token.Character();
        c.data("opt");
        assertTrue(TreeBuilderState.InSelect.process(c, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InSelect.process(comment, tb));

        Token.StartTag option = new Token.StartTag();
        option.name("option");
        assertTrue(TreeBuilderState.InSelect.process(option, tb));

        Token.StartTag optgroup = new Token.StartTag();
        optgroup.name("optgroup");
        assertTrue(TreeBuilderState.InSelect.process(optgroup, tb));

        Token.EndTag selectEnd = new Token.EndTag();
        selectEnd.name("select");
        assertTrue(TreeBuilderState.InSelect.process(selectEnd, tb));
    }

    @Test
    public void testAfterBodyState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.AfterBody);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.AfterBody.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.AfterBody.process(comment, tb));

        Token.EndTag htmlEnd = new Token.EndTag();
        htmlEnd.name("html");
        assertTrue(TreeBuilderState.AfterBody.process(htmlEnd, tb));
    }

    @Test
    public void testInFramesetState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><frameset>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.InFrameset);

        Token.Character whitespace = new Token.Character();
        whitespace.data(" ");
        assertTrue(TreeBuilderState.InFrameset.process(whitespace, tb));

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.InFrameset.process(comment, tb));

        Token.StartTag frame = new Token.StartTag();
        frame.name("frame");
        assertTrue(TreeBuilderState.InFrameset.process(frame, tb));
    }

    @Test
    public void testAfterFramesetState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><frameset></frameset>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.AfterFrameset);

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.AfterFrameset.process(comment, tb));

        Token.EndTag htmlEnd = new Token.EndTag();
        htmlEnd.name("html");
        assertTrue(TreeBuilderState.AfterFrameset.process(htmlEnd, tb));
    }

    @Test
    public void testAfterAfterBodyState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><body></body></html>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.AfterAfterBody);

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.AfterAfterBody.process(comment, tb));

        Token.EOF eof = new Token.EOF();
        assertTrue(TreeBuilderState.AfterAfterBody.process(eof, tb));
    }

    @Test
    public void testAfterAfterFramesetState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("<html><frameset></frameset></html>", "", new ParseErrorList(0));
        tb.transition(TreeBuilderState.AfterAfterFrameset);

        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.AfterAfterFrameset.process(comment, tb));

        Token.EOF eof = new Token.EOF();
        assertTrue(TreeBuilderState.AfterAfterFrameset.process(eof, tb));
    }

    @Test
    public void testForeignContentState() throws Throwable {
        TreeBuilder tb = new TreeBuilder();
        tb.initialiseParse("", "", new ParseErrorList(0));
        Token.Comment comment = new Token.Comment();
        assertTrue(TreeBuilderState.ForeignContent.process(comment, tb));
    }
}