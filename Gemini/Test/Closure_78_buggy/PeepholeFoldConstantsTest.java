package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class PeepholeFoldConstantsTest {

  private PeepholeFoldConstants createOptimizer() {
    PeepholeFoldConstants optimizer = new PeepholeFoldConstants();
    optimizer.beginTraversal(null);
    return optimizer;
  }

  @Test
  public void testOptimizeSubtreeCall() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node callNode = new Node(Token.CALL,
        new Node(Token.GETPROP,
            Node.newString("abcdef"),
            Node.newString("indexOf")),
        Node.newString("cd"));
    Node parent = new Node(Token.EXPR_RESULT, callNode);

    Node result = optimizer.optimizeSubtree(callNode);
    assertNotNull(result);
  }

  @Test
  public void testOptimizeSubtreeNew() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node newObj = new Node(Token.NEW, Node.newString("Object"));
    Node parent = new Node(Token.EXPR_RESULT, newObj);

    Node result = optimizer.optimizeSubtree(newObj);
    assertNotNull(result);
  }

  @Test
  public void testOptimizeSubtreeTypeof() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node typeofNode = new Node(Token.TYPEOF, Node.newString("hello"));
    Node parent = new Node(Token.EXPR_RESULT, typeofNode);

    Node result = optimizer.optimizeSubtree(typeofNode);
    assertEquals(Token.STRING, result.getType());
    assertEquals("string", result.getString());
  }

  @Test
  public void testOptimizeSubtreeUnaryNot() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node notNode = new Node(Token.NOT, Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, notNode);

    Node result = optimizer.optimizeSubtree(notNode);
    assertEquals(Token.FALSE, result.getType());
  }

  @Test
  public void testOptimizeSubtreeUnaryPos() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node posNode = new Node(Token.POS, Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, posNode);

    Node result = optimizer.optimizeSubtree(posNode);
    assertNotNull(result);
  }

  @Test
  public void testOptimizeSubtreeUnaryNeg() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node negNode = new Node(Token.NEG, Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, negNode);

    Node result = optimizer.optimizeSubtree(negNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(-5.0, result.getDouble(), 0.0);
  }

  @Test
  public void testOptimizeSubtreeBitNot() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node bitnotNode = new Node(Token.BITNOT, Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, bitnotNode);

    Node result = optimizer.optimizeSubtree(bitnotNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(~5, (int) result.getDouble());
  }

  @Test
  public void testOptimizeSubtreeVoid() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node voidNode = new Node(Token.VOID, Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, voidNode);

    Node result = optimizer.optimizeSubtree(voidNode);
    assertNotNull(result);
  }

  @Test
  public void testTryFoldBinaryOperatorAdd() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node addNode = new Node(Token.ADD, Node.newNumber(2.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, addNode);

    Node result = optimizer.optimizeSubtree(addNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(5.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorSub() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node subNode = new Node(Token.SUB, Node.newNumber(5.0), Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, subNode);

    Node result = optimizer.optimizeSubtree(subNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(3.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorMul() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node mulNode = new Node(Token.MUL, Node.newNumber(4.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, mulNode);

    Node result = optimizer.optimizeSubtree(mulNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(12.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorDiv() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node divNode = new Node(Token.DIV, Node.newNumber(6.0), Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, divNode);

    Node result = optimizer.optimizeSubtree(divNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(3.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorMod() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node modNode = new Node(Token.MOD, Node.newNumber(5.0), Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, modNode);

    Node result = optimizer.optimizeSubtree(modNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(1.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorBitAnd() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node bitandNode = new Node(Token.BITAND, Node.newNumber(5.0), Node.newNumber(1.0));
    Node parent = new Node(Token.EXPR_RESULT, bitandNode);

    Node result = optimizer.optimizeSubtree(bitandNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(1.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorBitOr() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node bitorNode = new Node(Token.BITOR, Node.newNumber(4.0), Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, bitorNode);

    Node result = optimizer.optimizeSubtree(bitorNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(6.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldBinaryOperatorBitXor() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node bitxorNode = new Node(Token.BITXOR, Node.newNumber(5.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, bitxorNode);

    Node result = optimizer.optimizeSubtree(bitxorNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(6.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldShiftLsh() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node lshNode = new Node(Token.LSH, Node.newNumber(1.0), Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, lshNode);

    Node result = optimizer.optimizeSubtree(lshNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(4.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldShiftRsh() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node rshNode = new Node(Token.RSH, Node.newNumber(4.0), Node.newNumber(1.0));
    Node parent = new Node(Token.EXPR_RESULT, rshNode);

    Node result = optimizer.optimizeSubtree(rshNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(2.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldShiftUrsh() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node urshNode = new Node(Token.URSH, Node.newNumber(4.0), Node.newNumber(1.0));
    Node parent = new Node(Token.EXPR_RESULT, urshNode);

    Node result = optimizer.optimizeSubtree(urshNode);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(2.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldComparisonEq() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node eqNode = new Node(Token.EQ, Node.newNumber(5.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, eqNode);

    Node result = optimizer.optimizeSubtree(eqNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonNe() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node neNode = new Node(Token.NE, Node.newNumber(5.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, neNode);

    Node result = optimizer.optimizeSubtree(neNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonLt() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node ltNode = new Node(Token.LT, Node.newNumber(3.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, ltNode);

    Node result = optimizer.optimizeSubtree(ltNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonLe() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node leNode = new Node(Token.LE, Node.newNumber(5.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, leNode);

    Node result = optimizer.optimizeSubtree(leNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonGt() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node gtNode = new Node(Token.GT, Node.newNumber(5.0), Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, gtNode);

    Node result = optimizer.optimizeSubtree(gtNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldComparisonGe() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node geNode = new Node(Token.GE, Node.newNumber(5.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, geNode);

    Node result = optimizer.optimizeSubtree(geNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldAndOrAnd() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node andNode = new Node(Token.AND, Node.newNumber(0.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, andNode);

    Node result = optimizer.optimizeSubtree(andNode);
    assertNotNull(result);
  }

  @Test
  public void testTryFoldAndOrOr() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node orNode = new Node(Token.OR, Node.newNumber(1.0), Node.newNumber(5.0));
    Node parent = new Node(Token.EXPR_RESULT, orNode);

    Node result = optimizer.optimizeSubtree(orNode);
    assertNotNull(result);
  }

  @Test
  public void testTryFoldGetPropLength() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1.0), Node.newNumber(2.0));
    Node getProp = new Node(Token.GETPROP, arrayLit, Node.newString("length"));
    Node parent = new Node(Token.EXPR_RESULT, getProp);

    Node result = optimizer.optimizeSubtree(getProp);
    assertEquals(Token.NUMBER, result.getType());
    assertEquals(2.0, result.getDouble(), 0.0);
  }

  @Test
  public void testTryFoldGetElem() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
    Node getElem = new Node(Token.GETELEM, arrayLit, Node.newNumber(1.0));
    Node parent = new Node(Token.EXPR_RESULT, getElem);

    Node result = optimizer.optimizeSubtree(getElem);
    assertEquals(Token.STRING, result.getType());
    assertEquals("b", result.getString());
  }

  @Test
  public void testTryFoldArrayJoin() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
    Node call = new Node(Token.CALL,
        new Node(Token.GETPROP, arrayLit, Node.newString("join")),
        Node.newString(""));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = optimizer.optimizeSubtree(call);
    assertEquals(Token.STRING, result.getType());
    assertEquals("ab", result.getString());
  }

  @Test
  public void testTryFoldStringSubstr() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node call = new Node(Token.CALL,
        new Node(Token.GETPROP, Node.newString("abcdef"), Node.newString("substr")),
        Node.newNumber(1.0),
        Node.newNumber(2.0));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = optimizer.optimizeSubtree(call);
    assertEquals(Token.STRING, result.getType());
    assertEquals("bc", result.getString());
  }

  @Test
  public void testTryFoldStringSubstring() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node call = new Node(Token.CALL,
        new Node(Token.GETPROP, Node.newString("abcdef"), Node.newString("substring")),
        Node.newNumber(1.0),
        Node.newNumber(3.0));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = optimizer.optimizeSubtree(call);
    assertEquals(Token.STRING, result.getType());
    assertEquals("bc", result.getString());
  }

  @Test
  public void testTryFoldStringToLowerCase() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node call = new Node(Token.CALL,
        new Node(Token.GETPROP, Node.newString("ABC"), Node.newString("toLowerCase")));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = optimizer.optimizeSubtree(call);
    assertEquals(Token.STRING, result.getType());
    assertEquals("abc", result.getString());
  }

  @Test
  public void testTryFoldStringToUpperCase() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node call = new Node(Token.CALL,
        new Node(Token.GETPROP, Node.newString("abc"), Node.newString("toUpperCase")));
    Node parent = new Node(Token.EXPR_RESULT, call);

    Node result = optimizer.optimizeSubtree(call);
    assertEquals(Token.STRING, result.getType());
    assertEquals("ABC", result.getString());
  }

  @Test
  public void testTryFoldInstanceof() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node instanceofNode = new Node(Token.INSTANCEOF, Node.newString("abc"), Node.newString("Object"));
    Node parent = new Node(Token.EXPR_RESULT, instanceofNode);

    Node result = optimizer.optimizeSubtree(instanceofNode);
    assertEquals(Token.TRUE, result.getType());
  }

  @Test
  public void testTryFoldAssign() throws Throwable {
    PeepholeFoldConstants optimizer = createOptimizer();
    Node nameNode = Node.newString(Token.NAME, "x");
    Node addNode = new Node(Token.ADD, Node.newString(Token.NAME, "x"), Node.newNumber(1.0));
    Node assignNode = new Node(Token.ASSIGN, nameNode, addNode);
    Node parent = new Node(Token.EXPR_RESULT, assignNode);

    Node result = optimizer.optimizeSubtree(assignNode);
    assertNotNull(result);
  }
}