package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class NodeUtilTest {

  @Test
  public void testGetPureBooleanValue() throws Throwable {
    Node stringNode = IR.string("hello");
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(stringNode));

    Node emptyStringNode = IR.string("");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(emptyStringNode));

    Node numNode = IR.number(5.0);
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(numNode));

    Node zeroNumNode = IR.number(0.0);
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(zeroNumNode));

    Node trueNode = IR.trueNode();
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(trueNode));

    Node falseNode = IR.falseNode();
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(falseNode));

    Node nullNode = IR.nullNode();
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(nullNode));

    Node undefName = IR.name("undefined");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(undefName));

    Node nanName = IR.name("NaN");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(nanName));

    Node infName = IR.name("Infinity");
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(infName));

    Node unknownName = IR.name("someVariable");
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(unknownName));

    Node notNode = IR.not(IR.trueNode());
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(notNode));

    Node voidNode = IR.voidNode(IR.number(0));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(voidNode));

    Node arrayLit = IR.arraylit(IR.number(1));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(arrayLit));

    Node objectLit = IR.objectlit();
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(objectLit));

    Node regexpNode = IR.regexp(IR.string("abc"));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(regexpNode));
  }

  @Test
  public void testGetStringValue() throws Throwable {
    assertEquals("hello", NodeUtil.getStringValue(IR.string("hello")));
    assertEquals("5", NodeUtil.getStringValue(IR.number(5.0)));
    assertEquals("5.5", NodeUtil.getStringValue(IR.number(5.5)));
    assertEquals("false", NodeUtil.getStringValue(IR.falseNode()));
    assertEquals("true", NodeUtil.getStringValue(IR.trueNode()));
    assertEquals("null", NodeUtil.getStringValue(IR.nullNode()));
    assertEquals("undefined", NodeUtil.getStringValue(IR.voidNode(IR.number(0))));
    assertEquals("undefined", NodeUtil.getStringValue(IR.name("undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(IR.name("Infinity")));
    assertEquals("NaN", NodeUtil.getStringValue(IR.name("NaN")));
    assertEquals("[object Object]", NodeUtil.getStringValue(IR.objectlit()));
    assertNull(NodeUtil.getStringValue(IR.name("unknown")));
  }

  @Test
  public void testGetNumberValue() throws Throwable {
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(IR.trueNode()));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(IR.falseNode()));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(IR.nullNode()));
    assertEquals(Double.valueOf(42.0), NodeUtil.getNumberValue(IR.number(42.0)));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(IR.name("undefined")));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(IR.name("NaN")));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(IR.name("Infinity")));
    assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), NodeUtil.getNumberValue(IR.neg(IR.name("Infinity"))));
    assertEquals(Double.valueOf(10.0), NodeUtil.getNumberValue(IR.string("10")));
    assertNull(NodeUtil.getNumberValue(IR.name("nonExistent")));
  }

  @Test
  public void testGetStringNumberValue() throws Throwable {
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue(""));
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue("   "));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0xf"));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0XF"));
    assertEquals(Double.valueOf(123.0), NodeUtil.getStringNumberValue("123"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("0xZZ"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
    assertNull(NodeUtil.getStringNumberValue("+infinity"));
    assertNull(NodeUtil.getStringNumberValue("123\u000b"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("not-a-number"));
  }

  @Test
  public void testIsStrWhiteSpaceChar() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar(' '));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\n'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\r'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\t'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u00A0'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u000C'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u2028'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u2029'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\uFEFF'));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.isStrWhiteSpaceChar('\u000B'));
    assertEquals(TernaryValue.FALSE, NodeUtil.isStrWhiteSpaceChar('a'));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(IR.string("test")));
    assertTrue(NodeUtil.isImmutableValue(IR.number(1.0)));
    assertTrue(NodeUtil.isImmutableValue(IR.nullNode()));
    assertTrue(NodeUtil.isImmutableValue(IR.trueNode()));
    assertTrue(NodeUtil.isImmutableValue(IR.falseNode()));
    assertTrue(NodeUtil.isImmutableValue(IR.name("undefined")));
    assertTrue(NodeUtil.isImmutableValue(IR.name("Infinity")));
    assertTrue(NodeUtil.isImmutableValue(IR.name("NaN")));
    assertTrue(NodeUtil.isImmutableValue(IR.not(IR.trueNode())));
    assertTrue(NodeUtil.isImmutableValue(IR.neg(IR.number(1.0))));
    assertFalse(NodeUtil.isImmutableValue(IR.name("window")));
  }

  @Test
  public void testIsSymmetricAndRelationalOperation() throws Throwable {
    Node eqNode = IR.eq(IR.number(1), IR.number(1));
    assertTrue(NodeUtil.isSymmetricOperation(eqNode));
    assertFalse(NodeUtil.isRelationalOperation(eqNode));

    Node gtNode = IR.gt(IR.number(2), IR.number(1));
    assertFalse(NodeUtil.isSymmetricOperation(gtNode));
    assertTrue(NodeUtil.isRelationalOperation(gtNode));

    assertEquals(Token.LT, NodeUtil.getInverseOperator(Token.GT));
    assertEquals(Token.GT, NodeUtil.getInverseOperator(Token.LT));
    assertEquals(Token.LE, NodeUtil.getInverseOperator(Token.GE));
    assertEquals(Token.GE, NodeUtil.getInverseOperator(Token.LE));
    assertEquals(Token.ERROR, NodeUtil.getInverseOperator(Token.ADD));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("MY_DEFINE");

    assertTrue(NodeUtil.isValidDefineValue(IR.string("ok"), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.number(123), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.trueNode(), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.falseNode(), defines));

    Node addNode = IR.add(IR.number(1), IR.number(2));
    assertTrue(NodeUtil.isValidDefineValue(addNode, defines));

    Node invalidAdd = IR.add(IR.number(1), IR.name("window"));
    assertFalse(NodeUtil.isValidDefineValue(invalidAdd, defines));

    Node nameNode = IR.name("MY_DEFINE");
    assertTrue(NodeUtil.isValidDefineValue(nameNode, defines));

    Node unknownName = IR.name("OTHER_DEFINE");
    assertFalse(NodeUtil.isValidDefineValue(unknownName, defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node block = IR.block();
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(IR.empty());
    assertTrue(NodeUtil.isEmptyBlock(block));

    block.addChildToBack(IR.number(1));
    assertFalse(NodeUtil.isEmptyBlock(block));

    assertFalse(NodeUtil.isEmptyBlock(IR.number(1)));
  }

  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.FUNCTION));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
  }

  @Test
  public void testConstructorCallHasSideEffects() throws Throwable {
    Node newObj = IR.newnode(IR.name("Object"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(newObj));

    Node newCustom = IR.newnode(IR.name("CustomClass"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(newCustom));
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node callObj = IR.call(IR.name("Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callObj));

    Node callCustom = IR.call(IR.name("customFunc"));
    assertTrue(NodeUtil.functionCallHasSideEffects(callCustom));
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
    assertEquals(15, NodeUtil.precedence(Token.CALL));
  }

  @Test(expected = Error.class)
  public void testPrecedenceError() throws Throwable {
    NodeUtil.precedence(Token.ERROR);
  }

  @Test
  public void testIsUndefinedAndNullOrUndefined() throws Throwable {
    Node voidNode = IR.voidNode(IR.number(0));
    assertTrue(NodeUtil.isUndefined(voidNode));
    assertTrue(NodeUtil.isNullOrUndefined(voidNode));

    Node undefName = IR.name("undefined");
    assertTrue(NodeUtil.isUndefined(undefName));
    assertTrue(NodeUtil.isNullOrUndefined(undefName));

    Node nullNode = IR.nullNode();
    assertFalse(NodeUtil.isUndefined(nullNode));
    assertTrue(NodeUtil.isNullOrUndefined(nullNode));

    Node numNode = IR.number(1);
    assertFalse(NodeUtil.isUndefined(numNode));
    assertFalse(NodeUtil.isNullOrUndefined(numNode));
  }

  @Test
  public void testIsNumericResult() throws Throwable {
    Node num = IR.number(5);
    assertTrue(NodeUtil.isNumericResult(num));

    Node add = IR.add(IR.number(1), IR.number(2));
    assertTrue(NodeUtil.isNumericResult(add));

    Node strAdd = IR.add(IR.string("a"), IR.string("b"));
    assertFalse(NodeUtil.isNumericResult(strAdd));
  }

  @Test
  public void testIsBooleanResult() throws Throwable {
    assertTrue(NodeUtil.isBooleanResult(IR.trueNode()));
    assertTrue(NodeUtil.isBooleanResult(IR.falseNode()));
    assertTrue(NodeUtil.isBooleanResult(IR.eq(IR.number(1), IR.number(1))));
    assertTrue(NodeUtil.isBooleanResult(IR.not(IR.trueNode())));
    assertFalse(NodeUtil.isBooleanResult(IR.number(1)));
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
  public void testAssignmentOps() throws Throwable {
    assertTrue(NodeUtil.isAssignmentOp(IR.assign(IR.name("x"), IR.number(1))));
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(IR.node(Token.ASSIGN_ADD)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetOpFromAssignmentOpError() throws Throwable {
    NodeUtil.getOpFromAssignmentOp(IR.assign(IR.name("x"), IR.number(1)));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("-", NodeUtil.opToStr(Token.SUB));
    assertEquals("===", NodeUtil.opToStr(Token.SHEQ));
    assertNull(NodeUtil.opToStr(Token.FUNCTION));

    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
  }

  @Test(expected = Error.class)
  public void testOpToStrNoFailError() throws Throwable {
    NodeUtil.opToStrNoFail(Token.FUNCTION);
  }

  @Test
  public void testIsLatin() throws Throwable {
    assertTrue(NodeUtil.isLatin("hello"));
    assertFalse(NodeUtil.isLatin("h\u0080llo"));
  }

  @Test
  public void testIsValidSimpleName() throws Throwable {
    assertTrue(NodeUtil.isValidSimpleName("validName"));
    assertFalse(NodeUtil.isValidSimpleName("if"));
    assertFalse(NodeUtil.isValidSimpleName("invalid.name"));
  }

  @Test
  public void testIsValidQualifiedName() throws Throwable {
    assertTrue(NodeUtil.isValidQualifiedName("a.b.c"));
    assertFalse(NodeUtil.isValidQualifiedName(".b.c"));
    assertFalse(NodeUtil.isValidQualifiedName("a.b."));
    assertFalse(NodeUtil.isValidQualifiedName("a..b"));
  }

  @Test
  public void testBooleanNode() throws Throwable {
    assertNotNull(NodeUtil.booleanNode(true));
    assertNotNull(NodeUtil.booleanNode(false));
  }

  @Test
  public void testNumberNode() throws Throwable {
    assertNotNull(NodeUtil.numberNode(Double.NaN, null));
    assertNotNull(NodeUtil.numberNode(Double.POSITIVE_INFINITY, null));
    assertNotNull(NodeUtil.numberNode(Double.NEGATIVE_INFINITY, null));
    assertNotNull(NodeUtil.numberNode(42.0, null));
  }

  @Test
  public void testNewUndefinedNode() throws Throwable {
    assertNotNull(NodeUtil.newUndefinedNode(null));
    assertNotNull(NodeUtil.newUndefinedNode(IR.number(1)));
  }

  @Test
  public void testNewVarNode() throws Throwable {
    assertNotNull(NodeUtil.newVarNode("x", null));
    assertNotNull(NodeUtil.newVarNode("y", IR.number(1)));
  }
}