package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TokenClaudeTest {

    // Covers Doctype() constructor setting type and default field values
    @Test
    public void testDoctypeConstructor_and_defaults() throws Throwable {
        Token.Doctype d = new Token.Doctype();
        assertEquals(Token.TokenType.Doctype, d.type);
        assertEquals("", d.getName());
        assertEquals("", d.getPublicIdentifier());
        assertEquals("", d.getSystemIdentifier());
        assertFalse(d.isForceQuirks());
    }

    // Covers name/publicIdentifier/systemIdentifier StringBuilder accumulation and forceQuirks mutation
    @Test
    public void testDoctypeFieldsAppend_reflectedInGetters() throws Throwable {
        Token.Doctype d = new Token.Doctype();
        d.name.append("html");
        d.publicIdentifier.append("pub");
        d.systemIdentifier.append("sys");
        d.forceQuirks = true;
        assertEquals("html", d.getName());
        assertEquals("pub", d.getPublicIdentifier());
        assertEquals("sys", d.getSystemIdentifier());
        assertTrue(d.isForceQuirks());
    }

    // Covers appendTagName(String) branch where tagName is null initially
    @Test
    public void testTagAppendTagName_nullInitial_setsName() throws Throwable {
        Token.StartTag tag = new Token.StartTag();
        tag.appendTagName("div");
        assertEquals("div", tag.name());
    }

    // Covers appendTagName(String) branch where tagName already set, and appendTagName(char) overload
    @Test
    public void testTagAppendTagName_existingName_concatenates() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.appendTagName('x');
        assertEquals("divx", tag.name());
    }

    // Covers appendAttributeName(String)/(char) and newAttribute() creating one attribute with value
    @Test
    public void testTagAppendAttributeName_and_newAttribute_incrementsSize() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.appendAttributeName("i");
        tag.appendAttributeName('d');
        tag.appendAttributeValue("1");
        tag.newAttribute();
        assertEquals(1, tag.getAttributes().size());
    }

    // Covers newAttribute() branch where pendingAttributeName is null: no attribute is created
    @Test
    public void testTagAppendAttributeValue_withoutName_newAttribute_noAttributeAdded() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.appendAttributeValue("value-only");
        tag.newAttribute();
        assertEquals(0, tag.getAttributes().size());
    }

    // Covers newAttribute() being called multiple times, including no-value attribute branch
    @Test
    public void testTagNewAttribute_multipleCalls_accumulatesDistinctAttributes() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.appendAttributeName("id");
        tag.appendAttributeValue("1");
        tag.newAttribute();
        tag.appendAttributeName("class");
        tag.newAttribute();
        assertEquals(2, tag.getAttributes().size());
    }

    // Covers finaliseTag() branch when pendingAttributeName != null
    @Test
    public void testTagFinaliseTag_pendingAttributeName_createsAttribute() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.appendAttributeName("disabled");
        tag.finaliseTag();
        assertEquals(1, tag.getAttributes().size());
    }

    // Covers finaliseTag() branch when pendingAttributeName == null: no-op
    @Test
    public void testTagFinaliseTag_noPendingAttribute_noChange() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        tag.finaliseTag();
        assertEquals(0, tag.getAttributes().size());
    }

    // Covers Tag.name() validation branch: empty tagName must fail Validate.isFalse
    @Test
    public void testTagName_emptyTagName_throwsException() throws Throwable {
        Token.StartTag tag = new Token.StartTag("");
        try {
            tag.name();
            fail("expected exception for empty tag name");
        } catch (RuntimeException expected) {
        }
    }

    // Covers Tag.name() returning tagName when non-empty
    @Test
    public void testTagName_nonEmptyTagName_returnsName() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        assertEquals("div", tag.name());
    }

    // Covers Tag.name(String): should set tagName field and return `this` for chaining
    @Test
    public void testTagNameSetter_returnsSameInstanceAndUpdatesName() throws Throwable {
        Token.StartTag tag = new Token.StartTag();
        Token.Tag returned = tag.name("div");
        assertSame(tag, returned);
        assertEquals("div", tag.name());
    }

    // Covers isSelfClosing() default value and field mutation branch
    @Test
    public void testTagIsSelfClosing_defaultFalseAndSettable() throws Throwable {
        Token.StartTag tag = new Token.StartTag("br");
        assertFalse(tag.isSelfClosing());
        tag.selfClosing = true;
        assertTrue(tag.isSelfClosing());
    }

    // Covers getAttributes() accessor
    @Test
    public void testTagGetAttributes_notNullForStartTag() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        assertNotNull(tag.getAttributes());
    }

    // Covers StartTag() constructor: type set and attributes initialized to empty Attributes
    @Test
    public void testStartTagDefaultConstructor_initializesAttributesAndType() throws Throwable {
        Token.StartTag tag = new Token.StartTag();
        assertEquals(Token.TokenType.StartTag, tag.type);
        assertNotNull(tag.getAttributes());
        assertEquals(0, tag.getAttributes().size());
    }

    // Covers StartTag(String name) constructor
    @Test
    public void testStartTagNameConstructor_setsTagName() throws Throwable {
        Token.StartTag tag = new Token.StartTag("span");
        assertEquals("span", tag.name());
    }

    // Covers StartTag(String name, Attributes attributes) constructor
    @Test
    public void testStartTagNameAttributesConstructor_setsBoth() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put(new Attribute("id", "x"));
        Token.StartTag tag = new Token.StartTag("div", attrs);
        assertEquals("div", tag.name());
        assertSame(attrs, tag.getAttributes());
    }

    // Covers StartTag.toString() branch when attributes empty
    @Test
    public void testStartTagToString_noAttributes() throws Throwable {
        Token.StartTag tag = new Token.StartTag("div");
        assertEquals("<div>", tag.toString());
    }

    // Covers StartTag.toString() branch when attributes.size() > 0
    @Test
    public void testStartTagToString_withAttributes() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put(new Attribute("id", "x"));
        Token.StartTag tag = new Token.StartTag("div", attrs);
        String s = tag.toString();
        assertTrue(s.startsWith("<div "));
        assertTrue(s.endsWith(">"));
    }

    // Covers EndTag() constructor
    @Test
    public void testEndTagDefaultConstructor_setsType() throws Throwable {
        Token.EndTag tag = new Token.EndTag();
        assertEquals(Token.TokenType.EndTag, tag.type);
    }

    // Covers EndTag(String name) constructor
    @Test
    public void testEndTagNameConstructor_setsTagName() throws Throwable {
        Token.EndTag tag = new Token.EndTag("div");
        assertEquals("div", tag.name());
    }

    // Covers EndTag.toString()
    @Test
    public void testEndTagToString() throws Throwable {
        Token.EndTag tag = new Token.EndTag("div");
        assertEquals("</div>", tag.toString());
    }

    // Covers Comment() constructor
    @Test
    public void testCommentConstructor_setsTypeAndEmptyData() throws Throwable {
        Token.Comment c = new Token.Comment();
        assertEquals(Token.TokenType.Comment, c.type);
        assertEquals("", c.getData());
    }

    // Covers Comment.data StringBuilder accumulation reflected in getData()
    @Test
    public void testCommentAppendData_and_getData() throws Throwable {
        Token.Comment c = new Token.Comment();
        c.data.append("hello");
        assertEquals("hello", c.getData());
    }

    // Covers Comment.toString()
    @Test
    public void testCommentToString() throws Throwable {
        Token.Comment c = new Token.Comment();
        c.data.append("hi");
        assertEquals("<!--hi-->", c.toString());
    }

    // Covers Character(String data) constructor and getData()
    @Test
    public void testCharacterConstructor_storesData() throws Throwable {
        Token.Character ch = new Token.Character("abc");
        assertEquals("abc", ch.getData());
        assertEquals(Token.TokenType.Character, ch.type);
    }

    // Covers Character(String data) when data is null
    @Test
    public void testCharacterConstructor_nullData() throws Throwable {
        Token.Character ch = new Token.Character(null);
        assertNull(ch.getData());
    }

    // Covers Character.toString()
    @Test
    public void testCharacterToString() throws Throwable {
        Token.Character ch = new Token.Character("abc");
        assertEquals("abc", ch.toString());
    }

    // Covers EOF() constructor
    @Test
    public void testEOFConstructor_setsType() throws Throwable {
        Token.EOF eof = new Token.EOF();
        assertEquals(Token.TokenType.EOF, eof.type);
    }

    // Covers tokenType() returning the simple class name for different subclasses
    @Test
    public void testTokenType_returnsSimpleClassName_forEachSubclass() throws Throwable {
        Token doctype = new Token.Doctype();
        Token startTag = new Token.StartTag("div");
        assertEquals("Doctype", doctype.tokenType());
        assertEquals("StartTag", startTag.tokenType());
    }

    // Covers isDoctype()/asDoctype()
    @Test
    public void testIsDoctypeAndAsDoctype() throws Throwable {
        Token token = new Token.Doctype();
        assertTrue(token.isDoctype());
        Token.Doctype d = token.asDoctype();
        assertNotNull(d);
    }

    // Covers isStartTag()/asStartTag()
    @Test
    public void testIsStartTagAndAsStartTag() throws Throwable {
        Token token = new Token.StartTag("div");
        assertTrue(token.isStartTag());
        Token.StartTag st = token.asStartTag();
        assertEquals("div", st.name());
    }

    // Covers isEndTag()/asEndTag()
    @Test
    public void testIsEndTagAndAsEndTag() throws Throwable {
        Token token = new Token.EndTag("div");
        assertTrue(token.isEndTag());
        Token.EndTag et = token.asEndTag();
        assertEquals("div", et.name());
    }

    // Covers isComment()/asComment()
    @Test
    public void testIsCommentAndAsComment() throws Throwable {
        Token token = new Token.Comment();
        assertTrue(token.isComment());
        Token.Comment c = token.asComment();
        assertNotNull(c);
    }

    // Covers isCharacter()/asCharacter()
    @Test
    public void testIsCharacterAndAsCharacter() throws Throwable {
        Token token = new Token.Character("x");
        assertTrue(token.isCharacter());
        Token.Character ch = token.asCharacter();
        assertEquals("x", ch.getData());
    }

    // Covers isEOF() true/false branches
    @Test
    public void testIsEOF_trueForEOF_falseForStartTag() throws Throwable {
        Token eofToken = new Token.EOF();
        Token startTagToken = new Token.StartTag("div");
        assertTrue(eofToken.isEOF());
        assertFalse(startTagToken.isEOF());
    }

    // Covers asStartTag() cast failure when actual type mismatches
    @Test
    public void testAsStartTag_wrongType_throwsClassCastException() throws Throwable {
        Token token = new Token.Doctype();
        try {
            token.asStartTag();
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }


}
