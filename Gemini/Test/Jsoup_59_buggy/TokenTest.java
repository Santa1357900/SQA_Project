package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Attributes;

public class TokenTest {

    @Test
    public void testDoctypeToken() throws Throwable {
        Token.Doctype doctype = new Token.Doctype();
        assertEquals("Doctype", doctype.tokenType());
        assertTrue(doctype.isDoctype());

        doctype.name.append("html");
        doctype.pubSysKey = "PUBLIC";
        doctype.publicIdentifier.append("-//W3C//DTD HTML 4.01//EN");
        doctype.systemIdentifier.append("http://www.w3.org/TR/html4/strict.dtd");
        doctype.forceQuirks = true;

        assertEquals("html", doctype.getName());
        assertEquals("PUBLIC", doctype.getPubSysKey());
        assertEquals("-//W3C//DTD HTML 4.01//EN", doctype.getPublicIdentifier());
        assertEquals("http://www.w3.org/TR/html4/strict.dtd", doctype.getSystemIdentifier());
        assertTrue(doctype.isForceQuirks());

        doctype.reset();
        assertEquals("", doctype.getName());
        assertNull(doctype.getPubSysKey());
        assertEquals("", doctype.getPublicIdentifier());
        assertEquals("", doctype.getSystemIdentifier());
        assertFalse(doctype.isForceQuirks());
    }

    @Test
    public void testStartTagToken() throws Throwable {
        Token.StartTag startTag = new Token.StartTag();
        assertEquals("StartTag", startTag.tokenType());
        assertTrue(startTag.isStartTag());

        startTag.name("div");
        assertEquals("div", startTag.name());
        assertEquals("div", startTag.normalName());
        assertFalse(startTag.isSelfClosing());

        startTag.selfClosing = true;
        assertTrue(startTag.isSelfClosing());

        startTag.appendTagName("Span");
        assertEquals("divSpan", startTag.name());
        assertEquals("divspan", startTag.normalName());

        startTag.appendTagName('X');
        assertEquals("divSpanX", startTag.name());

        startTag.appendAttributeName("id");
        startTag.appendAttributeName('1');
        startTag.setEmptyAttributeValue();
        startTag.newAttribute();

        startTag.appendAttributeName("class");
        startTag.appendAttributeValue("test-class");
        startTag.newAttribute();

        startTag.appendAttributeName("data-val");
        startTag.appendAttributeValue('A');
        startTag.appendAttributeValue(new char[]{'b', 'c'});
        startTag.appendAttributeValue(new int[]{68, 69}); // D, E
        startTag.newAttribute();

        startTag.finaliseTag();

        assertNotNull(startTag.getAttributes());
        assertTrue(startTag.toString().contains("<divspanx"));

        startTag.reset();
        assertNull(startTag.getAttributes());
    }

    @Test
    public void testStartTagWithNameAndAttributes() throws Throwable {
        Token.StartTag startTag = new Token.StartTag();
        Attributes attrs = new Attributes();
        attrs.put("href", "http://example.com");

        Token.StartTag configured = startTag.nameAttr("a", attrs);
        assertEquals("a", configured.name());
        assertEquals("<a href=\"http://example.com\">", configured.toString());
    }

    @Test
    public void testEndTagToken() throws Throwable {
        Token.EndTag endTag = new Token.EndTag();
        assertEquals("EndTag", endTag.tokenType());
        assertTrue(endTag.isEndTag());

        endTag.name("p");
        assertEquals("p", endTag.name());
        assertEquals("</p>", endTag.toString());

        endTag.reset();
    }

    @Test
    public void testCommentToken() throws Throwable {
        Token.Comment comment = new Token.Comment();
        assertEquals("Comment", comment.tokenType());
        assertTrue(comment.isComment());

        comment.data.append("some comment");
        comment.bogus = true;

        assertEquals("some comment", comment.getData());
        assertEquals("<!--some comment-->", comment.toString());

        comment.reset();
        assertEquals("", comment.getData());
        assertFalse(comment.bogus);
    }

    @Test
    public void testCharacterToken() throws Throwable {
        Token.Character character = new Token.Character();
        assertEquals("Character", character.tokenType());
        assertTrue(character.isCharacter());

        character.data("hello world");
        assertEquals("hello world", character.getData());
        assertEquals("hello world", character.toString());

        character.reset();
        assertNull(character.getData());
    }

    @Test
    public void testEOFToken() throws Throwable {
        Token.EOF eof = new Token.EOF();
        assertEquals("EOF", eof.tokenType());
        assertTrue(eof.isEOF());

        eof.reset();
    }

    @Test
    public void testResetStringBuilderNull() throws Throwable {
        // Should not throw exception when passing null
        Token.reset(null);
    }

    @Test
    public void testTagAttributeSingleShotAndBuilderMixed() throws Throwable {
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("input");
        
        // First attribute value using single shot (pendingAttributeValueS)
        startTag.appendAttributeName("value");
        startTag.appendAttributeValue("first");
        // Second append triggers ensureAttributeValue() moving single shot to StringBuilder
        startTag.appendAttributeValue("second");
        startTag.newAttribute();

        assertEquals("firstsecond", startTag.getAttributes().get("value"));
    }

    @Test
    public void testTagAttributeNoValueOrEmpty() throws Throwable {
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("hr");
        
        // Boolean attribute (no value set, no empty flag set)
        startTag.appendAttributeName("disabled");
        startTag.newAttribute();
        
        // Empty attribute value
        startTag.appendAttributeName("checked");
        startTag.setEmptyAttributeValue();
        startTag.newAttribute();

        assertTrue(startTag.getAttributes().hasKey("disabled"));
        assertEquals("", startTag.getAttributes().get("checked"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameValidationFailure() throws Throwable {
        Token.StartTag startTag = new Token.StartTag();
        startTag.name(); // tagName is null, should fail Validate.isFalse
    }
}