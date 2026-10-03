package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    Node undefName = Node.newString(Token.NAME, "undefined");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(undefName));

    Node nanName = Node.newString(Token.NAME, "NaN");
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(nanName));

    Node infName = Node.newString(Token.NAME, "Infinity");
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(infName));

    Node otherName = Node.newString(Token.NAME, "foo");
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getBooleanValue(otherName));

    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.ARRAYLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.OBJECTLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.REGEXP)));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getBooleanValue(new Node(Token.DEBUGGER)));
  }

  @Test
  public void testGetStringValue() throws Throwable {
    assertEquals("hello", NodeUtil.getStringValue(Node.newString("hello")));
    assertEquals("name", NodeUtil.getStringValue(Node.newString(Token.NAME, "name")));

    assertEquals("1", NodeUtil.getStringValue(Node.newNumber(1.0)));
    assertEquals("1.5", NodeUtil.getStringValue(Node.newNumber(1.5)));

    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID)));

    assertNull(NodeUtil.getStringValue(new Node(Token.DEBUGGER)));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("a")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(1.0)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isImmutableValue(voidNode));

    Node negNode = new Node(Token.NEG, Node.newNumber(1));
    assertTrue(NodeUtil.isImmutableValue(negNode));

    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "notConst")));

    assertFalse(NodeUtil.isImmutableValue(new Node(Token.DEBUGGER)));
  }

  @Test
  public void testIsLiteralValue() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1));
    assertTrue(NodeUtil.isLiteralValue(arrayLit, false));

    Node objLit = new Node(Token.OBJECTLIT, Node.newString("key"), Node.newNumber(1));
    assertTrue(NodeUtil.isLiteralValue(objLit, false));

    Node regexp = new Node(Token.REGEXP, Node.newString("abc"));
    assertTrue(NodeUtil.isLiteralValue(regexp, false));

    Node invalidChildLit = new Node(Token.ARRAYLIT, Node.newString(Token.NAME, "a"));
    assertFalse(NodeUtil.isLiteralValue(invalidChildLit, false));

    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    assertFalse(NodeUtil.isLiteralValue(func, true));

    Node funcExpr = new Node(Token.FUNCTION, Node.newString(Token.NAME, ""), new Node(Token.LP), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isLiteralValue(funcExpr, true));

    assertTrue(NodeUtil.isLiteralValue(Node.newNumber(10), false));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("myDefine");

    assertTrue(NodeUtil.isValidDefineValue(Node.newString("str"), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1.0), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node bitand = new Node(Token.BITAND, Node.newNumber(1), Node.newNumber(1));
    assertTrue(NodeUtil.isValidDefineValue(bitand, defines));

    Node not = new Node(Token.NOT, new Node(Token.TRUE));
    assertTrue(NodeUtil.isValidDefineValue(not, defines));

    Node nameDef = Node.newString(Token.NAME, "myDefine");
    assertTrue(NodeUtil.isValidDefineValue(nameDef, defines));

    Node nameNotDef = Node.newString(Token.NAME, "other");
    assertFalse(NodeUtil.isValidDefineValue(nameNotDef, defines));

    assertFalse(NodeUtil.isValidDefineValue(new Node(Token.DEBUGGER), defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node emptyBlock = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(emptyBlock));

    Node blockWithEmpty = new Node(Token.BLOCK, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(blockWithEmpty));

    Node blockWithStmt = new Node(Token.BLOCK, new Node(Token.TRUE));
    assertFalse(NodeUtil.isEmptyBlock(blockWithStmt));

    Node notBlock = new Node(Token.TRUE);
    assertFalse(NodeUtil.isEmptyBlock(notBlock));
  }

  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.BITAND));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.FUNCTION));
  }

  @Test
  public void testNewExpr() throws Throwable {
    Node exprChild = new Node(Token.TRUE);
    Node expr = NodeUtil.newExpr(exprChild);
    assertEquals(Token.EXPR_RESULT, expr.getType());
    assertEquals(exprChild, expr.getFirstChild());
  }

  @Test
  public void testConstructorCallHasSideEffects() throws Throwable {
    Node callNoSideEffect = new Node(Token.NEW, Node.newString(Token.NAME, "Array"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(callNoSideEffect));

    Node callSideEffect = new Node(Token.NEW, Node.newString(Token.NAME, "CustomClass"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(callSideEffect));

    callSideEffect.putBooleanProp(Node.NO_SIDE_EFFECTS_CALL, true);
    assertFalse(NodeUtil.constructorCallHasSideEffects(callSideEffect));

    try {
      NodeUtil.constructorCallHasSideEffects(new Node(Token.CALL));
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Expected NEW node"));
    }
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node callNoSideEffect = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callNoSideEffect));

    Node callSideEffect = new Node(Token.CALL, Node.newString(Token.NAME, "customFunc"));
    assertTrue(NodeUtil.functionCallHasSideEffects(callSideEffect));

    callSideEffect.putBooleanProp(Node.NO_SIDE_EFFECTS_CALL, true);
    assertFalse(NodeUtil.functionCallHasSideEffects(callSideEffect));

    Node mathCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "Math"), Node.newString(Token.STRING, "floor")));
    assertFalse(NodeUtil.functionCallHasSideEffects(mathCall));

    try {
      NodeUtil.functionCallHasSideEffects(new Node(Token.NEW));
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Expected CALL node"));
    }
  }

  @Test
  public void testCallHasLocalResult() throws Throwable {
    Node call = new Node(Token.CALL);
    call.setSideEffectFlags(Node.FLAG_LOCAL_RESULTS);
    assertTrue(NodeUtil.callHasLocalResult(call));
  }

  @Test
  public void testNodeTypeMayHaveSideEffects() throws Throwable {
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(new Node(Token.ASSIGN)));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(new Node(Token.THROW)));
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(new Node(Token.TRUE)));
    
    Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "a"));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(varNode));

    Node emptyVar = new Node(Token.VAR);
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(emptyVar));
  }

  @Test
  public void testCanBeSideEffected() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    assertTrue(NodeUtil.canBeSideEffected(nameNode));

    Set<String> constants = new HashSet<String>();
    constants.add("x");
    assertFalse(NodeUtil.canBeSideEffected(nameNode, constants));

    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "f"));
    assertTrue(NodeUtil.canBeSideEffected(callNode));

    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, ""), new Node(Token.LP), new Node(Token.BLOCK));
    assertFalse(NodeUtil.canBeSideEffected(func));
  }

  @Test
  public void testPrecedence() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(15, NodeUtil.precedence(Token.TRUE));

    try {
      NodeUtil.precedence(-999);
      fail("Expected Error");
    } catch (Error e) {
      assertTrue(e.getMessage().contains("Unknown precedence"));
    }
  }

  @Test
  public void testIsAssociative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertTrue(NodeUtil.isAssociative(Token.AND));
    assertTrue(NodeUtil.isAssociative(Token.OR));
    assertTrue(NodeUtil.isAssociative(Token.BITOR));
    assertTrue(NodeUtil.isAssociative(Token.BITAND));
    assertFalse(NodeUtil.isAssociative(Token.ADD));
  }

  @Test
  public void testAssignmentOps() throws Throwable {
    Node assign = new Node(Token.ASSIGN);
    assertTrue(NodeUtil.isAssignmentOp(assign));
    assertFalse(NodeUtil.isAssignmentOp(new Node(Token.ADD)));

    Node assignAdd = new Node(Token.ASSIGN_ADD);
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(assignAdd));

    try {
      NodeUtil.getOpFromAssignmentOp(new Node(Token.ADD));
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Not an assiment op"));
    }
  }

  @Test
  public void testContainsFunctionAndReferencesThis() throws Throwable {
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    assertTrue(NodeUtil.containsFunction(func));
    assertFalse(NodeUtil.containsFunction(new Node(Token.TRUE)));

    Node thisNode = new Node(Token.THIS);
    assertTrue(NodeUtil.referencesThis(thisNode));
    assertFalse(NodeUtil.referencesThis(func));
  }

  @Test
  public void testGetAssignedValue() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "a");
    Node varNode = new Node(Token.VAR, nameNode);
    Node valNode = Node.newNumber(1);
    nameNode.addChildToBack(valNode);

    assertEquals(valNode, NodeUtil.getAssignedValue(nameNode));

    Node assign = new Node(Token.ASSIGN, nameNode, valNode);
    assertNull(NodeUtil.getAssignedValue(nameNode)); // parent is ASSIGN, but nameNode is last child
  }

  @Test
  public void testLoopAndControlStructures() throws Throwable {
    Node forNode = new Node(Token.FOR, new Node(Token.EMPTY), new Node(Token.EMPTY), new Node(Token.EMPTY));
    assertTrue(NodeUtil.isLoopStructure(forNode));
    assertFalse(NodeUtil.isLoopStructure(new Node(Token.IF)));

    Node whileNode = new Node(Token.WHILE, new Node(Token.TRUE), new Node(Token.BLOCK));
    assertEquals(whileNode.getLastChild(), NodeUtil.getLoopCodeBlock(whileNode));

    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), new Node(Token.TRUE));
    assertEquals(doNode.getFirstChild(), NodeUtil.getLoopCodeBlock(doNode));
    assertNull(NodeUtil.getLoopCodeBlock(new Node(Token.TRUE)));

    assertTrue(NodeUtil.isControlStructure(new Node(Token.IF)));
    assertFalse(NodeUtil.isControlStructure(new Node(Token.TRUE)));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertNull(NodeUtil.opToStr(-999));

    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
    try {
      NodeUtil.opToStrNoFail(-999);
      fail("Expected Error");
    } catch (Error e) {
      assertTrue(e.getMessage().contains("Unknown op"));
    }
  }

  @Test
  public void testIsLatin() throws Throwable {
    assertTrue(NodeUtil.isLatin("abc123XYZ"));
    assertFalse(NodeUtil.isLatin("abc\u0100xyz"));
  }

  @Test
  public void testIsValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isValidPropertyName("validProp"));
    assertFalse(NodeUtil.isValidPropertyName("if")); // keyword
    assertFalse(NodeUtil.isValidPropertyName("invalid\u0100prop"));
  }

  @Test
  public void testPrototypeHelpers() throws Throwable {
    Node qName = NodeUtil.newQualifiedNameNode("A.prototype.b", 1, 1);
    assertTrue(NodeUtil.isPrototypeProperty(qName));
    assertEquals("b", NodeUtil.getPrototypePropertyName(qName));
    assertNotNull(NodeUtil.getPrototypeClassName(qName));

    Node notProto = NodeUtil.newQualifiedNameNode("A.b", 1, 1);
    assertFalse(NodeUtil.isPrototypeProperty(notProto));
    assertNull(NodeUtil.getPrototypeClassName(notProto));
  }

  @Test
  public void testNewUndefinedNode() throws Throwable {
    Node undef = NodeUtil.newUndefinedNode(null);
    assertEquals(Token.VOID, undef.getType());

    Node undefWithSrc = NodeUtil.newUndefinedNode(Node.newNumber(1));
    assertEquals(Token.VOID, undefWithSrc.getType());
  }

  @Test
  public void testNewVarNode() throws Throwable {
    Node var = NodeUtil.newVarNode("x", Node.newNumber(1));
    assertEquals(Token.VAR, var.getType());
    assertEquals("x", var.getFirstChild().getString());
  }

  @Test
  public void testEvaluatesToLocalValue() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newNumber(1)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.NEW, Node.newString(Token.NAME, "Object"))));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.ARRAYLIT)));

    Node assign = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"), Node.newNumber(1));
    assertTrue(NodeUtil.evaluatesToLocalValue(assign));
  }

}