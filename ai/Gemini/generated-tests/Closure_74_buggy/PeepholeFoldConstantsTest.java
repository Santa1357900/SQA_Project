package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeFoldConstantsTest extends TestCase {

  private PeepholeFoldConstants folder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    folder = new PeepholeFoldConstants();
  }

  public void testOptimizeSubtreeDefault() throws Throwable {
    Node expr = new Node(Token.EXPR_RESULT, Node.newNumber(10));
    Node result = folder.optimizeSubtree(expr);
    assertNotNull(result);
  }

  public void testTryFoldTypeof() throws Throwable {
    Node stringLit = Node.newString("hello");
    Node typeofNode = new Node(Token.TYPEOF, stringLit);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);

    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("string", result.getString());
  }

  public void testTryFoldTypeofNonLiteral() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    Node typeofNode = new Node(Token.TYPEOF, nameNode);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);

    Node result = folder.optimizeSubtree(typeofNode);
    assertSame(typeofNode, result);
  }

  public void testTryFoldUnaryOperatorNot() throws Throwable {
    Node trueNode = new Node(Token.TRUE);
    Node notNode = new Node(Token.NOT, trueNode);
    Node parent = new Node(Token.EXPR_RESULT, notNode);

    Node result = folder.optimizeSubtree(notNode);
    assertNotNull(result);
    assertEquals(Token.FALSE, result.getType());
  }

  public void testTryFoldUnaryOperatorNeg() throws Throwable {
    Node numNode = Node.newNumber(5.0);
    Node negNode = new Node(Token.NEG, numNode);
    Node parent = new Node(Token.EXPR_RESULT, negNode);

    Node result = folder.optimizeSubtree(negNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(-5.0, result.getDouble());
  }

  public void testTryFoldUnaryOperatorBitNot() throws Throwable {
    Node numNode = Node.newNumber(5.0);
    Node bitNotNode = new Node(Token.BITNOT, numNode);
    Node parent = new Node(Token.EXPR_RESULT, bitNotNode);

    Node result = folder.optimizeSubtree(bitNotNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(-6.0, result.getDouble());
  }

  public void testTryFoldInstanceof() throws Throwable {
    Node left = Node.newString("abc");
    Node right = Node.newString(Token.NAME, "Object");
    Node instanceOfNode = new Node(Token.INSTANCEOF, left, right);
    Node parent = new Node(Token.EXPR_RESULT, instanceOfNode);

    Node result = folder.optimizeSubtree(instanceOfNode);
    assertNotNull(result);
    assertEquals(Token.FALSE, result.getType());
  }

  public void testTryFoldAndOr() throws Throwable {
    Node left = new Node(Token.TRUE);
    Node right = Node.newNumber(10);
    Node andNode = new Node(Token.AND, left, right);
    Node parent = new Node(Token.EXPR_RESULT, andNode);

    Node result = folder.optimizeSubtree(andNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
  }

  public void testTryFoldAddConstants() throws Throwable {
    Node left = Node.newNumber(5.0);
    Node right = Node.newNumber(10.0);
    Node addNode = new Node(Token.ADD, left, right);
    Node parent = new Node(Token.EXPR_RESULT, addNode);

    Node result = folder.optimizeSubtree(addNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(15.0, result.getDouble());
  }

  public void testTryFoldAddStrings() throws Throwable {
    Node left = Node.newString("foo");
    Node right = Node.newString("bar");
    Node addNode = new Node(Token.ADD, left, right);
    Node parent = new Node(Token.EXPR_RESULT, addNode);

    Node result = folder.optimizeSubtree(addNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("foobar", result.getString());
  }

  public void testTryFoldShift() throws Throwable {
    Node left = Node.newNumber(8.0);
    Node right = Node.newNumber(2.0);
    Node lshNode = new Node(Token.LSH, left, right);
    Node parent = new Node(Token.EXPR_RESULT, lshNode);

    Node result = folder.optimizeSubtree(lshNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(32.0, result.getDouble());
  }

  public void testTryFoldComparison() throws Throwable {
    Node left = Node.newNumber(5.0);
    Node right = Node.newNumber(5.0);
    Node eqNode = new Node(Token.EQ, left, right);
    Node parent = new Node(Token.EXPR_RESULT, eqNode);

    Node result = folder.optimizeSubtree(eqNode);
    assertNotNull(result);
    assertEquals(Token.TRUE, result.getType());
  }

  public void testTryFoldGetElemArray() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(10), Node.newNumber(20));
    Node index = Node.newNumber(1.0);
    Node getElem = new Node(Token.GETELEM, arrayLit, index);
    Node parent = new Node(Token.EXPR_RESULT, getElem);

    Node result = folder.optimizeSubtree(getElem);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(20.0, result.getDouble());
  }

  public void testTryFoldGetPropLength() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newNumber(2), Node.newNumber(3));
    Node prop = Node.newString(Token.STRING, "length");
    Node getProp = new Node(Token.GETPROP, arrayLit, prop);
    Node parent = new Node(Token.EXPR_RESULT, getProp);

    Node result = folder.optimizeSubtree(getProp);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(3.0, result.getDouble());
  }

  public void testTryFoldArrayJoin() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
    Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString(Token.STRING, "join"));
    Node call = new Node(Token.CALL, getProp, Node.newString(""));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = folder.optimizeSubtree(call);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("ab", result.getString());
  }

  public void testTryFoldStringSubstr() throws Throwable {
    Node stringLit = Node.newString("abcdef");
    Node getProp = new Node(Token.GETPROP, stringLit, Node.newString(Token.STRING, "substr"));
    Node call = new Node(Token.CALL, getProp, Node.newNumber(1.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = folder.optimizeSubtree(call);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("bcd", result.getString());
  }

  public void testTryFoldStringSubstring() throws Throwable {
    Node stringLit = Node.newString("abcdef");
    Node getProp = new Node(Token.GETPROP, stringLit, Node.newString(Token.STRING, "substring"));
    Node call = new Node(Token.CALL, getProp, Node.newNumber(1.0), Node.newNumber(4.0));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = folder.optimizeSubtree(call);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("bcd", result.getString());
  }

  public void testTryFoldObjectPropAccess() throws Throwable {
    Node key = Node.newString(Token.STRING, "a");
    key.addChildToBack(Node.newNumber(42));
    Node objLit = new Node(Token.OBJECTLIT, key);
    Node getProp = new Node(Token.GETPROP, objLit, Node.newString(Token.STRING, "a"));
    Node parent = new Node(Token.EXPR_RESULT, getProp);

    Node result = folder.optimizeSubtree(getProp);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(42.0, result.getDouble());
  }
}