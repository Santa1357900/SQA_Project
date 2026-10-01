package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.HashSet;
import java.util.Set;

public class NodeUtilClaudeTest {

  // Covers ASSIGN case: value is the RHS (last child)
  @Test
  public void testGetImpureBooleanValue_assign_returnsRhsValue() throws Throwable {
    Node assign = new Node(Token.ASSIGN, IR.name("x"));
    assign.addChildToBack(IR.number(5));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(assign));
  }

  // Covers NOT case: inverts the boolean value of the operand
  @Test
  public void testGetImpureBooleanValue_not_invertsValue() throws Throwable {
    Node not = new Node(Token.NOT, IR.string(""));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(not));
  }

  // Covers AND case: both operands true -> true
  @Test
  public void testGetImpureBooleanValue_and_bothTrue_returnsTrue() throws Throwable {
    Node and = new Node(Token.AND, IR.string("a"));
    and.addChildToBack(IR.string("b"));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(and));
  }

  // Covers HOOK case: equal branch values returned directly
  @Test
  public void testGetImpureBooleanValue_hook_equalBranches_returnsSameValue() throws Throwable {
    Node hook = new Node(Token.HOOK, IR.string("cond"));
    hook.addChildToBack(IR.string("a"));
    hook.addChildToBack(IR.string("b"));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(hook));
  }

  // Covers HOOK case: different branch values -> UNKNOWN
  @Test
  public void testGetImpureBooleanValue_hook_differentBranches_returnsUnknown() throws Throwable {
    Node hook = new Node(Token.HOOK, IR.string("cond"));
    hook.addChildToBack(IR.string("a"));
    hook.addChildToBack(IR.string(""));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getImpureBooleanValue(hook));
  }

  // Covers OBJECTLIT/ARRAYLIT case: always true, ignoring side effects
  @Test
  public void testGetImpureBooleanValue_objectLit_returnsTrue() throws Throwable {
    Node obj = new Node(Token.OBJECTLIT);
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(obj));
  }

  // Covers STRING case: empty -> FALSE, non-empty -> TRUE
  @Test
  public void testGetPureBooleanValue_string_emptyAndNonEmpty() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.string("")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.string("x")));
  }

  // Covers NUMBER case: zero -> FALSE, non-zero -> TRUE
  @Test
  public void testGetPureBooleanValue_number_zeroAndNonzero() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.number(0)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.number(1)));
  }

  // Covers NAME case: undefined/NaN -> FALSE, Infinity -> TRUE
  @Test
  public void testGetPureBooleanValue_name_undefinedNaNInfinity() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.name("undefined")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.name("NaN")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.name("Infinity")));
  }

  // Covers ARRAYLIT case with side effects -> falls through to UNKNOWN
  @Test
  public void testGetPureBooleanValue_arrayLitWithSideEffects_returnsUnknown() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT);
    Node call = new Node(Token.CALL, IR.name("foo"));
    arr.addChildToBack(call);
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(arr));
  }

  // Covers NUMBER case: integer formatted without decimal, fraction keeps decimal
  @Test
  public void testGetStringValue_number_integerVsFraction() throws Throwable {
    assertEquals("1", NodeUtil.getStringValue(IR.number(1.0)));
    assertEquals("1.5", NodeUtil.getStringValue(IR.number(1.5)));
  }

  // Covers NOT case: String() of negated empty string is "true"
  @Test
  public void testGetStringValue_not_reversedBoolean() throws Throwable {
    Node not = new Node(Token.NOT, IR.string(""));
    assertEquals("true", NodeUtil.getStringValue(not));
  }

  // Covers OBJECTLIT case and default (unmatched type) case returning null
  @Test
  public void testGetStringValue_objectLitAndUnknownType() throws Throwable {
    assertEquals("[object Object]", NodeUtil.getStringValue(new Node(Token.OBJECTLIT)));
    assertNull(NodeUtil.getStringValue(new Node(Token.THIS)));
  }

  // Covers null/undefined/EMPTY -> "" special-casing
  @Test
  public void testGetArrayElementStringValue_nullUndefinedEmpty_returnsEmptyString() throws Throwable {
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.NULL)));
    assertEquals("", NodeUtil.getArrayElementStringValue(new Node(Token.EMPTY)));
  }

  // Covers normal loop joining elements with comma
  @Test
  public void testArrayToString_multipleElements_joinedByComma() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT);
    arr.addChildToBack(IR.number(1));
    arr.addChildToBack(IR.number(2));
    assertEquals("1,2", NodeUtil.arrayToString(arr));
  }

  // Covers early-return-null path when an element's string value is null
  @Test
  public void testArrayToString_elementReturnsNull_returnsNull() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT);
    arr.addChildToBack(new Node(Token.THIS));
    assertNull(NodeUtil.arrayToString(arr));
  }

  // Covers TRUE/FALSE/NULL numeric conversions
  @Test
  public void testGetNumberValue_trueFalseNull() throws Throwable {
    assertEquals(1.0, NodeUtil.getNumberValue(new Node(Token.TRUE)), 1e-9);
    assertEquals(0.0, NodeUtil.getNumberValue(new Node(Token.FALSE)), 1e-9);
    assertEquals(0.0, NodeUtil.getNumberValue(new Node(Token.NULL)), 1e-9);
  }

  // Covers VOID case with no side effects -> NaN
  @Test
  public void testGetNumberValue_voidNoSideEffects_returnsNaN() throws Throwable {
    Node voidNode = new Node(Token.VOID, IR.number(0));
    Double result = NodeUtil.getNumberValue(voidNode);
    assertTrue(Double.isNaN(result));
  }

  // Covers NEG(Infinity) special case
  @Test
  public void testGetNumberValue_negInfinity_returnsNegativeInfinity() throws Throwable {
    Node neg = new Node(Token.NEG, IR.name("Infinity"));
    assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), NodeUtil.getNumberValue(neg), 0.0);
  }

  // BUG: hex strings that overflow Integer range must still parse to the correct
  // numeric value, per JS Number() cast semantics (0xffffffff == 4294967295).
  @Test
  public void testGetStringNumberValue_hexOverflow_returnsCorrectValue() throws Throwable {
    Double result = NodeUtil.getStringNumberValue("0xffffffff");
    assertEquals(4294967295.0, result, 1e-9);
  }

  // Covers empty-after-trim string -> 0.0
  @Test
  public void testGetStringNumberValue_emptyString_returnsZero() throws Throwable {
    assertEquals(0.0, NodeUtil.getStringNumberValue("   "), 1e-9);
  }

  // Covers explicitly signed hex numbers -> null (browser-dependent, left alone)
  @Test
  public void testGetStringNumberValue_signedHex_returnsNull() throws Throwable {
    assertNull(NodeUtil.getStringNumberValue("+0x10"));
  }

  // Covers "infinity" (any sign) literal strings -> null
  @Test
  public void testGetStringNumberValue_infinityVariants_returnNull() throws Throwable {
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
  }

  // Covers unparsable string -> NaN via caught NumberFormatException
  @Test
  public void testGetStringNumberValue_invalidNumber_returnsNaN() throws Throwable {
    Double result = NodeUtil.getStringNumberValue("abc");
    assertTrue(Double.isNaN(result));
  }

  // Covers leading/trailing whitespace trimming
  @Test
  public void testTrimJsWhiteSpace_trimsLeadingAndTrailingWhitespace() throws Throwable {
    assertEquals("abc", NodeUtil.trimJsWhiteSpace("  abc  "));
  }

  // Covers immutable literal types vs. non-constant NAME
  @Test
  public void testIsImmutableValue_variousTypes() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(IR.string("x")));
    assertTrue(NodeUtil.isImmutableValue(IR.name("undefined")));
    assertFalse(NodeUtil.isImmutableValue(IR.name("x")));
  }

  // Covers ARRAYLIT loop skipping EMPTY slots while checking remaining children
  @Test
  public void testIsLiteralValue_arrayLitWithEmptySlots_true() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT);
    arr.addChildToBack(new Node(Token.EMPTY));
    arr.addChildToBack(IR.number(1));
    assertTrue(NodeUtil.isLiteralValue(arr, false));
  }

  // Covers immutable literal and known-define-name acceptance, unknown name rejection
  @Test
  public void testIsValidDefineValue_validAndInvalidCases() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("FOO");
    assertTrue(NodeUtil.isValidDefineValue(IR.number(1), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.name("FOO"), defines));
    assertFalse(NodeUtil.isValidDefineValue(IR.name("BAR"), defines));
  }

  // Covers BLOCK with no real children vs. BLOCK with a real statement vs non-BLOCK
  @Test
  public void testIsEmptyBlock_variousCases() throws Throwable {
    assertTrue(NodeUtil.isEmptyBlock(IR.block()));
    Node block = IR.block();
    block.addChildToBack(new Node(Token.EXPR_RESULT, IR.number(1)));
    assertFalse(NodeUtil.isEmptyBlock(block));
    assertFalse(NodeUtil.isEmptyBlock(IR.name("x")));
  }

  // Covers simple-operator classification true/false branches
  @Test
  public void testIsSimpleOperatorType_trueAndFalseCases() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
  }

  // Covers creation of an EXPR_RESULT wrapper node
  @Test
  public void testNewExpr_createsExprResultWithChild() throws Throwable {
    Node child = IR.number(5);
    Node expr = NodeUtil.newExpr(child);
    assertEquals(Token.EXPR_RESULT, expr.getType());
    assertEquals(child, expr.getFirstChild());
  }

  // Covers known no-side-effect constructor vs unknown constructor
  @Test
  public void testConstructorCallHasSideEffects_knownAndUnknownConstructors() throws Throwable {
    Node knownNew = new Node(Token.NEW, IR.name("Array"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(knownNew));
    Node unknownNew = new Node(Token.NEW, IR.name("Foo"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(unknownNew));
  }

  // Covers precondition check throwing for non-NEW node
  @Test
  public void testConstructorCallHasSideEffects_nonNewNode_throwsIllegalStateException() throws Throwable {
    try {
      NodeUtil.constructorCallHasSideEffects(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers Math namespace calls being side-effect free
  @Test
  public void testFunctionCallHasSideEffects_mathNamespace_noSideEffects() throws Throwable {
    Node getprop = new Node(Token.GETPROP, IR.name("Math"));
    getprop.addChildToBack(IR.string("max"));
    Node call = new Node(Token.CALL, getprop);
    assertFalse(NodeUtil.functionCallHasSideEffects(call));
  }

  // Covers precondition check throwing for non-CALL node
  @Test
  public void testFunctionCallHasSideEffects_nonCallNode_throwsIllegalStateException() throws Throwable {
    try {
      NodeUtil.functionCallHasSideEffects(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers known precedence mapping and the default-case Error for unmapped types
  @Test
  public void testPrecedence_knownAndUnknownTypes() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(15, NodeUtil.precedence(Token.NAME));
    try {
      NodeUtil.precedence(Token.SCRIPT);
      fail("expected Error");
    } catch (Error expected) {
    }
  }

  // Covers associative and commutative operator classification
  @Test
  public void testIsAssociativeAndCommutative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.ADD));
    assertTrue(NodeUtil.isCommutative(Token.BITAND));
    assertFalse(NodeUtil.isCommutative(Token.SUB));
  }

  // Covers assignment-op detection and mapping to underlying operator
  @Test
  public void testIsAssignmentOp_andGetOpFromAssignmentOp() throws Throwable {
    Node assign = new Node(Token.ASSIGN_ADD, IR.name("x"));
    assign.addChildToBack(IR.number(1));
    assertTrue(NodeUtil.isAssignmentOp(assign));
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(assign));
  }

  // Covers IllegalArgumentException thrown for a non-assignment operator
  @Test
  public void testGetOpFromAssignmentOp_notAssignmentOp_throwsIllegalArgumentException() throws Throwable {
    try {
      NodeUtil.getOpFromAssignmentOp(IR.name("x"));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Covers VAR-declaration branch of getAssignedValue
  @Test
  public void testGetAssignedValue_varDeclaration() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    Node value = IR.number(5);
    nameNode.addChildToBack(value);
    new Node(Token.VAR, nameNode);
    assertEquals(value, NodeUtil.getAssignedValue(nameNode));
  }

  // Covers FOR with 3 children (for-in, no explicit condition) vs 4 children (condition present)
  @Test
  public void testGetConditionExpression_forLoopVariants() throws Throwable {
    Node forIn = new Node(Token.FOR);
    forIn.addChildToBack(new Node(Token.NAME));
    forIn.addChildToBack(new Node(Token.NAME));
    forIn.addChildToBack(IR.block());
    assertNull(NodeUtil.getConditionExpression(forIn));

    Node forFull = new Node(Token.FOR);
    forFull.addChildToBack(new Node(Token.EMPTY));
    Node cond = IR.name("cond");
    forFull.addChildToBack(cond);
    forFull.addChildToBack(new Node(Token.EMPTY));
    forFull.addChildToBack(IR.block());
    assertEquals(cond, NodeUtil.getConditionExpression(forFull));
  }

  // Covers detection of a given token type anywhere within the subtree
  @Test
  public void testContainsType_foundAndNotFound() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, IR.name("foo"));
    root.addChildToBack(new Node(Token.EXPR_RESULT, call));
    assertTrue(NodeUtil.containsType(root, Token.CALL));
    assertFalse(NodeUtil.containsType(root, Token.NEW));
  }

  // Covers the IS_CONSTANT_NAME boolean prop read by isConstantName
  @Test
  public void testIsConstantName_trueAndFalse() throws Throwable {
    Node name = IR.name("FOO");
    assertFalse(NodeUtil.isConstantName(name));
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertTrue(NodeUtil.isConstantName(name));
  }
}
