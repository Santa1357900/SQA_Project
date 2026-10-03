package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class NodeUtilTest {

  @Test
  public void test_NodeUtilTest_getPureBooleanValue_literals_attempt_1() throws Throwable {
    Node trueNode = new Node(Token.TRUE);
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(trueNode));

    Node falseNode = new Node(Token.FALSE);
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(falseNode));

    Node nullNode = new Node(Token.NULL);
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(nullNode));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(voidNode));

    Node numZero = Node.newNumber(0.0);
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(numZero));

    Node numNonZero = Node.newNumber(5.5);
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(numNonZero));

    Node emptyStr = Node.newString("");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(emptyStr));

    Node nonEmptyStr = Node.newString("hello");
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(nonEmptyStr));
  }

  @Test
  public void test_NodeUtilTest_getPureBooleanValue_names_attempt_2() throws Throwable {
    Node undefinedName = Node.newString(Token.NAME, "undefined");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(undefinedName));

    Node nanName = Node.newString(Token.NAME, "NaN");
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(nanName));

    Node infinityName = Node.newString(Token.NAME, "Infinity");
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(infinityName));

    Node unknownName = Node.newString(Token.NAME, "someUnknownVar");
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(unknownName));
  }

  @Test
  public void test_NodeUtilTest_getStringValue_primitives_attempt_3() throws Throwable {
    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID, Node.newNumber(0))));

    assertEquals("10", NodeUtil.getStringValue(Node.newNumber(10.0)));
    assertEquals("3.5", NodeUtil.getStringValue(Node.newNumber(3.5)));
    assertEquals("hello", NodeUtil.getStringValue(Node.newString("hello")));

    assertEquals("undefined", NodeUtil.getStringValue(Node.newString(Token.NAME, "undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals("NaN", NodeUtil.getStringValue(Node.newString(Token.NAME, "NaN")));
    assertNull(NodeUtil.getStringValue(Node.newString(Token.NAME, "regularVar")));
  }

  @Test
  public void test_NodeUtilTest_getNumberValue_conversion_attempt_4() throws Throwable {
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(new Node(Token.TRUE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.FALSE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.NULL)));
    assertEquals(Double.valueOf(42.5), NodeUtil.getNumberValue(Node.newNumber(42.5)));

    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(Node.newString(Token.NAME, "undefined")));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(Node.newString(Token.NAME, "NaN")));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(Node.newString(Token.NAME, "Infinity")));
    assertNull(NodeUtil.getNumberValue(Node.newString(Token.NAME, "unknown")));

    assertEquals(Double.valueOf(123.0), NodeUtil.getNumberValue(Node.newString("123")));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(Node.newString("")));
    assertEquals(Double.valueOf(255.0), NodeUtil.getNumberValue(Node.newString("0xFF")));
  }

  @Test
  public void test_NodeUtilTest_getStringNumberValue_edgeCases_attempt_5() throws Throwable {
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue(""));
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue("   "));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0xF"));
    assertEquals(Double.valueOf(15.0), NodeUtil.getStringNumberValue("0XF"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("0xINVALID"));
    assertNull(NodeUtil.getStringNumberValue("+0xF"));
    assertNull(NodeUtil.getStringNumberValue("-0xF"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("not-a-number"));
  }

  @Test
  public void test_NodeUtilTest_isImmutableValue_attempt_6() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("abc")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(1.0)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));

    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "window")));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertTrue(NodeUtil.isImmutableValue(notNode));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isImmutableValue(voidNode));
  }

  @Test
  public void test_NodeUtilTest_isEmptyBlock_attempt_7() throws Throwable {
    Node blockNode = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(blockNode));

    Node emptyChildBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(emptyChildBlock));

    Node nonBlock = new Node(Token.TRUE);
    assertFalse(NodeUtil.isEmptyBlock(nonBlock));

    Node nonEmptyBlock = new Node(Token.BLOCK, new Node(Token.TRUE));
    assertFalse(NodeUtil.isEmptyBlock(nonEmptyBlock));
  }

  @Test
  public void test_NodeUtilTest_precedence_and_ops_attempt_8() throws Throwable {
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(12, NodeUtil.precedence(Token.MUL));
    assertEquals(15, NodeUtil.precedence(Token.TRUE));

    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("-", NodeUtil.opToStr(Token.SUB));
    assertEquals("===", NodeUtil.opToStr(Token.SHEQ));
    assertNull(NodeUtil.opToStr(Token.FUNCTION));

    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
  }

  @Test(expected = Error.class)
  public void test_NodeUtilTest_opToStrNoFail_exception_attempt_9() throws Throwable {
    NodeUtil.opToStrNoFail(Token.FUNCTION);
  }

  @Test
  public void test_NodeUtilTest_isValidDefineValue_attempt_10() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("MY_DEFINE");

    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newString("str"), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node validName = Node.newString(Token.NAME, "MY_DEFINE");
    assertTrue(NodeUtil.isValidDefineValue(validName, defines));

    Node invalidName = Node.newString(Token.NAME, "OTHER_VAR");
    assertFalse(NodeUtil.isValidDefineValue(invalidName, defines));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isValidDefineValue(addNode, defines));

    Node invalidBinOp = new Node(Token.ADD, Node.newNumber(1), invalidName);
    assertFalse(NodeUtil.isValidDefineValue(invalidBinOp, defines));
  }

  @Test
  public void test_NodeUtilTest_isSimpleOperatorType_attempt_11() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.NOT));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.FUNCTION));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
  }

  @Test
  public void test_NodeUtilTest_isNumericResult_attempt_12() throws Throwable {
    Node numNode = Node.newNumber(10);
    assertTrue(NodeUtil.isNumericResult(numNode));

    Node nanName = Node.newString(Token.NAME, "NaN");
    assertTrue(NodeUtil.isNumericResult(nanName));

    Node infinityName = Node.newString(Token.NAME, "Infinity");
    assertTrue(NodeUtil.isNumericResult(infinityName));

    Node stringNode = Node.newString("test");
    assertFalse(NodeUtil.isNumericResult(stringNode));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isNumericResult(addNode));
  }

  @Test
  public void test_NodeUtilTest_isBooleanResult_attempt_13() throws Throwable {
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.EQ)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.NE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.NOT)));
    assertFalse(NodeUtil.isBooleanResult(Node.newNumber(1)));
  }

  @Test
  public void test_NodeUtilTest_isUndefinedAndNull_attempt_14() throws Throwable {
    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isUndefined(voidNode));

    Node undefName = Node.newString(Token.NAME, "undefined");
    assertTrue(NodeUtil.isUndefined(undefName));
    assertFalse(NodeUtil.isUndefined(Node.newNumber(1)));

    Node nullNode = new Node(Token.NULL);
    assertTrue(NodeUtil.isNull(nullNode));
    assertFalse(NodeUtil.isNull(Node.newNumber(1)));

    assertTrue(NodeUtil.isNullOrUndefined(nullNode));
    assertTrue(NodeUtil.isNullOrUndefined(undefNode(voidNode)));
    assertFalse(NodeUtil.isNullOrUndefined(Node.newNumber(0)));
  }

  private Node undefNode(Node n) {
    return n;
  }

  @Test
  public void test_NodeUtilTest_isAssociativeAndCommutative_attempt_15() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertTrue(NodeUtil.isAssociative(Token.AND));
    assertTrue(NodeUtil.isAssociative(Token.OR));
    assertFalse(NodeUtil.isAssociative(Token.ADD));
    assertFalse(NodeUtil.isAssociative(Token.SUB));

    assertTrue(NodeUtil.isCommutative(Token.MUL));
    assertTrue(NodeUtil.isCommutative(Token.BITAND));
    assertFalse(NodeUtil.isCommutative(Token.DIV));
  }

  @Test
  public void test_NodeUtilTest_isAssignmentOp_attempt_16() throws Throwable {
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN)));
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN_ADD)));
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN_MUL)));
    assertFalse(NodeUtil.isAssignmentOp(new Node(Token.ADD)));
  }

  @Test
  public void test_NodeUtilTest_getOpFromAssignmentOp_attempt_17() throws Throwable {
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_ADD)));
    assertEquals(Token.SUB, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_SUB)));
    assertEquals(Token.MUL, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_MUL)));
    assertEquals(Token.DIV, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_DIV)));
    assertEquals(Token.BITOR, NodeUtil.getOpFromAssignmentOp(new Node(Token.ASSIGN_BITOR)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void test_NodeUtilTest_getOpFromAssignmentOp_exception_attempt_18() throws Throwable {
    NodeUtil.getOpFromAssignmentOp(new Node(Token.ADD));
  }

  @Test
  public void test_NodeUtilTest_isLatin_attempt_19() throws Throwable {
    assertTrue(NodeUtil.isLatin("hello world 123!"));
    assertFalse(NodeUtil.isLatin("hello \u0100 world"));
  }

  @Test
  public void test_NodeUtilTest_isValidPropertyName_attempt_20() throws Throwable {
    assertTrue(NodeUtil.isValidPropertyName("validName"));
    assertTrue(NodeUtil.isValidPropertyName("_privateProp"));
    assertFalse(NodeUtil.isValidPropertyName("if")); // keyword
    assertFalse(NodeUtil.isValidPropertyName("not valid")); // invalid identifier
    assertFalse(NodeUtil.isValidPropertyName("prop\u0100")); // non-latin
  }

  @Test
  public void test_NodeUtilTest_evaluatesToLocalValue_attempt_21() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newNumber(5)));
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newString("abc")));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.ARRAYLIT)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.OBJECTLIT)));
    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.REGEXP)));
  }

  @Test
  public void test_NodeUtilTest_constructorCallHasSideEffects_exception_attempt_22() throws Throwable {
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    try {
      NodeUtil.constructorCallHasSideEffects(callNode);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Expected NEW node"));
    }
  }

  @Test
  public void test_NodeUtilTest_functionCallHasSideEffects_exception_attempt_23() throws Throwable {
    Node newCallNode = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    try {
      NodeUtil.functionCallHasSideEffects(newCallNode);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Expected CALL node"));
    }
  }

  @Test
  public void test_NodeUtilTest_constructorCallHasSideEffects_builtins_attempt_24() throws Throwable {
    Node newObj = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(newObj));

    Node newCustom = new Node(Token.NEW, Node.newString(Token.NAME, "CustomClass"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(newCustom));
  }

  @Test
  public void test_NodeUtilTest_functionCallHasSideEffects_builtins_attempt_25() throws Throwable {
    Node callObj = new Node(Token.CALL, Node.newString(Token.NAME, "Object"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callObj));

    Node callMath = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "Math"), Node.newString(Token.STRING, "floor")), Node.newNumber(1.5));
    assertFalse(NodeUtil.functionCallHasSideEffects(callMath));
  }

  @Test
  public void test_NodeUtilTest_mayHaveSideEffects_literals_attempt_26() throws Throwable {
    assertFalse(NodeUtil.mayHaveSideEffects(Node.newNumber(10)));
    assertFalse(NodeUtil.mayHaveSideEffects(Node.newString("hello")));
    assertFalse(NodeUtil.mayHaveSideEffects(new Node(Token.TRUE)));
    assertFalse(NodeUtil.mayHaveSideEffects(new Node(Token.FALSE)));
    assertFalse(NodeUtil.mayHaveSideEffects(new Node(Token.NULL)));

    assertTrue(NodeUtil.mayHaveSideEffects(new Node(Token.THROW, Node.newString("error"))));
  }

  @Test
  public void test_NodeUtilTest_getArrayElementStringValue_attempt_27() throws Throwable {
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.EMPTY)));
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.NULL)));
    assertEquals("10", NodeUtil.getArrayElementStringValue(Node.newNumber(10)));
    assertEquals("foo", NodeUtil.getArrayElementStringValue(Node.newString("foo")));
  }

  @Test
  public void test_NodeUtilTest_arrayToString_attempt_28() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newString("two"), new Node(Token.TRUE));
    assertEquals("1,two,true", NodeUtil.arrayToString(arrayLit));

    Node arrayLitWithEmpty = new Node(Token.ARRAYLIT, Node.newNumber(1), new Node(Token.EMPTY));
    assertEquals("1,", NodeUtil.arrayToString(arrayLitWithEmpty));
  }

  @Test
  public void test_NodeUtilTest_isLoopStructure_attempt_29() throws Throwable {
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.FOR)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.DO)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.WHILE)));
    assertFalse(NodeUtil.isLoopStructure(new Node(Token.IF)));
  }

  @Test
  public void test_NodeUtilTest_isControlStructure_attempt_30() throws Throwable {
    assertTrue(NodeUtil.isControlStructure(new Node(Token.IF)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.SWITCH)));
    assertTrue(NodeUtil.isControlStructure(new Node(Token.TRY)));
    assertFalse(NodeUtil.isControlStructure(Node.newNumber(1)));
  }
}