package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeReplaceKnownMethodsTest extends TestCase {

    private PeepholeReplaceKnownMethods optimizer;

    protected void setUp() throws Exception {
        super.setUp();
        optimizer = new PeepholeReplaceKnownMethods();
    }

    private Node wrapInScriptAndCall(Node callNode) {
        Node script = new Node(Token.SCRIPT);
        script.addChildToBack(callNode);
        return script;
    }

    public void testOptimizeSubtreeNonCall() throws Throwable {
        Node numberNode = Node.newNumber(10.0);
        Node result = optimizer.optimizeSubtree(numberNode);
        assertSame(numberNode, result);
    }

    public void testTryFoldKnownMethodsNullCallTarget() throws Throwable {
        Node callNode = new Node(Token.CALL);
        Node parent = new Node(Token.SCRIPT);
        parent.addChildToBack(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringMethodsNoArgsLowerCase() throws Throwable {
        // "abc".toLowerCase()
        Node stringNode = Node.newString("ABC");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("toLowerCase"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("abc", result.getString());
    }

    public void testTryFoldStringMethodsNoArgsUpperCase() throws Throwable {
        // "abc".toUpperCase()
        Node stringNode = Node.newString("abc");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("toUpperCase"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("ABC", result.getString());
    }

    public void testTryFoldStringIndexOf() throws Throwable {
        // "abcdef".indexOf("cd") -> 2
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("indexOf"));
        Node callNode = new Node(Token.CALL, getProp, Node.newString("cd"));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(2.0, result.getDouble());
    }

    public void testTryFoldStringLastIndexOf() throws Throwable {
        // "abcdefbc".lastIndexOf("bc") -> 6
        Node stringNode = Node.newString("abcdefbc");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("lastIndexOf"));
        Node callNode = new Node(Token.CALL, getProp, Node.newString("bc"));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(6.0, result.getDouble());
    }

    public void testTryFoldStringIndexOfWithFromIndex() throws Throwable {
        // "abcdefbc".indexOf("bc", 3) -> 6
        Node stringNode = Node.newString("abcdefbc");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("indexOf"));
        Node callNode = new Node(Token.CALL, getProp, Node.newString("bc"), Node.newNumber(3.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(6.0, result.getDouble());
    }

    public void testTryFoldStringIndexOfInvalidSearchValue() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("indexOf"));
        Node callNode = new Node(Token.CALL, getProp, new Node(Token.TRUE));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringIndexOfInvalidSecondArg() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("indexOf"));
        Node callNode = new Node(Token.CALL, getProp, Node.newString("cd"), Node.newString("invalid"));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringSubstr() throws Throwable {
        // "abcdef".substr(1, 3) -> "bcd"
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substr"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(1.0), Node.newNumber(3.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("bcd", result.getString());
    }

    public void testTryFoldStringSubstrNoLength() throws Throwable {
        // "abcdef".substr(2) -> "cdef"
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substr"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(2.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("cdef", result.getString());
    }

    public void testTryFoldStringSubstrOutOfBounds() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substr"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(5.0), Node.newNumber(10.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringSubstring() throws Throwable {
        // "abcdef".substring(1, 4) -> "bcd"
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substring"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(1.0), Node.newNumber(4.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("bcd", result.getString());
    }

    public void testTryFoldStringSubstringNoEnd() throws Throwable {
        // "abcdef".substring(2) -> "cdef"
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substring"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(2.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("cdef", result.getString());
    }

    public void testTryFoldStringSubstringOutOfBounds() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("substring"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(-1.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringCharAt() throws Throwable {
        // "abcdef".charAt(2) -> "c"
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("charAt"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(2.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("c", result.getString());
    }

    public void testTryFoldStringCharAtOutOfBounds() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("charAt"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(10.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldStringCharCodeAt() throws Throwable {
        // "abc".charCodeAt(1) -> 98
        Node stringNode = Node.newString("abc");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("charCodeAt"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(1.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(98.0, result.getDouble());
    }

    public void testTryFoldStringCharCodeAtOutOfBounds() throws Throwable {
        Node stringNode = Node.newString("abc");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString("charCodeAt"));
        Node callNode = new Node(Token.CALL, getProp, Node.newNumber(5.0));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testTryFoldArrayJoinDefault() throws Throwable {
        // ['a', 'b', 'c'].join() -> "a,b,c"
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"), Node.newString("c"));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("join"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("a,b,c", result.getString());
    }

    public void testTryFoldArrayJoinEmptySeparator() throws Throwable {
        // ['a', 'b', 'c'].join('') -> "abc"
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"), Node.newString("c"));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("join"));
        Node callNode = new Node(Token.CALL, getProp, Node.newString(""));
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("abc", result.getString());
    }

    public void testTryFoldArrayJoinEmptyArray() throws Throwable {
        // [].join() -> ""
        Node arrayLit = new Node(Token.ARRAYLIT);
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("join"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("", result.getString());
    }

    public void testTryFoldArrayJoinSingleElement() throws Throwable {
        // ['a'].join() -> "a"
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("join"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("a", result.getString());
    }

    public void testTryFoldArrayJoinWithNonImmutable() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), new Node(Token.NAME, "x"));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("join"));
        Node callNode = new Node(Token.CALL, getProp);
        wrapInScriptAndCall(callNode);

        Node result = optimizer.optimizeSubtree(callNode);
        assertSame(callNode, result);
    }

    public void testNormalizeNumericString() throws Throwable {
        // Tested indirectly via parseFloat/parseInt or internal logic if accessible, 
        // but since it's private, we test through parseFloat with strings having leading/trailing zeros.
        // parseFloat("001.100") -> 1.1
        Node nameNode = Node.newName("parseFloat");
        Node callNode = new Node(Token.CALL, nameNode, Node.newString("001.1"));
        wrapInScriptAndCall(callNode);

        Compiler compiler = new Compiler();
        AbstractPeepholeOptimization.setCompiler(compiler);

        Node result = optimizer.optimizeSubtree(callNode);
        assertEquals(Token.NUMBER, result.getType());
    }
}