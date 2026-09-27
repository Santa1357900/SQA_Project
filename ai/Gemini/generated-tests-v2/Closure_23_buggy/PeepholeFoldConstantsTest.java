package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeFoldConstantsTest extends TestCase {

  private Compiler compiler;

  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    // Initialize dummy compiler config if needed
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testConstructorLate() throws Throwable {
    PeepholeFoldConstants foldLate = new PeepholeFoldConstants(true);
    PeepholeFoldConstants foldEarly = new PeepholeFoldConstants(false);
    assertNotNull(foldLate);
    assertNotNull(foldEarly);
  }

  public void testOptimizeSubtreeNew() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node expr = IR.newnode(IR.name("String"), IR.string("abc"));
    Node parent = IR.exprResult(expr);
    Node result = folder.optimizeSubtree(expr);
    assertNotNull(result);
  }

  public void testOptimizeSubtreeTypeof() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node typeofNode = new Node(Token.TYPEOF, IR.number(5));
    Node parent = IR.exprResult(typeofNode);
    Node result = folder.optimizeSubtree(typeofNode);
    assertNotNull(result);
  }

  public void testOptimizeSubtreeUnary() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node notNode = new Node(Token.NOT, IR.trueNode());
    Node parent = IR.exprResult(notNode);
    Node result = folder.optimizeSubtree(notNode);
    assertNotNull(result);
  }

  public void testOptimizeSubtreeVoid() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node voidNode = new Node(Token.VOID, IR.number(5));
    Node parent = IR.exprResult(voidNode);
    Node result = folder.optimizeSubtree(voidNode);
    assertNotNull(result);
  }

  public void testOptimizeSubtreeBinary() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node addNode = IR.add(IR.number(1), IR.number(2));
    Node parent = IR.exprResult(addNode);
    Node result = folder.optimizeSubtree(addNode);
    assertNotNull(result);
  }

  public void testTryFoldBinaryOperatorGetProp() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node left = IR.string("abc");
    Node right = IR.string("length");
    Node getProp = new Node(Token.GETPROP, left, right);
    Node parent = IR.exprResult(getProp);
    Node result = folder.optimizeSubtree(getProp);
    assertNotNull(result);
  }

  public void testTryFoldBinaryOperatorGetElem() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node left = IR.arrayLit(IR.number(10), IR.number(20));
    Node right = IR.number(0);
    Node getElem = new Node(Token.GETELEM, left, right);
    Node parent = IR.exprResult(getElem);
    Node result = folder.optimizeSubtree(getElem);
    assertNotNull(result);
  }

  public void testTryFoldBinaryOperatorInstanceof() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node left = IR.string("abc");
    Node right = IR.name("Object");
    Node instanceOf = new Node(Token.INSTANCEOF, left, right);
    Node parent = IR.exprResult(instanceOf);
    Node result = folder.optimizeSubtree(instanceOf);
    assertNotNull(result);
  }

  public void testTryFoldBinaryOperatorAndOr() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node andNode = new Node(Token.AND, IR.trueNode(), IR.falseNode());
    Node parent = IR.exprResult(andNode);
    Node result = folder.optimizeSubtree(andNode);
    assertNotNull(result);

    Node orNode = new Node(Token.OR, IR.falseNode(), IR.trueNode());
    Node parent2 = IR.exprResult(orNode);
    Node result2 = folder.optimizeSubtree(orNode);
    assertNotNull(result2);
  }

  public void testTryFoldBinaryOperatorShift() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node lshNode = new Node(Token.LSH, IR.number(4), IR.number(1));
    Node parent = IR.exprResult(lshNode);
    Node result = folder.optimizeSubtree(lshNode);
    assertNotNull(result);

    Node rshNode = new Node(Token.RSH, IR.number(4), IR.number(1));
    Node parent2 = IR.exprResult(rshNode);
    Node result2 = folder.optimizeSubtree(rshNode);
    assertNotNull(result2);

    Node urshNode = new Node(Token.URSH, IR.number(4), IR.number(1));
    Node parent3 = IR.exprResult(urshNode);
    Node result3 = folder.optimizeSubtree(urshNode);
    assertNotNull(result3);
  }

  public void testTryFoldBinaryOperatorAssign() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node left = IR.name("x");
    Node right = IR.add(IR.name("x"), IR.number(1));
    Node assignNode = IR.assign(left, right);
    Node parent = IR.exprResult(assignNode);
    Node result = folder.optimizeSubtree(assignNode);
    assertNotNull(result);
  }

  public void testTryUnfoldAssignOp() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(false);
    Node left = IR.name("x");
    Node right = IR.number(1);
    Node assignAdd = new Node(Token.ASSIGN_ADD, left, right);
    Node parent = IR.exprResult(assignAdd);
    Node result = folder.optimizeSubtree(assignAdd);
    assertNotNull(result);
  }

  public void testTryFoldArithmeticOp() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node subNode = new Node(Token.SUB, IR.number(5), IR.number(3));
    Node parent = IR.exprResult(subNode);
    Node result = folder.optimizeSubtree(subNode);
    assertNotNull(result);

    Node mulNode = new Node(Token.MUL, IR.number(2), IR.number(3));
    Node parent2 = IR.exprResult(mulNode);
    Node result2 = folder.optimizeSubtree(mulNode);
    assertNotNull(result2);

    Node divNode = new Node(Token.DIV, IR.number(6), IR.number(2));
    Node parent3 = IR.exprResult(divNode);
    Node result3 = folder.optimizeSubtree(divNode);
    assertNotNull(result3);

    Node modNode = new Node(Token.MOD, IR.number(5), IR.number(2));
    Node parent4 = IR.exprResult(modNode);
    Node result4 = folder.optimizeSubtree(modNode);
    assertNotNull(result4);
  }

  public void testTryFoldBitwiseOp() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node bitAnd = new Node(Token.BITAND, IR.number(5), IR.number(3));
    Node parent = IR.exprResult(bitAnd);
    Node result = folder.optimizeSubtree(bitAnd);
    assertNotNull(result);

    Node bitOr = new Node(Token.BITOR, IR.number(4), IR.number(2));
    Node parent2 = IR.exprResult(bitOr);
    Node result2 = folder.optimizeSubtree(bitOr);
    assertNotNull(result2);

    Node bitXor = new Node(Token.BITXOR, IR.number(5), IR.number(1));
    Node parent3 = IR.exprResult(bitXor);
    Node result3 = folder.optimizeSubtree(bitXor);
    assertNotNull(result3);
  }

  public void testTryFoldComparison() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node eqNode = new Node(Token.EQ, IR.number(1), IR.number(1));
    Node parent = IR.exprResult(eqNode);
    Node result = folder.optimizeSubtree(eqNode);
    assertNotNull(result);

    Node ltNode = new Node(Token.LT, IR.number(1), IR.number(2));
    Node parent2 = IR.exprResult(ltNode);
    Node result2 = folder.optimizeSubtree(ltNode);
    assertNotNull(result2);

    Node gtNode = new Node(Token.GT, IR.number(2), IR.number(1));
    Node parent3 = IR.exprResult(gtNode);
    Node result3 = folder.optimizeSubtree(gtNode);
    assertNotNull(result3);
  }

  public void testTryFoldUnaryNegAndBitNot() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node negNode = new Node(Token.NEG, IR.number(5));
    Node parent = IR.exprResult(negNode);
    Node result = folder.optimizeSubtree(negNode);
    assertNotNull(result);

    Node bitNotNode = new Node(Token.BITNOT, IR.number(5));
    Node parent2 = IR.exprResult(bitNotNode);
    Node result2 = folder.optimizeSubtree(bitNotNode);
    assertNotNull(result2);
  }

  public void testTryFoldObjectPropAccess() throws Throwable {
    PeepholeFoldConstants folder = new PeepholeFoldConstants(true);
    Node objLit = new Node(Token.OBJECTLIT, IR.propdef(IR.string("a"), IR.number(1)));
    Node getProp = new Node(Token.GETPROP, objLit, IR.string("a"));
    Node parent = IR.exprResult(getProp);
    Node result = folder.optimizeSubtree(getProp);
    assertNotNull(result);
  }
}