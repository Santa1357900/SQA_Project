package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class FoldConstantsTest extends TestCase {

  private Compiler compiler;
  private FoldConstants foldConstants;

  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    foldConstants = new FoldConstants(compiler);
  }

  public void testProcess() throws Throwable {
    Node script = new Node(Token.SCRIPT, Node.newNumber(1));
    Node externs = new Node(Token.SCRIPT);
    foldConstants.process(externs, script);
    assertTrue(script.hasChildren());
  }

  public void testFoldBlock() throws Throwable {
    Node block = new Node(Token.BLOCK, Node.newNumber(1));
    Node parent = new Node(Token.SCRIPT, block);
    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, block, parent);
    assertEquals(Token.SCRIPT, parent.getType());
  }

  public void testTypeofLiteral() throws Throwable {
    Node numNode = Node.newNumber(5);
    Node typeofNode = new Node(Token.TYPEOF, numNode);
    Node exprResult = new Node(Token.EXPR_RESULT, typeofNode);
    Node parent = new Node(Token.BLOCK, exprResult);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, typeofNode, exprResult);

    assertEquals(Token.STRING, exprResult.getFirstChild().getType());
    assertEquals("number", exprResult.getFirstChild().getString());
  }

  public void testNotLiteral() throws Throwable {
    Node trueNode = new Node(Token.TRUE);
    Node notNode = new Node(Token.NOT, trueNode);
    Node exprResult = new Node(Token.EXPR_RESULT, notNode);
    Node parent = new Node(Token.BLOCK, exprResult);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, notNode, exprResult);

    assertEquals(Token.FALSE, exprResult.getFirstChild().getType());
  }

  public void testNegateLiteral() throws Throwable {
    Node numNode = Node.newNumber(5);
    Node negNode = new Node(Token.NEG, numNode);
    Node exprResult = new Node(Token.EXPR_RESULT, negNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, negNode, exprResult);

    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
    assertEquals(-5.0, exprResult.getFirstChild().getDouble());
  }

  public void testBitNotLiteral() throws Throwable {
    Node numNode = Node.newNumber(5);
    Node bitNotNode = new Node(Token.BITNOT, numNode);
    Node exprResult = new Node(Token.EXPR_RESULT, bitNotNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, bitNotNode, exprResult);

    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
    assertEquals(-6.0, exprResult.getFirstChild().getDouble());
  }

  public void testArithmeticAdd() throws Throwable {
    Node left = Node.newNumber(2);
    Node right = Node.newNumber(3);
    Node addNode = new Node(Token.ADD, left, right);
    Node exprResult = new Node(Token.EXPR_RESULT, addNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, addNode, exprResult);

    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
    assertEquals(5.0, exprResult.getFirstChild().getDouble());
  }

  public void testStringConcat() throws Throwable {
    Node left = Node.newString("a");
    Node right = Node.newString("b");
    Node addNode = new Node(Token.ADD, left, right);
    Node exprResult = new Node(Token.EXPR_RESULT, addNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, addNode, exprResult);

    assertEquals(Token.STRING, exprResult.getFirstChild().getType());
    assertEquals("ab", exprResult.getFirstChild().getString());
  }

  public void testComparisonEq() throws Throwable {
    Node left = Node.newNumber(5);
    Node right = Node.newNumber(5);
    Node eqNode = new Node(Token.EQ, left, right);
    Node exprResult = new Node(Token.EXPR_RESULT, eqNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, eqNode, exprResult);

    assertEquals(Token.TRUE, exprResult.getFirstChild().getType());
  }

  public void testGetElem() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(10), Node.newNumber(20));
    Node index = Node.newNumber(1);
    Node getElem = new Node(Token.GETELEM, arrayLit, index);
    Node exprResult = new Node(Token.EXPR_RESULT, getElem);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, getElem, exprResult);

    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
    assertEquals(20.0, exprResult.getFirstChild().getDouble());
  }

  public void testGetPropLength() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newNumber(2));
    Node prop = Node.newString("length");
    Node getProp = new Node(Token.GETPROP, arrayLit, prop);
    Node exprResult = new Node(Token.EXPR_RESULT, getProp);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, getProp, exprResult);

    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
    assertEquals(2.0, exprResult.getFirstChild().getDouble());
  }

  public void testAndOrFolding() throws Throwable {
    Node left = new Node(Token.TRUE);
    Node right = new Node(Token.FALSE);
    Node andNode = new Node(Token.AND, left, right);
    Node exprResult = new Node(Token.EXPR_RESULT, andNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, andNode, exprResult);

    assertEquals(Token.FALSE, exprResult.getFirstChild().getType());
  }

  public void testWhileFalseRemoval() throws Throwable {
    Node cond = new Node(Token.FALSE);
    Node body = new Node(Token.BLOCK);
    Node whileNode = new Node(Token.WHILE, cond, body);
    Node parent = new Node(Token.BLOCK, whileNode);

    NodeTraversal t = new NodeTraversal(compiler, foldConstants);
    foldConstants.visit(t, whileNode, parent);

    assertFalse(parent.hasChildren());
  }

  public void testContainsUnicodeEscape() {
    assertFalse(FoldConstants.containsUnicodeEscape("abc"));
  }
}