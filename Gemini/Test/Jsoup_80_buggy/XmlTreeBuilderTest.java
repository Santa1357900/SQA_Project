package org.jsoup.parser;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.XmlDeclaration;

import java.io.StringReader;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class XmlTreeBuilderTest {

    @Test
    public void testDefaultSettings() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        ParseSettings settings = builder.defaultSettings();
        assertEquals(ParseSettings.preserveCase, settings);
    }

    @Test
    public void testParseReader() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        StringReader reader = new StringReader("<root><child/></root>");
        Document doc = builder.parse(reader, "http://example.com");
        assertNotNull(doc);
        assertEquals("root", doc.child(0).tagName());
    }

    @Test
    public void testParseString() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        Document doc = builder.parse("<root>text</root>", "http://example.com");
        assertNotNull(doc);
        assertEquals("root", doc.child(0).tagName());
        assertEquals("text", doc.child(0).text());
    }

    @Test
    public void testProcessStartTagAndEndTag() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<tag></tag>"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        
        Token.StartTag start = new Token.StartTag();
        start.name("tag");
        assertTrue(builder.process(start));

        Token.EndTag end = new Token.EndTag();
        end.name("tag");
        assertTrue(builder.process(end));
    }

    @Test
    public void testProcessComment() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<!--comment-->"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Comment comment = new Token.Comment();
        comment.data = "comment data";
        assertTrue(builder.process(comment));
    }

    @Test
    public void testProcessBogusCommentXmlDeclaration() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<?xml version=\"1.0\"?>"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Comment comment = new Token.Comment();
        comment.bogus = true;
        comment.data = "?xml version=\"1.0\"?";
        assertTrue(builder.process(comment));
    }

    @Test
    public void testProcessBogusCommentBang() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<!DOCTYPE root>"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Comment comment = new Token.Comment();
        comment.bogus = true;
        comment.data = "!DOCTYPE root";
        assertTrue(builder.process(comment));
    }

    @Test
    public void testProcessCharacter() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader(""), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Character ch = new Token.Character();
        ch.data("hello xml");
        assertTrue(builder.process(ch));
    }

    @Test
    public void testProcessCData() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader(""), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Character ch = new Token.Character();
        ch.data("cdata content");
        ch.setAsCData();
        assertTrue(builder.process(ch));
    }

    @Test
    public void testProcessDoctype() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader(""), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.Doctype dt = new Token.Doctype();
        dt.name.append("html");
        dt.publicIdentifier("pubId");
        dt.systemIdentifier("sysId");
        assertTrue(builder.process(dt));
    }

    @Test
    public void testProcessEOF() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader(""), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.EOF eof = new Token.EOF();
        assertTrue(builder.process(eof));
    }

    @Test
    public void testSelfClosingUnknownTag() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<unknownTag/>"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.StartTag start = new Token.StartTag();
        start.name("unknownTag");
        start.selfClosing = true;
        
        Element el = builder.insert(start);
        assertNotNull(el);
        assertTrue(el.tag().isSelfClosing());
    }

    @Test
    public void testPopStackToCloseNotFound() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.initialiseParse(new StringReader("<root></root>"), "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);

        Token.EndTag endTag = new Token.EndTag();
        endTag.name("nonexistent");
        
        // This exercises popStackToClose when firstFound == null
        assertTrue(builder.process(endTag));
    }

    @Test
    public void testParseFragment() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        List<Node> nodes = builder.parseFragment("<child>text</child>", "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());
        assertEquals("child", nodes.get(0).nodeName());
    }
}