package com.google.javascript.rhino;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenStreamTest {

    @Test
    public void testIsKeywordValidKeywords() throws Throwable {
        assertTrue(TokenStream.isKeyword("if"));
        assertTrue(TokenStream.isKeyword("in"));
        assertTrue(TokenStream.isKeyword("do"));
        assertTrue(TokenStream.isKeyword("for"));
        assertTrue(TokenStream.isKeyword("int"));
        assertTrue(TokenStream.isKeyword("new"));
        assertTrue(TokenStream.isKeyword("try"));
        assertTrue(TokenStream.isKeyword("var"));
        assertTrue(TokenStream.isKeyword("byte"));
        assertTrue(TokenStream.isKeyword("case"));
        assertTrue(TokenStream.isKeyword("char"));
        assertTrue(TokenStream.isKeyword("else"));
        assertTrue(TokenStream.isKeyword("enum"));
        assertTrue(TokenStream.isKeyword("goto"));
        assertTrue(TokenStream.isKeyword("long"));
        assertTrue(TokenStream.isKeyword("null"));
        assertTrue(TokenStream.isKeyword("true"));
        assertTrue(TokenStream.isKeyword("this"));
        assertTrue(TokenStream.isKeyword("void"));
        assertTrue(TokenStream.isKeyword("with"));
        assertTrue(TokenStream.isKeyword("class"));
        assertTrue(TokenStream.isKeyword("break"));
        assertTrue(TokenStream.isKeyword("while"));
        assertTrue(TokenStream.isKeyword("false"));
        assertTrue(TokenStream.isKeyword("const"));
        assertTrue(TokenStream.isKeyword("final"));
        assertTrue(TokenStream.isKeyword("float"));
        assertTrue(TokenStream.isKeyword("short"));
        assertTrue(TokenStream.isKeyword("super"));
        assertTrue(TokenStream.isKeyword("throw"));
        assertTrue(TokenStream.isKeyword("catch"));
        assertTrue(TokenStream.isKeyword("native"));
        assertTrue(TokenStream.isKeyword("delete"));
        assertTrue(TokenStream.isKeyword("return"));
        assertTrue(TokenStream.isKeyword("throws"));
        assertTrue(TokenStream.isKeyword("import"));
        assertTrue(TokenStream.isKeyword("double"));
        assertTrue(TokenStream.isKeyword("static"));
        assertTrue(TokenStream.isKeyword("public"));
        assertTrue(TokenStream.isKeyword("switch"));
        assertTrue(TokenStream.isKeyword("export"));
        assertTrue(TokenStream.isKeyword("typeof"));
        assertTrue(TokenStream.isKeyword("package"));
        assertTrue(TokenStream.isKeyword("default"));
        assertTrue(TokenStream.isKeyword("finally"));
        assertTrue(TokenStream.isKeyword("boolean"));
        assertTrue(TokenStream.isKeyword("private"));
        assertTrue(TokenStream.isKeyword("extends"));
        assertTrue(TokenStream.isKeyword("abstract"));
        assertTrue(TokenStream.isKeyword("continue"));
        assertTrue(TokenStream.isKeyword("debugger"));
        assertTrue(TokenStream.isKeyword("function"));
        assertTrue(TokenStream.isKeyword("volatile"));
        assertTrue(TokenStream.isKeyword("interface"));
        assertTrue(TokenStream.isKeyword("protected"));
        assertTrue(TokenStream.isKeyword("transient"));
        assertTrue(TokenStream.isKeyword("implements"));
        assertTrue(TokenStream.isKeyword("instanceof"));
        assertTrue(TokenStream.isKeyword("synchronized"));
    }

    @Test
    public void testIsKeywordInvalidKeywords() throws Throwable {
        assertFalse(TokenStream.isKeyword("notakeyword"));
        assertFalse(TokenStream.isKeyword("a"));
        assertFalse(TokenStream.isKeyword("toolongkeywordname"));
        assertFalse(TokenStream.isKeyword(""));
        assertFalse(TokenStream.isKeyword("fa"));
        assertFalse(TokenStream.isKeyword("fo"));
        assertFalse(TokenStream.isKeyword("fx"));
        assertFalse(TokenStream.isKeyword("cax"));
        assertFalse(TokenStream.isKeyword("cb"));
        assertFalse(TokenStream.isKeyword("ex"));
        assertFalse(TokenStream.isKeyword("ta"));
        assertFalse(TokenStream.isKeyword("tb"));
        assertFalse(TokenStream.isKeyword("ca"));
        assertFalse(TokenStream.isKeyword("daa"));
        assertFalse(TokenStream.isKeyword("fa"));
        assertFalse(TokenStream.isKeyword("ga"));
        assertFalse(TokenStream.isKeyword("ia"));
        assertFalse(TokenStream.isKeyword("pa"));
        assertFalse(TokenStream.isKeyword("za"));
    }

    @Test(expected = NullPointerException.class)
    public void testIsKeywordNull() throws Throwable {
        TokenStream.isKeyword(null);
    }

    @Test
    public void testIsJSIdentifierValid() throws Throwable {
        assertTrue(TokenStream.isJSIdentifier("validIdentifier"));
        assertTrue(TokenStream.isJSIdentifier("$var"));
        assertTrue(TokenStream.isJSIdentifier("_var"));
        assertTrue(TokenStream.isJSIdentifier("variable123"));
        assertTrue(TokenStream.isJSIdentifier("a"));
    }

    @Test
    public void testIsJSIdentifierInvalid() throws Throwable {
        assertFalse(TokenStream.isJSIdentifier(""));
        assertFalse(TokenStream.isJSIdentifier("123variable"));
        assertFalse(TokenStream.isJSIdentifier("var-name"));
        assertFalse(TokenStream.isJSIdentifier("var.name"));
        assertFalse(TokenStream.isJSIdentifier("var name"));
    }

    @Test(expected = NullPointerException.class)
    public void testIsJSIdentifierNull() throws Throwable {
        TokenStream.isJSIdentifier(null);
    }
}