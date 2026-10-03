package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class PeepholeFoldConstantsTest {

  @Test
  public void testOptimizeSubtreeCall() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    Node callNode = new Node(Token.CALL);
    Node result = folder.optimizeSubtree(callNode);
    assertNotNull(result);
  }

  @Test
  public void testTryFoldTypeof() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node stringLiteral = Node.newString("bar");
    Node typeofNode = new Node(Token.TYPEOF, stringLiteral);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);
    
    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("string", result.getString());
  }

  @Test
  public void testTryFoldTypeofNumber() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node numLiteral = Node.newNumber(5.0);
    Node typeofNode = new Node(Token.TYPEOF, numLiteral);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);
    
    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("number", result.getString());
  }

  @Test
  public void testTryFoldTypeofBoolean() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node trueNode = new Node(Token.TRUE);
    Node typeofNode = new Node(Token.TYPEOF, trueNode);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);
    
    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("boolean", result.getString());
  }

  @Test
  public void testTryFoldTypeofUndefinedName() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node nameNode = Node.newString(Token.NAME, "undefined");
    Node typeofNode = new Node(Token.TYPEOF, nameNode);
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);
    
    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("undefined", result.getString());
  }

  @Test
  public void testTryFoldUnaryNot() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node trueNode = new Node(Token.TRUE);
    Node notNode = new Node(Token.NOT, trueNode);
    Node parent = new Node(Token.EXPR_RESULT, notNode);
    
    Node result = folder.optimizeSubtree(notNode);
    assertNotNull(result);
    assertEquals(Token.FALSE, result.getType());
  }

  @Test
  public void testTryFoldUnaryNegNumber() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node numNode = Node.newNumber(5.0);
    Node negNode = new Node(Token.NEG, numNode);
    Node parent = new Node(Token.EXPR_RESULT, negNode);
    
    Node result = folder.optimizeSubtree(negNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(-5.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldUnaryNegNaN() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node nameNode = Node.newString(Token.NAME, "NaN");
    Node negNode = new Node(Token.NEG, nameNode);
    Node parent = new Node(Token.EXPR_RESULT, negNode);
    
    Node result = folder.optimizeSubtree(negNode);
    assertNotNull(result);
    assertEquals(Token.NAME, result.getType());
    assertEquals("NaN", result.getString());
  }

  @Test
  public void testTryFoldUnaryBitNot() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node numNode = Node.newNumber(0.0);
    Node bitNotNode = new Node(Token.BITNOT, numNode);
    Node parent = new Node(Token.EXPR_RESULT, bitNotNode);
    
    Node result = folder.optimizeSubtree(bitNotNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(-1.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldInstanceofPrimitive() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node numNode = Node.newNumber(5.0);
    Node nameNode = Node.newString(Token.NAME, "Number");
    Node instanceofNode = new Node(Token.INSTANCEOF, numNode, nameNode);
    Node parent = new Node(Token.EXPR_RESULT, instanceofNode);
    
    Node result = folder.optimizeSubtree(instanceofNode);
    assertNotNull(result);
    assertEquals(Token.FALSE, result.getType());
  }

  @Test
  public void testTryFoldInstanceofObject() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node stringNode = Node.newString("abc");
    Node nameNode = Node.newString(Token.NAME, "Object");
    Node instanceofNode = new Node(Token.INSTANCEOF, stringNode, nameNode);
    Node parent = new Node(Token.EXPR_RESULT, instanceofNode);
    
    Node result = folder.optimizeSubtree(instanceofNode);
    assertNotNull(result);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldAndOr() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node trueNode = new Node(Token.TRUE);
    Node numNode = Node.newNumber(5.0);
    Node orNode = new Node(Token.OR, trueNode, numNode);
    Node parent = new Node(Token.EXPR_RESULT, orNode);
    
    Node result = folder.optimizeSubtree(orNode);
    assertNotNull(result);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldAddString() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftStr = Node.newString("Hello ");
    Node rightStr = Node.newString("World");
    Node addNode = new Node(Token.ADD, leftStr, rightStr);
    Node parent = new Node(Token.EXPR_RESULT, addNode);
    
    Node result = folder.optimizeSubtree(addNode);
    assertNotNull(result);
    assertEquals(Token.STRING, result.getType());
    assertEquals("Hello World", result.getString());
  }

  @Test
  public void testTryFoldArithmeticAdd() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(10.0);
    Node rightNum = Node.newNumber(32.0);
    Node addNode = new Node(Token.ADD, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, addNode);
    
    Node result = folder.optimizeSubtree(addNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(42.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldArithmeticSub() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(50.0);
    Node rightNum = Node.newNumber(8.0);
    Node subNode = new Node(Token.SUB, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, subNode);
    
    Node result = folder.optimizeSubtree(subNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(42.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldArithmeticMul() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(6.0);
    Node rightNum = Node.newNumber(7.0);
    Node mulNode = new Node(Token.MUL, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, mulNode);
    
    Node result = folder.optimizeSubtree(mulNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(42.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldArithmeticDiv() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(84.0);
    Node rightNum = Node.newNumber(2.0);
    Node divNode = new Node(Token.DIV, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, divNode);
    
    Node result = folder.optimizeSubtree(divNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(42.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldArithmeticDivByZero() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(1.0);
    Node rightNum = Node.newNumber(0.0);
    Node divNode = new Node(Token.DIV, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, divNode);
    
    Node result = folder.optimizeSubtree(divNode);
    assertNotNull(result);
    assertEquals(Token.DIV, result.getType());
  }

  @Test
  public void testTryFoldBitAnd() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(6.0);
    Node rightNum = Node.newNumber(3.0);
    Node bitAndNode = new Node(Token.BITAND, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, bitAndNode);
    
    Node result = folder.optimizeSubtree(bitAndNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(2.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldBitOr() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(4.0);
    Node rightNum = Node.newNumber(2.0);
    Node bitOrNode = new Node(Token.BITOR, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, bitOrNode);
    
    Node result = folder.optimizeSubtree(bitOrNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(6.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldShiftLsh() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(2.0);
    Node rightNum = Node.newNumber(3.0);
    Node lshNode = new Node(Token.LSH, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, lshNode);
    
    Node result = folder.optimizeSubtree(lshNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(16.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldComparisonEq() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(5.0);
    Node rightNum = Node.newNumber(5.0);
    Node eqNode = new Node(Token.EQ, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, eqNode);
    
    Node result = folder.optimizeSubtree(eqNode);
    assertNotNull(result);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonLt() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node leftNum = Node.newNumber(3.0);
    Node rightNum = Node.newNumber(5.0);
    Node ltNode = new Node(Token.LT, leftNum, rightNum);
    Node parent = new Node(Token.EXPR_RESULT, ltNode);
    
    Node result = folder.optimizeSubtree(ltNode);
    assertNotNull(result);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldGetPropLength() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node strNode = Node.newString("abcde");
    Node propNode = Node.newString("length");
    Node getPropNode = new Node(Token.GETPROP, strNode, propNode);
    Node parent = new Node(Token.EXPR_RESULT, getPropNode);
    
    Node result = folder.optimizeSubtree(getPropNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(5.0, result.getDouble(), 0.001);
  }

  @Test
  public void testTryFoldGetElemArray() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants();
    
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(10.0), Node.newNumber(20.0), Node.newNumber(30.0));
    Node indexNode = Node.newNumber(1.0);
    Node getElemNode = new Node(Token.GETELEM, arrayLit, indexNode);
    Node parent = new Node(Token.EXPR_RESULT, getElemNode);
    
    Node result = folder.optimizeSubtree(getElemNode);
    assertNotNull(result);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(20.0, result.getDouble(), 0.001);
  }

}