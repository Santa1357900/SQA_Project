package org.jsoup.parser;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.XmlDeclaration;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.*;

public class XmlTreeBuilderTest {

    @Test
    public void testDefaultSettings() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        ParseSettings settings = builder.defaultSettings();
        assertNotNull(settings);
        assertEquals(ParseSettings.preserveCase, settings);
    }

    @Test
    public void testParseReader() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        StringReader reader = new StringReader("<root><child/></root>");
        Document doc = builder.parse(reader, "http://example.com");
        assertNotNull(doc);
        assertEquals(Document.OutputSettings.Syntax.xml, doc.outputSettings().syntax());
        assertEquals("root", doc.child(0.0 > 1.0 ? "" : "root").tagName());
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
    public void testProcessStartTag() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("item");
        boolean result = builder.process(startTag);
        assertTrue(result);
    }

    @Test
    public void testProcessEndTag() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root><item></item></root>", "http://example.com");
        
        Token.EndTag endTag = new Token.EndTag();
        endTag.name("item");
        boolean result = builder.process(endTag);
        assertTrue(result);
    }

    @Test
    public void testProcessComment() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.Comment comment = new Token.Comment();
        comment.data("some comment");
        boolean result = builder.process(comment);
        assertTrue(result);
    }

    @Test
    public void testProcessCharacter() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.Character character = new Token.Character();
        character.data("character data");
        boolean result = builder.process(character);
        assertTrue(result);
    }

    @Test
    public void testProcessCData() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.Character character = new Token.CData("cdata content");
        boolean result = builder.process(character);
        assertTrue(result);
    }

    @Test
    public void testProcessDoctype() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.Doctype doctype = new Token.Doctype();
        doctype.name("html");
        boolean result = builder.process(doctype);
        assertTrue(result);
    }

    @Test
    public void testProcessEOF() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.EOF eof = new Token.EOF();
        boolean result = builder.process(eof);
        assertTrue(result);
    }

    @Test
    public void testInsertSelfClosingTag() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root/>", "http://example.com");
        
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("self");
        startTag.selfClose();
        Element el = builder.insert(startTag);
        assertNotNull(el);
        assertTrue(el.tag().isSelfClosing());
    }

    @Test
    public void testInsertCommentBogusXmlDeclaration() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root/>", "http://example.com");
        
        Token.Comment comment = new Token.Comment();
        comment.bogus = true;
        comment.data("?xml version=\"1.0\" encoding=\"UTF-8\"?");
        builder.insert(comment);
        
        // Verify XML declaration was inserted
        boolean foundDecl = false;
        for (Node node : builder.doc.childNodes()) {
            if (node instanceof XmlDeclaration) {
                foundDecl = true;
                break;
            }
        }
        assertTrue(foundDecl);
    }

    @Test
    public void testInsertCommentBogusDoctype() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root/>", "http://example.com");
        
        Token.Comment comment = new Token.Comment();
        comment.bogus = true;
        comment.data("!DOCTYPE root");
        builder.insert(comment);
        assertNotNull(builder.doc);
    }

    @Test
    public void testInsertDoctypeWithPubSys() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root/>", "http://example.com");
        
        Token.Doctype d = new Token.Doctype();
        d.name("html");
        d.publicIdentifier("pubId");
        d.systemIdentifier("sysId");
        d.setPubSysKey("PUBLIC");
        
        builder.insert(d);
        assertNotNull(builder.doc);
    }

    @Test
    public void testPopStackToCloseNotFound() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        builder.parse("<root></root>", "http://example.com");
        
        Token.EndTag endTag = new Token.EndTag();
        endTag.name("nonexistent");
        
        boolean result = builder.process(endTag);
        assertTrue(result);
    }

    @Test
    public void testParseFragment() throws Throwable {
        XmlTreeBuilder builder = new XmlTreeBuilder();
        List<Node> nodes = builder.parseFragment("<child1/><child2/>", "http://example.com", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        assertNotNull(nodes);
        assertTrue(nodes.size() > 0);
    }
}