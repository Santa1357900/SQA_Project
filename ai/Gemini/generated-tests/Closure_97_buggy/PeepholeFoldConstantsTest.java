package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeFoldConstantsTest extends TestCase {

    private PeepholeFoldConstants optimizer;
    private AbstractCompiler compiler;

    protected void setUp() throws Exception {
        super.setUp();
        optimizer = new PeepholeFoldConstants();
        compiler = new Compiler();
        optimizer.beginTraversal(compiler);
    }

    public void testTypeofLiteralString() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, Node.newString("hello"));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);
        
        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("string", result.getString());
    }

    public void testTypeofLiteralNumber() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, Node.newNumber(42.0));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("number", result.getString());
    }

    public void testTypeofLiteralBoolean() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, new Node(Token.TRUE));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("boolean", result.getString());
    }

    public void testTypeofLiteralNull() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, new Node(Token.NULL));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("object", result.getString());
    }

    public void testTypeofVoid() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, new Node(Token.VOID, Node.newNumber(0)));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("undefined", result.getString());
    }

    public void testTypeofUndefinedName() throws Throwable {
        Node nameNode = Node.newString(Token.NAME, "undefined");
        Node typeofNode = new Node(Token.TYPEOF, nameNode);
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, result.getType());
        assertEquals("undefined", result.getString());
    }

    public void testTypeofNonLiteral() throws Throwable {
        Node nameNode = Node.newString(Token.NAME, "someVar");
        Node typeofNode = new Node(Token.TYPEOF, nameNode);
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);

        Node result = optimizer.optimizeSubtree(typeofNode);
        assertSame(typeofNode, result);
    }

    public void testUnaryNotTrue() throws Throwable {
        Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
        Node parent = new Node(Token.EXPR_RESULT, notNode);

        Node result = optimizer.optimizeSubtree(notNode);
        assertEquals(Token.FALSE, result.getType());
    }

    public void testUnaryNegateNumber() throws Throwable {
        Node negNode = new Node(Token.NEG, Node.newNumber(5.0));
        Node parent = new Node(Token.EXPR_RESULT, negNode);

        Node result = optimizer.optimizeSubtree(negNode);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(-5.0, result.getDouble(), 0.001);
    }

    public void testUnaryNegateInfinity() throws Throwable {
        Node nameNode = Node.newString(Token.NAME, "Infinity");
        Node negNode = new Node(Token.NEG, nameNode);
        Node parent = new Node(Token.EXPR_RESULT, negNode);

        Node result = optimizer.optimizeSubtree(negNode);
        assertSame(negNode, result);
    }

    public void testUnaryNegateNaN() throws Throwable {
        Node nameNode = Node.newString(Token.NAME, "NaN");
        Node negNode = new Node(Token.NEG, nameNode);
        Node parent = new Node(Token.EXPR_RESULT, negNode);

        Node result = optimizer.optimizeSubtree(negNode);
        assertSame(nameNode, result);
    }

    public void testBitNotInteger() throws Throwable {
        Node bitNot = new Node(Token.BITNOT, Node.newNumber(0.0));
        Node parent = new Node(Token.EXPR_RESULT, bitNot);

        Node result = optimizer.optimizeSubtree(bitNot);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(-1.0, result.getDouble(), 0.001);
    }

    public void testBitNotFractional() throws Throwable {
        Node bitNot = new Node(Token.BITNOT, Node.newNumber(1.5));
        Node parent = new Node(Token.EXPR_RESULT, bitNot);

        Node result = optimizer.optimizeSubtree(bitNot);
        assertSame(bitNot, result);
    }

    public void testBitNotOutOfRange() throws Throwable {
        Node bitNot = new Node(Token.BITNOT, Node.newNumber(1e20));
        Node parent = new Node(Token.EXPR_RESULT, bitNot);

        Node result = optimizer.optimizeSubtree(bitNot);
        assertSame(bitNot, result);
    }

    public void testInstanceofImmutable() throws Throwable {
        Node inst = new Node(Token.INSTANCEOF, Node.newString("abc"), Node.newString(Token.NAME, "Object"));
        Node parent = new Node(Token.EXPR_RESULT, inst);

        Node result = optimizer.optimizeSubtree(inst);
        assertEquals(Token.FALSE, result.getType());
    }

    public void testInstanceofObject() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT);
        Node inst = new Node(Token.INSTANCEOF, arrayLit, Node.newString(Token.NAME, "Object"));
        Node parent = new Node(Token.EXPR_RESULT, inst);

        Node result = optimizer.optimizeSubtree(inst);
        assertEquals(Token.TRUE, result.getType());
    }

    public void testBinaryAddConstants() throws Throwable {
        Node add = new Node(Token.ADD, Node.newNumber(2.0), Node.newNumber(3.0));
        Node parent = new Node(Token.EXPR_RESULT, add);

        Node result = optimizer.optimizeSubtree(add);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(5.0, result.getDouble(), 0.001);
    }

    public void testBinaryAddStrings() throws Throwable {
        Node add = new Node(Token.ADD, Node.newString("a"), Node.newString("b"));
        Node parent = new Node(Token.EXPR_RESULT, add);

        Node result = optimizer.optimizeSubtree(add);
        assertEquals(Token.STRING, result.getType());
        assertEquals("ab", result.getString());
    }

    public void testArithmeticSub() throws Throwable {
        Node sub = new Node(Token.SUB, Node.newNumber(5.0), Node.newNumber(2.0));
        Node parent = new Node(Token.EXPR_RESULT, sub);

        Node result = optimizer.optimizeSubtree(sub);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(3.0, result.getDouble(), 0.001);
    }

    public void testArithmeticMul() throws Throwable {
        Node mul = new Node(Token.MUL, Node.newNumber(3.0), Node.newNumber(4.0));
        Node parent = new Node(Token.EXPR_RESULT, mul);

        Node result = optimizer.optimizeSubtree(mul);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(12.0, result.getDouble(), 0.001);
    }

    public void testArithmeticDiv() throws Throwable {
        Node div = new Node(Token.DIV, Node.newNumber(10.0), Node.newNumber(2.0));
        Node parent = new Node(Token.EXPR_RESULT, div);

        Node result = optimizer.optimizeSubtree(div);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(5.0, result.getDouble(), 0.001);
    }

    public void testArithmeticDivByZero() throws Throwable {
        Node div = new Node(Token.DIV, Node.newNumber(10.0), Node.newNumber(0.0));
        Node parent = new Node(Token.EXPR_RESULT, div);

        Node result = optimizer.optimizeSubtree(div);
        assertSame(div, result);
    }

    public void testBitAndOr() throws Throwable {
        Node bitAnd = new Node(Token.BITAND, Node.newNumber(5.0), Node.newNumber(3.0));
        Node parent = new Node(Token.EXPR_RESULT, bitAnd);

        Node result = optimizer.optimizeSubtree(bitAnd);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(1.0, result.getDouble(), 0.001);

        Node bitOr = new Node(Token.BITOR, Node.newNumber(4.0), Node.newNumber(2.0));
        Node parent2 = new Node(Token.EXPR_RESULT, bitOr);

        Node result2 = optimizer.optimizeSubtree(bitOr);
        assertEquals(Token.NUMBER, result2.getType());
        assertEquals(6.0, result2.getDouble(), 0.001);
    }

    public void testShiftOperations() throws Throwable {
        Node lsh = new Node(Token.LSH, Node.newNumber(1.0), Node.newNumber(2.0));
        Node parent = new Node(Token.EXPR_RESULT, lsh);

        Node result = optimizer.optimizeSubtree(lsh);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(4.0, result.getDouble(), 0.001);

        Node rsh = new Node(Token.RSH, Node.newNumber(4.0), Node.newNumber(1.0));
        Node parent2 = new Node(Token.EXPR_RESULT, rsh);

        Node result2 = optimizer.optimizeSubtree(rsh);
        assertEquals(Token.NUMBER, result2.getType());
        assertEquals(2.0, result2.getDouble(), 0.001);

        Node ursh = new Node(Token.URSH, Node.newNumber(-1.0), Node.newNumber(1.0));
        Node parent3 = new Node(Token.EXPR_RESULT, ursh);

        Node result3 = optimizer.optimizeSubtree(ursh);
        assertEquals(Token.NUMBER, result3.getType());
        assertEquals(2147483647.0, result3.getDouble(), 0.001);
    }

    public void testShiftOutOfBounds() throws Throwable {
        Node lsh = new Node(Token.LSH, Node.newNumber(1.0), Node.newNumber(35.0));
        Node parent = new Node(Token.EXPR_RESULT, lsh);

        Node result = optimizer.optimizeSubtree(lsh);
        assertSame(lsh, result);
    }

    public void testComparisonNumber() throws Throwable {
        Node eq = new Node(Token.EQ, Node.newNumber(5.0), Node.newNumber(5.0));
        Node parent = new Node(Token.EXPR_RESULT, eq);

        Node result = optimizer.optimizeSubtree(eq);
        assertEquals(Token.TRUE, result.getType());
    }

    public void testComparisonString() throws Throwable {
        Node eq = new Node(Token.SHEQ, Node.newString("test"), Node.newString("test"));
        Node parent = new Node(Token.EXPR_RESULT, eq);

        Node result = optimizer.optimizeSubtree(eq);
        assertEquals(Token.TRUE, result.getType());
    }

    public void testStringIndexOf() throws Throwable {
        Node stringNode = Node.newString("abcdef");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString(Token.STRING, "indexOf"));
        Node call = new Node(Token.CALL, getProp, Node.newString("cd"));
        Node parent = new Node(Token.EXPR_RESULT, call);

        Node result = optimizer.optimizeSubtree(call);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(2.0, result.getDouble(), 0.001);
    }

    public void testStringJoin() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString(Token.STRING, "join"));
        Node call = new Node(Token.CALL, getProp, Node.newString(","));
        Node parent = new Node(Token.EXPR_RESULT, call);

        Node result = optimizer.optimizeSubtree(call);
        assertEquals(Token.STRING, result.getType());
        assertEquals("a,b", result.getString());
    }

    public void testGetElemValid() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(10.0), Node.newNumber(20.0));
        Node getElem = new Node(Token.GETELEM, arrayLit, Node.newNumber(1.0));
        Node parent = new Node(Token.EXPR_RESULT, getElem);

        Node result = optimizer.optimizeSubtree(getElem);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(20.0, result.getDouble(), 0.001);
    }

    public void testGetElemOutOfBounds() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(10.0));
        Node getElem = new Node(Token.GETELEM, arrayLit, Node.newNumber(5.0));
        Node parent = new Node(Token.EXPR_RESULT, getElem);

        Node result = optimizer.optimizeSubtree(getElem);
        assertSame(getElem, result);
    }

    public void testGetPropLengthArray() throws Throwable {
        Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1.0), Node.newNumber(2.0));
        Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString(Token.STRING, "length"));
        Node parent = new Node(Token.EXPR_RESULT, getProp);

        Node result = optimizer.optimizeSubtree(getProp);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(2.0, result.getDouble(), 0.001);
    }

    public void testGetPropLengthString() throws Throwable {
        Node stringNode = Node.newString("hello");
        Node getProp = new Node(Token.GETPROP, stringNode, Node.newString(Token.STRING, "length"));
        Node parent = new Node(Token.EXPR_RESULT, getProp);

        Node result = optimizer.optimizeSubtree(getProp);
        assertEquals(Token.NUMBER, result.getType());
        assertEquals(5.0, result.getDouble(), 0.001);
    }

    public void testAndOrShortCircuit() throws Throwable {
        Node andNode = new Node(Token.AND, new Node(Token.FALSE), Node.newNumber(1.0));
        Node parent = new Node(Token.EXPR_RESULT, andNode);

        Node result = optimizer.optimizeSubtree(andNode);
        assertEquals(Token.FALSE, result.getType());

        Node orNode = new Node(Token.OR, new Node(Token.TRUE), Node.newNumber(1.0));
        Node parent2 = new Node(Token.EXPR_RESULT, orNode);

        Node result2 = optimizer.optimizeSubtree(orNode);
        assertEquals(Token.TRUE, result2.getType());
    }

    public void testAssignFolding() throws Throwable {
        Node name1 = Node.newString(Token.NAME, "x");
        Node name2 = Node.newString(Token.NAME, "x");
        Node add = new Node(Token.ADD, name2, Node.newNumber(1.0));
        Node assign = new Node(Token.ASSIGN, name1, add);
        Node parent = new Node(Token.EXPR_RESULT, assign);

        Node result = optimizer.optimizeSubtree(assign);
        assertEquals(Token.ASSIGN_ADD, result.getType());
    }
}