package com.google.javascript.jscomp;

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
  public void testGetStringValueDouble() throws Throwable {
    assertEquals("1", NodeUtil.getStringValue(1.0));
    assertEquals("1.5", NodeUtil.getStringValue(1.5));
    assertEquals("0", NodeUtil.getStringValue(0.0));
  }

  @Test
  public void testTrimJsWhiteSpace() throws Throwable {
    assertEquals("abc", NodeUtil.trimJsWhiteSpace("  abc \t\n\r"));
    assertEquals("", NodeUtil.trimJsWhiteSpace("   "));
    assertEquals("", NodeUtil.trimJsWhiteSpace(""));
  }

  @Test
  public void testIsStrWhiteSpaceChar() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar(' '));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\t'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\n'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\r'));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.isStrWhiteSpaceChar('\u000B'));
    assertEquals(TernaryValue.FALSE, NodeUtil.isStrWhiteSpaceChar('a'));
  }

  @Test
  public void testGetNumberValue() throws Throwable {
    Node trueNode = new Node(Token.TRUE);
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(trueNode));

    Node falseNode = new Node(Token.FALSE);
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(falseNode));

    Node nullNode = new Node(Token.NULL);
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(nullNode));

    Node numNode = Node.newNumber(42.5);
    assertEquals(Double.valueOf(42.5), NodeUtil.getNumberValue(numNode));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(voidNode));

    Node nanNameNode = Node.newString(Token.NAME, "NaN");
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(nanNameNode));

    Node infNameNode = Node.newString(Token.NAME, "Infinity");
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(infNameNode));

    Node undefNameNode = Node.newString(Token.NAME, "undefined");
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(undefNameNode));

    Node unknownNameNode = Node.newString(Token.NAME, "unknownVar");
    assertNull(NodeUtil.getNumberValue(unknownNameNode));

    Node negInfNode = new Node(Token.NEG, Node.newString(Token.NAME, "Infinity"));
    assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), NodeUtil.getNumberValue(negInfNode));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(notNode));

    Node strNode = Node.newString("123");
    assertEquals(Double.valueOf(123.0), NodeUtil.getNumberValue(strNode));
  }

  @Test
  public void testGetStringNumberValue() throws Throwable {
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue(""));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0xf"));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0XF"));
    assertNull(NodeUtil.getStringNumberValue("0x123\u000b"));
    assertNull(NodeUtil.getStringNumberValue("-0x12"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
    assertNull(NodeUtil.getStringNumberValue("+infinity"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("notANumber"));
  }

  @Test
  public void testGetPureBooleanValue() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newString("hello")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString("")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newNumber(5.0)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newNumber(0.0)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.NULL)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.FALSE)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.VOID)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.TRUE)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.REGEXP)));
    
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "undefined")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "NaN")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "someVar")));
  }

  @Test
  public void testGetImpureBooleanValue() throws Throwable {
    Node assignNode = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"), Node.newString("val"));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(assignNode));

    Node commaNode = new Node(Token.COMMA, Node.newNumber(1), Node.newNumber(0));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(commaNode));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(notNode));

    Node andNode = new Node(Token.AND, new Node(Token.TRUE), new Node(Token.FALSE));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(andNode));

    Node orNode = new Node(Token.OR, new Node(Token.FALSE), new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(orNode));

    Node hookNodeEqual = new Node(Token.HOOK, new Node(Token.TRUE), Node.newString("a"), Node.newString("b"));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(hookNodeEqual));
  }

  @Test
  public void testGetStringValue() throws Throwable {
    assertEquals("abc", NodeUtil.getStringValue(Node.newString("abc")));
    assertEquals("undefined", NodeUtil.getStringValue(Node.newString(Token.NAME, "undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals("NaN", NodeUtil.getStringValue(Node.newString(Token.NAME, "NaN")));
    assertNull(NodeUtil.getStringValue(Node.newString(Token.NAME, "other")));
    assertEquals("5", NodeUtil.getStringValue(Node.newNumber(5.0)));
    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID)));
    assertEquals("[object Object]", NodeUtil.getStringValue(new Node(Token.OBJECTLIT)));
  }

  @Test
  public void testGetArrayElementStringValue() throws Throwable {
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.NULL)));
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.EMPTY)));
    assertEquals("hello", NodeUtil.getArrayElementStringValue(Node.newString("hello")));
  }

  @Test
  public void testArrayToString() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newNumber(1), new Node(Token.TRUE));
    assertEquals("a,1,true", NodeUtil.arrayToString(arrayLit));

    Node invalidArrayLit = new Node(Token.ARRAYLIT, new Node(Token.FUNCTION));
    assertNull(NodeUtil.arrayToString(invalidArrayLit));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("test")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(10)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "mutable")));
  }

  @Test
  public void testIsLiteralValue() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), new Node(Token.EMPTY));
    assertTrue(NodeUtil.isLiteralValue(arrayLit, false));

    Node regexpNode = new Node(Token.REGEXP, Node.newString("abc"));
    assertTrue(NodeUtil.isLiteralValue(regexpNode, false));

    Node objLitKey = Node.newString("key");
    Node objLitVal = Node.newNumber(123);
    objLitKey.addChildToBack(objLitVal);
    Node objLit = new Node(Token.OBJECTLIT, objLitKey);
    assertTrue(NodeUtil.isLiteralValue(objLit, false));

    Node funcNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    assertFalse(NodeUtil.isLiteralValue(funcNode, true));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("MY_DEFINE");

    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newString("a"), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isValidDefineValue(addNode, defines));

    Node negNode = new Node(Token.NEG, Node.newNumber(1));
    assertTrue(NodeUtil.isValidDefineValue(negNode, defines));

    Node nameNode = Node.newQualifiedNameNode(new DefaultCodingConvention(), "MY_DEFINE", -1, -1);
    assertTrue(NodeUtil.isValidDefineValue(nameNode, defines));

    Node invalidNameNode = Node.newQualifiedNameNode(new DefaultCodingConvention(), "OTHER_DEFINE", -1, -1);
    assertFalse(NodeUtil.isValidDefineValue(invalidNameNode, defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node emptyBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(emptyBlock));

    Node nonBlock = new Node(Token.EMPTY);
    assertFalse(NodeUtil.isEmptyBlock(nonBlock));

    Node nonEmptyBlock = new Node(Token.BLOCK, Node.newNumber(1));
    assertFalse(NodeUtil.isEmptyBlock(nonEmptyBlock));
  }

  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.MUL));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.DIV));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
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

    Node newCustom = new Node(Token.NEW, Node.newString(Token.NAME, "CustomCtor"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(newCustom));
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node callObj = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callObj));

    Node getProp = new Node(Token.GETPROP, Node.newString(Token.NAME, "a"), Node.newString("toString"));
    Node callToString = new Node(Token.CALL, getProp);
    assertFalse(NodeUtil.functionCallHasSideEffects(callToString));
  }

  @Test
  public void testPrecedence() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(15, NodeUtil.precedence(Token.TRUE));
  }

  @Test(expected = Error.class)
  public void testPrecedenceUnknown() throws Throwable {
    NodeUtil.precedence(-999);
  }

  @Test
  public void testIsNumericResult() throws Throwable {
    Node num = Node.newNumber(5);
    assertTrue(NodeUtil.isNumericResult(num));

    Node add = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isNumericResult(add));

    Node strAdd = new Node(Token.ADD, Node.newString("a"), Node.newString("b"));
    assertFalse(NodeUtil.isNumericResult(strAdd));
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
    assertTrue(NodeUtil.isNullOrUndefined(voidNode));

    Node nullNode = new Node(Token.NULL);
    assertTrue(NodeUtil.isNull(nullNode));
    assertTrue(NodeUtil.isNullOrUndefined(nullNode));

    Node undefName = Node.newString(Token.NAME, "undefined");
    assertTrue(NodeUtil.isUndefined(undefName));
  }

  @Test
  public void testMayBeString() throws Throwable {
    Node num = Node.newNumber(1);
    assertFalse(NodeUtil.mayBeString(num));

    Node str = Node.newString("abc");
    assertTrue(NodeUtil.mayBeString(str));
  }

  @Test
  public void testIsAssociativeAndCommutative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertTrue(NodeUtil.isAssociative(Token.AND));
    assertFalse(NodeUtil.isAssociative(Token.ADD));

    assertTrue(NodeUtil.isCommutative(Token.MUL));
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
    assertEquals(Token.MUL, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_MUL)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetOpFromAssignmentOpInvalid() throws Throwable {
    NodeUtil.getOpFromAssignmentOp(new Node(Token.ADD));
  }

  @Test
  public void testNodeQueryHelpers() throws Throwable {
    Node exprRes = new Node(Token.EXPR_RESULT);
    assertTrue(NodeUtil.isExpressionNode(exprRes));

    Node getProp = new Node(Token.GETPROP);
    Node getElem = new Node(Token.GETELEM);
    assertTrue(NodeUtil.isGet(getProp));
    assertTrue(NodeUtil.isGetProp(getProp));
    assertFalse(NodeUtil.isGetProp(getElem));

    Node nameNode = new Node(Token.NAME);
    assertTrue(NodeUtil.isName(nameNode));

    Node newNode = new Node(Token.NEW);
    assertTrue(NodeUtil.isNew(newNode));

    Node varNode = new Node(Token.VAR);
    assertTrue(NodeUtil.isVar(varNode));

    Node strNode = new Node(Token.STRING);
    assertTrue(NodeUtil.isString(strNode));

    Node assignNode = new Node(Token.ASSIGN);
    assertTrue(NodeUtil.isAssign(assignNode));

    Node funcNode = new Node(Token.FUNCTION);
    assertTrue(NodeUtil.isFunction(funcNode));

    Node thisNode = new Node(Token.THIS);
    assertTrue(NodeUtil.isThis(thisNode));

    Node arrayLit = new Node(Token.ARRAYLIT);
    assertTrue(NodeUtil.isArrayLiteral(arrayLit));
  }

  @Test
  public void testIsVarDeclaration() throws Throwable {
    Node var = new Node(Token.VAR);
    Node name = Node.newString(Token.NAME, "x");
    var.addChildToBack(name);
    assertTrue(NodeUtil.isVarDeclaration(name));
    assertFalse(NodeUtil.isVarDeclaration(var));
  }

  @Test
  public void testGetAssignedValue() throws Throwable {
    Node name = Node.newString(Token.NAME, "x");
    Node var = new Node(Token.VAR, name);
    Node val = Node.newNumber(5);
    name.addChildToBack(val);

    assertEquals(val, NodeUtil.getAssignedValue(name));
  }

  @Test
  public void testLoopAndControlStructures() throws Throwable {
    Node forIn = new Node(Token.FOR, new Node(Token.NAME), new Node(Token.IN), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isForIn(forIn));

    Node whileLoop = new Node(Token.WHILE, new Node(Token.TRUE), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isLoopStructure(whileLoop));
    assertNotNull(NodeUtil.getLoopCodeBlock(whileLoop));

    Node ifNode = new Node(Token.IF, new Node(Token.TRUE), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isControlStructure(ifNode));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("-", NodeUtil.opToStr(Token.SUB));
    assertEquals("===", NodeUtil.opToStr(Token.SHEQ));
    assertNull(NodeUtil.opToStr(-999));

    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
  }

  @Test(expected = Error.class)
  public void testOpToStrNoFailError() throws Throwable {
    NodeUtil.opToStrNoFail(-999);
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
    assertFalse(NodeUtil.isValidPropertyName("invalid\u0100name"));
  }

  @Test
  public void testNewFunctionNode() throws Throwable {
    List<Node> params = new ArrayList<Node>();
    params.add(Node.newString(Token.NAME, "a"));
    Node body = new Node(Token.BLOCK);
    Node fn = NodeUtil.newFunctionNode("myFunc", params, body, 1, 0);
    assertEquals(Token.FUNCTION, fn.getType());
  }

  @Test
  public void testNewQualifiedNameNode() throws Throwable {
    CodingConvention conv = new DefaultCodingConvention();
    Node qName = NodeUtil.newQualifiedNameNode(conv, "foo.bar.baz", 1, 0);
    assertNotNull(qName);
  }

  @Test
  public void testGetRootOfQualifiedName() throws Throwable {
    Node name = Node.newString(Token.NAME, "foo");
    Node getprop = new Node(Token.GETPROP, name, Node.newString(Token.STRING, "bar"));
    assertEquals(name, NodeUtil.getRootOfQualifiedName(getprop));
  }

  @Test
  public void testGetSourceName() throws Throwable {
    Node node = Node.newNumber(1);
    node.putProp(Node.SOURCENAME_PROP, "testSource.js");
    assertEquals("testSource.js", NodeUtil.getSourceName(node));
  }

  @Test
  public void testNewCallNode() throws Throwable {
    Node target = Node.newString(Token.NAME, "func");
    Node call = NodeUtil.newCallNode(target, Node.newNumber(1));
    assertEquals(Token.CALL, call.getType());
    assertTrue(call.getBooleanProp(Node.FREE_CALL));
  }

  @Test
  public void testEvaluatesToLocalValue() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newNumber(1)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.ARRAYLIT)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.OBJECTLIT)));
  }

  @Test
  public void testGetArgumentForFunctionAndCall() throws Throwable {
    Node paramLP = new Node(Token.LP, Node.newString(Token.NAME, "arg1"));
    Node fn = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), paramLP, new Node(Token.BLOCK));
    assertNotNull(NodeUtil.getArgumentForFunction(fn, 0));
    assertNull(NodeUtil.getArgumentForFunction(fn, 5));

    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "f"), Node.newNumber(10));
    assertNotNull(NodeUtil.getArgumentForCallOrNew(call, 0));
    assertNull(NodeUtil.getArgumentForCallOrNew(call, 5));
  }
}