package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class NodeUtilTest {

  @Test
  public void testGetBooleanValue() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.TRUE)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.FALSE)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.NULL)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.VOID)));
    
    Node strNode = Node.newString("hello");
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(strNode));

    Node emptyStrNode = Node.newString("");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(emptyStrNode));

    Node numNode = Node.newNumber(5.0);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(numNode));

    Node zeroNumNode = Node.newNumber(0.0);
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(zeroNumNode));

    Node nameUndefined = Node.newString(Token.NAME, "undefined");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(nameUndefined));

    Node nameNaN = Node.newString(Token.NAME, "NaN");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(nameNaN));

    Node nameInfinity = Node.newString(Token.NAME, "Infinity");
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(nameInfinity));

    Node nameOther = Node.newString(Token.NAME, "someName");
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getBooleanValue(nameOther));

    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.ARRAYLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.OBJECTLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.REGEXP)));
  }

  @Test
  public void testGetStringValue() throws Throwable {
    assertEquals("hello", NodeUtil.getStringValue(Node.newString("hello")));
    assertEquals("undefined", NodeUtil.getStringValue(Node.newString(Token.NAME, "undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals("NaN", NodeUtil.getStringValue(Node.newString(Token.NAME, "NaN")));
    assertNull(NodeUtil.getStringValue(Node.newString(Token.NAME, "other")));

    assertEquals("1", NodeUtil.getStringValue(Node.newNumber(1.0)));
    assertEquals("1.5", NodeUtil.getStringValue(Node.newNumber(1.5)));

    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID)));

    assertNull(NodeUtil.getStringValue(new Node(Token.BLOCK)));
  }

  @Test
  public void testGetNumberValue() throws Throwable {
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(new Node(Token.TRUE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.FALSE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.NULL)));
    assertEquals(Double.valueOf(5.5), NodeUtil.getNumberValue(Node.newNumber(5.5)));
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(new Node(Token.VOID))));
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(Node.newString(Token.NAME, "undefined"))));
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(Node.newString(Token.NAME, "NaN"))));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(Node.newString(Token.NAME, "Infinity")));
    assertNull(NodeUtil.getNumberValue(Node.newString(Token.NAME, "unknown")));
    assertNull(NodeUtil.getNumberValue(new Node(Token.BLOCK)));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("abc")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(10)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isImmutableValue(voidNode));

    Node negNode = new Node(Token.NEG, Node.newNumber(5));
    assertTrue(NodeUtil.isImmutableValue(negNode));

    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "foo")));
    assertFalse(NodeUtil.isImmutableValue(new Node(Token.BLOCK)));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node block = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(Node.newNumber(1));
    assertFalse(NodeUtil.isEmptyBlock(block));

    Node notBlock = new Node(Token.EXPR_RESULT);
    assertFalse(NodeUtil.isEmptyBlock(notBlock));
  }

  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.MUL));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.DIV));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.NOT));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.FUNCTION));
  }

  @Test
  public void testNewExpr() throws Throwable {
    Node child = Node.newNumber(1);
    Node expr = NodeUtil.newExpr(child);
    assertEquals(Token.EXPR_RESULT, expr.getType());
    assertEquals(child, expr.getFirstChild());
  }

  @Test
  public void testConstructorCallHasSideEffects() throws Throwable {
    Node newCall = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(newCall));

    Node customNew = new Node(Token.NEW, Node.newString(Token.NAME, "CustomCtor"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(customNew));

    Node noEffectsNew = new Node(Token.NEW, Node.newString(Token.NAME, "CustomCtor"));
    noEffectsNew.setNoSideEffectsCall();
    assertFalse(NodeUtil.constructorCallHasSideEffects(noEffectsNew));
  }

  @Test(expected = IllegalStateException.class)
  public void testConstructorCallHasSideEffectsException() throws Throwable {
    Node notNew = new Node(Token.CALL);
    NodeUtil.constructorCallHasSideEffects(notNew);
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(call));

    Node customCall = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    assertTrue(NodeUtil.functionCallHasSideEffects(customCall));

    Node noEffectsCall = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    noEffectsCall.setNoSideEffectsCall();
    assertFalse(NodeUtil.functionCallHasSideEffects(noEffectsCall));

    Node mathCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "Math"), Node.newString(Token.STRING, "floor")));
    assertFalse(NodeUtil.functionCallHasSideEffects(mathCall));
  }

  @Test(expected = IllegalStateException.class)
  public void testFunctionCallHasSideEffectsException() throws Throwable {
    Node notCall = new Node(Token.NEW);
    NodeUtil.functionCallHasSideEffects(notCall);
  }

  @Test
  public void testPrecedence() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(12, NodeUtil.precedence(Token.MUL));
    assertEquals(15, NodeUtil.precedence(Token.TRUE));
  }

  @Test(expected = Error.class)
  public void testPrecedenceUnknown() throws Throwable {
    NodeUtil.precedence(-999);
  }

  @Test
  public void testIsAssociative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertTrue(NodeUtil.isAssociative(Token.AND));
    assertTrue(NodeUtil.isAssociative(Token.OR));
    assertFalse(NodeUtil.isAssociative(Token.ADD));
  }

  @Test
  public void testIsCommutative() throws Throwable {
    assertTrue(NodeUtil.isCommutative(Token.MUL));
    assertTrue(NodeUtil.isCommutative(Token.BITOR));
    assertTrue(NodeUtil.isCommutative(Token.BITAND));
    assertFalse(NodeUtil.isCommutative(Token.ADD));
  }

  @Test
  public void testIsAssignmentOp() throws Throwable {
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN)));
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN_ADD)));
    assertFalse(NodeUtil.isAssignmentOp(new Node(Token.ADD)));
  }

  @Test
  public void testGetOpFromAssignmentOp() throws Throwable {
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_ADD)));
    assertEquals(Token.SUB, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_SUB)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetOpFromAssignmentOpInvalid() throws Throwable {
    NodeUtil.getOpFromAssignmentOp(new Node(Token.ADD));
  }

  @Test
  public void testIsGetAndNameAndNewAndVar() throws Throwable {
    Node getProp = new Node(Token.GETPROP);
    Node getElem = new Node(Token.GETELEM);
    Node name = new Node(Token.NAME);
    Node newNode = new Node(Token.NEW);
    Node varNode = new Node(Token.VAR);
    Node strNode = new Node(Token.STRING);

    assertTrue(NodeUtil.isGet(getProp));
    assertTrue(NodeUtil.isGet(getElem));
    assertFalse(NodeUtil.isGet(name));

    assertTrue(NodeUtil.isGetProp(getProp));
    assertFalse(NodeUtil.isGetProp(getElem));

    assertTrue(NodeUtil.isName(name));
    assertFalse(NodeUtil.isName(getProp));

    assertTrue(NodeUtil.isNew(newNode));
    assertFalse(NodeUtil.isNew(name));

    assertTrue(NodeUtil.isVar(varNode));
    assertFalse(NodeUtil.isVar(name));

    assertTrue(NodeUtil.isString(strNode));
    assertFalse(NodeUtil.isString(name));
  }

  @Test
  public void testIsVarDeclaration() throws Throwable {
    Node var = new Node(Token.VAR);
    Node name = new Node(Token.NAME, Node.newString("a"));
    var.addChildToBack(name);

    assertTrue(NodeUtil.isVarDeclaration(name));
    assertFalse(NodeUtil.isVarDeclaration(var));
  }

  @Test
  public void testGetAssignedValue() throws Throwable {
    Node var = new Node(Token.VAR);
    Node name = new Node(Token.NAME, Node.newString("a"));
    Node val = Node.newNumber(1);
    name.addChildToBack(val);
    var.addChildToBack(name);

    assertEquals(val, NodeUtil.getAssignedValue(name));

    Node assign = new Node(Token.ASSIGN, name, Node.newNumber(2));
    assertNull(NodeUtil.getAssignedValue(name));
  }

  @Test
  public void testIsForInAndLoopStructure() throws Throwable {
    Node forNode = new Node(Token.FOR);
    forNode.addChildToBack(new Node(Token.NAME));
    forNode.addChildToBack(new Node(Token.IN));
    forNode.addChildToBack(new Node(Token.BLOCK));

    assertTrue(NodeUtil.isForIn(forNode));
    assertTrue(NodeUtil.isLoopStructure(forNode));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.DO)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.WHILE)));
    assertFalse(NodeUtil.isLoopStructure(new Node(Token.IF)));
  }

  @Test
  public void testGetLoopCodeBlock() throws Throwable {
    Node whileNode = new Node(Token.WHILE, Node.newNumber(1), new Node(Token.BLOCK));
    assertNotNull(NodeUtil.getLoopCodeBlock(whileNode));

    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), Node.newNumber(1));
    assertNotNull(NodeUtil.getLoopCodeBlock(doNode));

    assertNull(NodeUtil.getLoopCodeBlock(new Node(Token.IF)));
  }

  @Test
  public void testIsControlStructure() throws Throwable {
    assertTrue(NodeUtil.isControlStructure(new Node(Token.IF)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.SWITCH)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.TRY)));
    assertFalse(NodeUtil.isControlStructure(new Node(Token.ADD)));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("-", NodeUtil.opToStr(Token.SUB));
    assertEquals("===", NodeUtil.opToStr(Token.SHEQ));
    assertNull(NodeUtil.opToStr(Token.FUNCTION));
  }

  @Test
  public void testOpToStrNoFail() throws Throwable {
    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
  }

  @Test(expected = Error.class)
  public void testOpToStrNoFailError() throws Throwable {
    NodeUtil.opToStrNoFail(Token.FUNCTION);
  }

  @Test
  public void testIsLatin() throws Throwable {
    assertTrue(NodeUtil.isLatin("abc123XYZ"));
    assertFalse(NodeUtil.isLatin("abc\u0100de"));
  }

  @Test
  public void testIsValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isValidPropertyName("foo"));
    assertFalse(NodeUtil.isValidPropertyName("if"));
    assertFalse(NodeUtil.isValidPropertyName("foo\u0100"));
  }

  @Test
  public void testNewUndefinedNode() throws Throwable {
    Node undef = NodeUtil.newUndefinedNode(null);
    assertEquals(Token.VOID, undef.getType());
    
    Node ref = Node.newNumber(1);
    Node undefWithRef = NodeUtil.newUndefinedNode(ref);
    assertEquals(Token.VOID, undefWithRef.getType());
  }

  @Test
  public void testNewVarNode() throws Throwable {
    Node varNode = NodeUtil.newVarNode("x", Node.newNumber(5));
    assertEquals(Token.VAR, varNode.getType());
    assertEquals("x", varNode.getFirstChild().getString());
    
    Node varNodeNoVal = NodeUtil.newVarNode("y", null);
    assertEquals(Token.VAR, varNodeNoVal.getType());
  }

  @Test
  public void testHasFinallyAndCatchBlock() throws Throwable {
    Node tryNode = new Node(Token.TRY, new Node(Token.BLOCK), new Node(Token.BLOCK), new Node(Token.BLOCK));
    assertTrue(NodeUtil.hasFinally(tryNode));
    assertNotNull(NodeUtil.getCatchBlock(tryNode));
    
    Node catchBlock = new Node(Token.BLOCK, new Node(Token.CATCH));
    assertTrue(NodeUtil.hasCatchHandler(catchBlock));
  }

  @Test
  public void testEvaluateToLocalValue() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newNumber(1)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.REGEXP)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.ARRAYLIT)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.OBJECTLIT)));
  }

  @Test
  public void testGetFunctionName() throws Throwable {
    Node fn = new Node(Token.FUNCTION, Node.newString(Token.NAME, "myFunc"), new Node(Token.LP), new Node(Token.BLOCK));
    Node nameParent = new Node(Token.NAME, fn);
    nameParent.setString("varName");

    assertEquals("varName", NodeUtil.getFunctionName(fn));
  }

  @Test
  public void testMatchPredicates() throws Throwable {
    NodeUtil.MatchNodeType matchType = new NodeUtil.MatchNodeType(Token.ADD);
    assertTrue(matchType.apply(new Node(Token.ADD)));
    assertFalse(matchType.apply(new Node(Token.SUB)));

    NodeUtil.MatchDeclaration matchDecl = new NodeUtil.MatchDeclaration();
    assertTrue(matchDecl.apply(new Node(Token.VAR)));
    assertFalse(matchDecl.apply(new Node(Token.ADD)));

    NodeUtil.MatchNotFunction matchNotFn = new NodeUtil.MatchNotFunction();
    assertTrue(matchNotFn.apply(new Node(Token.ADD)));
    assertFalse(matchNotFn.apply(new Node(Token.FUNCTION)));
  }

}