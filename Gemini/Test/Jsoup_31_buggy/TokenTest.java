package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Attribute;

public class TokenTest {

    @Test
    public void testDoctypeToken() throws Throwable {
        Token.Doctype doctype = new Token.Doctype();
        assertEquals(Token.TokenType.Doctype, doctype.type);
        assertEquals("Doctype", doctype.tokenType());
        assertTrue(doctype.isDoctype());
        assertSame(doctype, doctype.asDoctype());

        doctype.name.append("html");
        doctype.publicIdentifier.append("pubId");
        doctype.systemIdentifier.append("sysId");
        doctype.forceQuirks = true;

        assertEquals("html", doctype.getName());
        assertEquals("pubId", doctype.getPublicIdentifier());
        assertEquals("sysId", doctype.getSystemIdentifier());
        assertTrue(doctype.isForceQuirks());
    }

    @Test
    public void testStartTagToken() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        assertEquals(Token.TokenType.StartTag, tag.type);
        assertEquals("StartTag", tag.tokenType());
        assertTrue(tag.isStartTag());
        assertSame(tag, tag.asStartTag());
        assertEquals("div", tag.name());
        assertFalse(tag.isSelfClosing());
        assertNotNull(tag.getAttributes());
        assertEquals("<div>", tag.toString());

        Attributes attrs = new Attributes();
        attrs.put("class", "container");
        Token.StartTag tagWithAttrs = new Token.StartTag("span", attrs);
        assertEquals("<span class=\"container\">", tagWithAttrs.toString());

        tag.name("p");
        assertEquals("p", tag.name());
    }

    @Test
    public void testEndTagToken() throws Throwable {
        Token.EndTag tag = new Token.EndTag("div");
        assertEquals(Token.TokenType.EndTag, tag.type);
        assertEquals("EndTag", tag.tokenType());
        assertTrue(tag.isEndTag());
        assertSame(tag, tag.asEndTag());
        assertEquals("div", tag.name());
        assertEquals("</div>", tag.toString());
    }

    @Test
    public void testCommentToken() throws Throwable {
        Token.Comment comment = new Token.Comment();
        assertEquals(Token.TokenType.Comment, comment.type);
        assertEquals("Comment", comment.tokenType());
        assertTrue(comment.isComment());
        assertSame(comment, comment.asComment());

        comment.data.append("test comment");
        assertEquals("test comment", comment.getData());
        assertEquals("<!--test comment-->", comment.toString());
    }

    @Test
    public void testCharacterToken() throws Throwable {
        Token.Character character = new Token.Character("abc");
        assertEquals(Token.TokenType.Character, character.type);
        assertEquals("Character", character.tokenType());
        assertTrue(character.isCharacter());
        assertSame(character, character.asCharacter());
        assertEquals("abc", character.getData());
        assertEquals("abc", character.toString());
    }

    @Test
    public void testEOFToken() throws Throwable {
        Token.EOF eof = new Token.EOF();
        assertEquals(Token.TokenType.EOF, eof.type);
        assertEquals("EOF", eof.tokenType());
        assertTrue(eof.isEOF());
    }

    @Test
    public void testTagAttributeManipulation() throws Throwable {
        Token.StartTag tag = new Token.StartTag("a");
        
        // Append attribute name using String and char
        tag.appendAttributeName("hre");
        tag.appendAttributeName('f');
        
        // Append attribute value using String and char
        tag.appendAttributeValue("www");
        tag.appendAttributeValue('.');
        tag.appendAttributeValue("com");
        
        tag.newAttribute();
        
        assertEquals("www.com", tag.getAttributes().get("href"));

        // Test attribute name without value
        tag.appendAttributeName("disabled");
        tag.newAttribute();
        assertEquals("", tag.getAttributes().get("disabled"));

        // Test finaliseTag with pending attribute name
        tag.appendAttributeName("checked");
        tag.finaliseTag();
        assertEquals("", tag.getAttributes().get("checked"));
    }

    @Test
    public void testTagAppendersAndEdgeCases() throws Throwable {
        Token.StartTag tag = new Token.StartTag();
        tag.name("span");
        
        // appendTagName with null and non-null
        tag.appendTagName("sub");
        tag.appendTagName('s');
        assertEquals("subs", tag.name());

        // pendingAttributeValue with multiple values and appenders
        tag.appendAttributeName("data-val");
        tag.appendAttributeValue("foo");
        tag.appendAttributeValue("bar");
        tag.newAttribute();
        assertEquals("foobar", tag.getAttributes().get("data-val"));

        // self-closing and attributes getter
        tag.selfClosing = true;
        assertTrue(tag.isSelfClosing());
        assertNotNull(tag.getAttributes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameValidation() throws Throwable {
        Token.StartTag tag = new Token.StartTag();
        tag.tagName = "";
        tag.name();
    }
}