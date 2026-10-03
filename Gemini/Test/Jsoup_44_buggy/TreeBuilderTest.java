package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;

public class TreeBuilderTest {

    private static class DummyTreeBuilder extends TreeBuilder {
        boolean processCalled = false;
        Token lastToken = null;

        @Override
        protected boolean process(Token token) {
            processCalled = true;
            lastToken = token;
            return true;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitialiseParseNullInput() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        builder.initialiseParse(null, "http://example.com", ParseErrorList.noTracking());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitialiseParseNullBaseUri() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        builder.initialiseParse("<html></html>", null, ParseErrorList.noTracking());
    }

    @Test
    public void testInitialiseParseValid() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        ParseErrorList errorList = ParseErrorList.tracking(10);
        builder.initialiseParse("<html></html>", "http://example.com", errorList);

        assertNotNull(builder.doc);
        assertEquals("http://example.com", builder.doc.baseUri());
        assertNotNull(builder.reader);
        assertNotNull(builder.tokeniser);
        assertNotNull(builder.stack);
        assertEquals(0, builder.stack.size());
        assertEquals("http://example.com", builder.baseUri);
        assertEquals(errorList, builder.errors);
    }

    @Test
    public void testCurrentElementEmptyStack() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        builder.stack = new ArrayList<Element>();
        assertNull(builder.currentElement());
    }

    @Test
    public void testCurrentElementWithElements() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        builder.stack = new ArrayList<Element>();
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("span"), "http://example.com");
        builder.stack.add(el1);
        builder.stack.add(el2);

        assertEquals(el2, builder.currentElement());
    }

    @Test
    public void testProcessStartTagString() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        boolean result = builder.processStartTag("div");
        assertTrue(result);
        assertTrue(builder.processCalled);
        assertNotNull(builder.lastToken);
        assertEquals(Token.TokenType.StartTag, builder.lastToken.type);
    }

    @Test
    public void testProcessStartTagStringAndAttributes() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        Attributes attrs = new Attributes();
        attrs.put("id", "testId");
        boolean result = builder.processStartTag("div", attrs);
        assertTrue(result);
        assertTrue(builder.processCalled);
        assertNotNull(builder.lastToken);
        assertEquals(Token.TokenType.StartTag, builder.lastToken.type);
    }

    @Test
    public void testProcessEndTagString() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        boolean result = builder.processEndTag("div");
        assertTrue(result);
        assertTrue(builder.processCalled);
        assertNotNull(builder.lastToken);
        assertEquals(Token.TokenType.EndTag, builder.lastToken.type);
    }

    @Test
    public void testParseWithInputAndBaseUri() throws Throwable {
        DummyTreeBuilder builder = new DummyTreeBuilder();
        Document doc = builder.parse("<html></html>", "http://example.com");
        assertNotNull(doc);
        assertEquals("http://example.com", doc.baseUri());
        assertTrue(builder.processCalled);
    }
}