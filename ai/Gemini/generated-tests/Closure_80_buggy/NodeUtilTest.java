package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class NodeUtilTest {

  @Test
  public void testGetBooleanValue() throws Throwable {
    Node stringNode = Node.newString("hello");
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(stringNode));

    Node emptyStringNode = Node.newString("");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(emptyStringNode));

    Node numberNode = Node.newNumber(5.0);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(numberNode));

    Node zeroNumberNode = Node.newNumber(0.0);
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(zeroNumberNode));

    Node nullNode = new Node(Token.NULL);
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(nullNode));

    Node falseNode = new Node(Token.FALSE);
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(falseNode));

    Node trueNode = new Node(Token.TRUE);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(trueNode));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(voidNode));

    Node undefName = Node.newString(Token.NAME, "undefined");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(undefName));

    Node nanName = Node.newString(Token.NAME, "NaN");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(nanName));

    Node infName = Node.newString(Token.NAME, "Infinity");
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(infName));

    Node unknownName = Node.newString(Token.NAME, "someUnknownVar");
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getBooleanValue(unknownName));

    Node arrayLit = new Node(Token.ARRAYLIT);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(arrayLit));

    Node objectLit = new Node(Token.OBJECTLIT);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(objectLit));

    Node regexp = new Node(Token.REGEXP);
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(regexp));

    Node notNode = new Node(Token.NOT, Node.newNumber(0.0));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(notNode));
  }

  @Test
  public void testGetStringValue() throws Throwable {
    assertEquals("test", NodeUtil.getStringValue(Node.newString("test")));
    assertEquals("undefined", NodeUtil.getStringValue(Node.newString(Token.NAME, "undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals("NaN", NodeUtil.getStringValue(Node.newString(Token.NAME, "NaN")));
    assertNull(NodeUtil.getStringValue(Node.newString(Token.NAME, "other")));

    assertEquals("1", NodeUtil.getStringValue(Node.newNumber(1.0)));
    assertEquals("1.5", NodeUtil.getStringValue(Node.newNumber(1.5)));

    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID, Node.newNumber(0))));

    Node notNode = new Node(Token.NOT, Node.newString(""));
    assertEquals("true", NodeUtil.getStringValue(notNode));

    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
    assertEquals("a,b", NodeUtil.getStringValue(arrayLit));

    Node objLit = new Node(Token.OBJECTLIT);
    assertEquals("[object Object]", NodeUtil.getStringValue(objLit));

    Node unknownTypeNode = new Node(Token.DEBUGGER);
    assertNull(NodeUtil.getStringValue(unknownTypeNode));
  }

  @Test
  public void testGetNumberValue() throws Throwable {
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(new Node(Token.TRUE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.FALSE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.NULL)));
    assertEquals(Double.valueOf(42.0), NodeUtil.getNumberValue(Node.newNumber(42.0)));

    Node voidSafe = new Node(Token.VOID, Node.newNumber(1));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(voidSafe));

    Node voidSideEffect = new Node(Token.VOID, new Node(Token.INC, Node.newString(Token.NAME, "x")));
    assertNull(NodeUtil.getNumberValue(voidSideEffect));

    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(Node.newString(Token.NAME, "undefined")));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(Node.newString(Token.NAME, "NaN")));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(Node.newString(Token.NAME, "Infinity")));
    assertNull(NodeUtil.getNumberValue(Node.newString(Token.NAME, "unknown")));

    Node negInf = new Node(Token.NEG, Node.newString(Token.NAME, "Infinity"));
    assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), NodeUtil.getNumberValue(negInf));

    Node notNode = new Node(Token.NOT, Node.newString("abc"));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(notNode));

    assertEquals(Double.valueOf(123.0), NodeUtil.getNumberValue(Node.newString("123")));
    assertNull(NodeUtil.getNumberValue(new Node(Token.DEBUGGER)));
  }

  @Test
  public void testGetStringNumberValue() throws Throwable {
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue(""));
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue("   "));
    assertEquals(Double.valueOf(255.0), NodeUtil.getStringNumberValue("0xFF"));
    assertEquals(Double.valueOf(255.0), NodeUtil.getStringNumberValue("0Xff"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("0xZZ"));
    assertNull(NodeUtil.getStringNumberValue("-0xFF"));
    assertNull(NodeUtil.getStringNumberValue("+0X12"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
    assertNull(NodeUtil.getStringNumberValue("+infinity"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("notANumber"));
    assertEquals(Double.valueOf(456.0), NodeUtil.getStringNumberValue("  456  "));
  }

  @Test
  public void testIsStrWhiteSpaceChar() throws Throwable {
    assertTrue(NodeUtil.isStrWhiteSpaceChar(' '));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\n'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\r'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\t'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u00A0'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u000C'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u000B'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u2028'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u2029'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\uFEFF'));
    assertTrue(NodeUtil.isStrWhiteSpaceChar('\u2000')); // Space separator
    assertFalse(NodeUtil.isStrWhiteSpaceChar('a'));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("str")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(10)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));

    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NOT, Node.newString("str"))));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.VOID, Node.newNumber(0))));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NEG, Node.newNumber(1))));

    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "myVar")));

    assertFalse(NodeUtil.isImmutableValue(new Node(Token.DEBUGGER)));
  }

  @Test
  public void testIsLiteralValue() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newString("a"));
    assertTrue(NodeUtil.isLiteralValue(arrayLit, false));

    Node badArrayLit = new Node(Token.ARRAYLIT, Node.newString(Token.NAME, "x"));
    assertFalse(NodeUtil.isLiteralValue(badArrayLit, false));

    Node keyNode = Node.newString("a");
    Node valNode = Node.newNumber(1);
    keyNode.addChildToBack(valNode);
    Node objLit = new Node(Token.OBJECTLIT, keyNode);
    assertTrue(NodeUtil.isLiteralValue(objLit, false));

    Node badObjVal = Node.newString(Token.NAME, "x");
    Node badKeyNode = Node.newString("b");
    badKeyNode.addChildToBack(badObjVal);
    Node badObjLit = new Node(Token.OBJECTLIT, badKeyNode);
    assertFalse(NodeUtil.isLiteralValue(badObjLit, false));

    Node funcNode = new Node(Token.FUNCTION);
    assertFalse(NodeUtil.isLiteralValue(funcNode, true));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("myDefine");

    assertTrue(NodeUtil.isValidDefineValue(Node.newString("str"), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isValidDefineValue(addNode, defines));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertTrue(NodeUtil.isValidDefineValue(notNode, defines));

    Node nameNode = Node.newString(Token.NAME, "myDefine");
    assertTrue(NodeUtil.isValidDefineValue(nameNode, defines));

    Node unknownName = Node.newString(Token.NAME, "other");
    assertFalse(NodeUtil.isValidDefineValue(unknownName, defines));

    assertFalse(NodeUtil.isValidDefineValue(new Node(Token.DEBUGGER), defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node block = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(new Node(Token.TRUE));
    assertFalse(NodeUtil.isEmptyBlock(block));

    Node notBlock = new Node(Token.TRUE);
    assertFalse(NodeUtil.isEmptyBlock(notBlock));
  }

  @Test
  public void testIsSimpleOperator() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));

    Node opNode = new Node(Token.ADD);
    assertTrue(NodeUtil.isSimpleOperator(opNode));
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
    Node newObj = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(newObj));

    Node newCustom = new Node(Token.NEW, Node.newString(Token.NAME, "CustomClass"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(newCustom));

    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    boolean exceptionThrown = false;
    try {
      NodeUtil.constructorCallHasSideEffects(callNode);
    } catch (IllegalStateException e) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node objCall = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(objCall));

    Node mathCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "Math"), Node.newString("floor")));
    assertFalse(NodeUtil.functionCallHasSideEffects(mathCall));

    Node toStringCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(""), Node.newString("toString")));
    assertFalse(NodeUtil.functionCallHasSideEffects(toStringCall));

    Node invalidCall = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    boolean exceptionThrown = false;
    try {
      NodeUtil.functionCallHasSideEffects(invalidCall);
    } catch (IllegalStateException e) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }

  @Test
  public void testCanBeSideEffected() throws Throwable {
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    assertTrue(NodeUtil.canBeSideEffected(callNode));

    Node nameNode = Node.newString(Token.NAME, "x");
    assertTrue(NodeUtil.canBeSideEffected(nameNode));

    Set<String> constants = new HashSet<String>();
    constants.add("x");
    assertFalse(NodeUtil.canBeSideEffected(nameNode, constants));

    Node getProp = new Node(Token.GETPROP, Node.newString(Token.NAME, "x"), Node.newString("p"));
    assertTrue(NodeUtil.canBeSideEffected(getProp));

    Node funcExpr = new Node(Token.FUNCTION);
    funcExpr.addChildToBack(Node.newString(Token.NAME, ""));
    funcExpr.addChildToBack(new Node(Token.LP));
    funcExpr.addChildToBack(new Node(Token.BLOCK));
    assertFalse(NodeUtil.canBeSideEffected(funcExpr));
  }

  @Test
  public void testPrecedence() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(5, NodeUtil.precedence(Token.BITOR));
    assertEquals(6, NodeUtil.precedence(Token.BITXOR));
    assertEquals(7, NodeUtil.precedence(Token.BITAND));
    assertEquals(8, NodeUtil.precedence(Token.EQ));
    assertEquals(9, NodeUtil.precedence(Token.LT));
    assertEquals(10, NodeUtil.precedence(Token.LSH));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(12, NodeUtil.precedence(Token.MUL));
    assertEquals(13, NodeUtil.precedence(Token.INC));
    assertEquals(15, NodeUtil.precedence(Token.NUMBER));

    boolean errorThrown = false;
    try {
      NodeUtil.precedence(-999);
    } catch (Error e) {
      errorThrown = true;
    }
    assertTrue(errorThrown);
  }

  @Test
  public void testIsNumericResult() throws Throwable {
    Node numNode = Node.newNumber(5);
    assertTrue(NodeUtil.isNumericResult(numNode));

    Node nanNode = Node.newString(Token.NAME, "NaN");
    assertTrue(NodeUtil.isNumericResult(nanNode));

    Node infNode = Node.newString(Token.NAME, "Infinity");
    assertTrue(NodeUtil.isNumericResult(nanNode));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isNumericResult(addNode));

    Node badAdd = new Node(Token.ADD, Node.newString("a"), Node.newString("b"));
    assertFalse(NodeUtil.isNumericResult(badAdd));
  }

  @Test
  public void testIsBooleanResult() throws Throwable {
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.EQ)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.NOT)));
    assertFalse(NodeUtil.isBooleanResult(Node.newNumber(1)));
  }

  @Test
  public void testIsUndefinedAndNull() throws Throwable {
    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isUndefined(voidNode));
    Node undefName = Node.newString(Token.NAME, "undefined");
    assertTrue(NodeUtil.isUndefined(undefName));
    assertFalse(NodeUtil.isUndefined(Node.newNumber(1)));

    Node nullNode = new Node(Token.NULL);
    assertTrue(NodeUtil.isNull(nullNode));
    assertFalse(NodeUtil.isNull(Node.newNumber(1)));

    assertTrue(NodeUtil.isNullOrUndefined(nullNode));
    assertTrue(NodeUtil.isNullOrUndefined(undefName));
    assertFalse(NodeUtil.isNullOrUndefined(Node.newNumber(1)));
  }

  @Test
  public void testMayBeString() throws Throwable {
    Node numNode = Node.newNumber(1);
    assertFalse(NodeUtil.mayBeString(numNode));

    Node strNode = Node.newString("hello");
    assertTrue(NodeUtil.mayBeString(strNode));
    assertTrue(NodeUtil.mayBeString(strNode, false));
  }

  @Test
  public void testIsAssociativeAndCommutative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.ADD));

    assertTrue(NodeUtil.isCommutative(Token.MUL));
    assertFalse(NodeUtil.isCommutative(Token.ADD));
  }

  @Test
  public void testAssignmentOps() throws Throwable {
    Node assign = new Node(Token.ASSIGN);
    assertTrue(NodeUtil.isAssignmentOp(assign));
    assertFalse(NodeUtil.isAssignmentOp(Node.newNumber(1)));

    Node assignAdd = new Node(Token.ASSIGN_ADD);
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(assignAdd));

    boolean errorThrown = false;
    try {
      NodeUtil.getOpFromAssignmentOp(Node.newNumber(1));
    } catch (IllegalArgumentException e) {
      errorThrown = true;
    }
    assertTrue(errorThrown);
  }

  @Test
  public void testNodeInspectors() throws Throwable {
    assertTrue(NodeUtil.isExpressionNode(new Node(Token.EXPR_RESULT)));
    assertTrue(NodeUtil.isGet(new Node(Token.GETPROP)));
    assertTrue(NodeUtil.isGet(new Node(Token.GETELEM)));
    assertTrue(NodeUtil.isGetProp(new Node(Token.GETPROP)));
    assertTrue(NodeUtil.isName(Node.newString(Token.NAME, "x")));
    assertTrue(NodeUtil.isNew(new Node(Token.NEW)));
    assertTrue(NodeUtil.isVar(new Node(Token.VAR)));
    assertTrue(NodeUtil.isString(Node.newString("")));
    assertTrue(NodeUtil.isAssign(new Node(Token.ASSIGN)));
    assertTrue(NodeUtil.isCall(new Node(Token.CALL)));
    assertTrue(NodeUtil.isCallOrNew(new Node(Token.CALL)));
    assertTrue(NodeUtil.isFunction(new Node(Token.FUNCTION)));
    assertTrue(NodeUtil.isThis(new Node(Token.THIS)));
    assertTrue(NodeUtil.isArrayLiteral(new Node(Token.ARRAYLIT)));
    assertTrue(NodeUtil.isSwitchCase(new Node(Token.CASE)));
    assertTrue(NodeUtil.isSwitchCase(new Node(Token.DEFAULT)));
    assertTrue(NodeUtil.isReferenceName(Node.newString(Token.NAME, "abc")));
    assertFalse(NodeUtil.isReferenceName(Node.newString(Token.NAME, "")));
    assertTrue(NodeUtil.isLabelName(new Node(Token.LABEL_NAME)));
    assertFalse(NodeUtil.isLabelName(null));
  }

  @Test
  public void testLoopAndControlStructures() throws Throwable {
    Node forIn = new Node(Token.FOR, new Node(Token.NAME, "x"), new Node(Token.IN), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isForIn(forIn));

    assertTrue(NodeUtil.isLoopStructure(new Node(Token.FOR)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.DO)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.WHILE)));
    assertFalse(NodeUtil.isLoopStructure(new Node(Token.IF)));

    assertNull(NodeUtil.getLoopCodeBlock(new Node(Token.IF)));
    assertNotNull(NodeUtil.getLoopCodeBlock(new Node(Token.WHILE, new Node(Token.TRUE), new Node(Token.BLOCK))));
    assertNotNull(NodeUtil.getLoopCodeBlock(new Node(Token.DO, new Node(Token.BLOCK), new Node(Token.TRUE))));

    assertTrue(NodeUtil.isControlStructure(new Node(Token.WITH)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.TRY)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.CATCH)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.SWITCH)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.DEFAULT)));

    Node ifNode = new Node(Token.IF, Node.newString("cond"), Node.newBlock(), Node.newBlock());
    assertEquals(ifNode.getFirstChild(), NodeUtil.getConditionExpression(ifNode));

    Node whileNode = new Node(Token.WHILE, Node.newString("cond"), Node.newBlock());
    assertEquals(whileNode.getFirstChild(), NodeUtil.getConditionExpression(whileNode));

    Node doNode = new Node(Token.DO, Node.newBlock(), Node.newString("cond"));
    assertEquals(doNode.getLastChild(), NodeUtil.getConditionExpression(doNode));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
    assertNull(NodeUtil.opToStr(-999));

    boolean errorThrown = false;
    try {
      NodeUtil.opToStrNoFail(-999);
    } catch (Error e) {
      errorThrown = true;
    }
    assertTrue(errorThrown);
  }

  @Test
  public void testLatinCheck() throws Throwable {
    assertTrue(NodeUtil.isLatin("abc123XYZ"));
    assertFalse(NodeUtil.isLatin("abc\u0100"));
  }

  @Test
  public void testValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isValidPropertyName("validName"));
    assertFalse(NodeUtil.isValidPropertyName("if")); // Keyword
    assertFalse(NodeUtil.isValidPropertyName("bad\u0100Name"));
  }

  @Test
  public void testNewNodeHelpers() throws Throwable {
    Node undef = NodeUtil.newUndefinedNode(null);
    assertEquals(Token.VOID, undef.getType());

    Node undefWithRef = NodeUtil.newUndefinedNode(Node.newNumber(1));
    assertEquals(Token.VOID, undefWithRef.getType());

    Node varNode = NodeUtil.newVarNode("x", Node.newNumber(1));
    assertEquals(Token.VAR, varNode.getType());

    List<Node> params = new ArrayList<Node>();
    params.add(Node.newString(Token.NAME, "p1"));
    Node fn = NodeUtil.newFunctionNode("fn", params, new Node(Token.BLOCK), 1, 1);
    assertEquals(Token.FUNCTION, fn.getType());
  }

  @Test
  public void testGetSourceName() throws Throwable {
    Node node = Node.newNumber(1);
    node.putProp(Node.SOURCENAME_PROP, "testfile.js");
    assertEquals("testfile.js", NodeUtil.getSourceName(node));

    Node parent = new Node(Token.BLOCK, node);
    assertEquals("testfile.js", NodeUtil.getSourceName(node));
  }

}