package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.HashSet;
import java.util.Set;

public class NodeUtilClaudeTest {

  // VOID branch of getImpureBooleanValue always returns FALSE, even with side-effecting child.
  @Test
  public void testGetImpureBooleanValue_voidAlwaysFalseRegardlessOfSideEffects() throws Throwable {
    Node voidNode = IR.voidNode(IR.call(IR.name("f")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(voidNode));
  }

  // default branch delegates to getPureBooleanValue.
  @Test
  public void testGetImpureBooleanValue_defaultDelegatesToPure() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(IR.number(5)));
  }

  // STRING and NUMBER branches of getPureBooleanValue.
  @Test
  public void testGetPureBooleanValue_stringAndNumber() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.string("")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.string("x")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.number(0)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.number(5)));
  }

  // NAME branch: undefined/NaN false, Infinity true, other name unknown.
  @Test
  public void testGetPureBooleanValue_nameConstants() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.name("undefined")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.name("NaN")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.name("Infinity")));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(IR.name("foo")));
  }

  // TRUE/FALSE literal nodes and VOID with no side effects.
  @Test
  public void testGetPureBooleanValue_trueFalseAndVoid() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(IR.trueNode()));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.falseNode()));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(IR.voidNode(IR.number(0))));
  }

  // STRING and NAME-constant branches of getStringValue.
  @Test
  public void testGetStringValue_stringAndNameConstants() throws Throwable {
    assertEquals("abc", NodeUtil.getStringValue(IR.string("abc")));
    assertEquals("undefined", NodeUtil.getStringValue(IR.name("undefined")));
    assertEquals("Infinity", NodeUtil.getStringValue(IR.name("Infinity")));
    assertNull(NodeUtil.getStringValue(IR.name("x")));
  }

  // NUMBER, TRUE/FALSE, VOID and default branches of getStringValue.
  @Test
  public void testGetStringValue_numberTrueFalseVoidAndDefault() throws Throwable {
    assertEquals("3", NodeUtil.getStringValue(IR.number(3.0)));
    assertEquals("true", NodeUtil.getStringValue(IR.trueNode()));
    assertEquals("false", NodeUtil.getStringValue(IR.falseNode()));
    assertEquals("undefined", NodeUtil.getStringValue(IR.voidNode(IR.number(0))));
    assertNull(NodeUtil.getStringValue(IR.call(IR.name("f"))));
  }

  // getStringValue(double): "1" not "1.0" for integral values, keeps decimals otherwise.
  @Test
  public void testGetStringValueDouble_integralVsFractional() throws Throwable {
    assertEquals("1", NodeUtil.getStringValue(1.0));
    assertEquals("100", NodeUtil.getStringValue(100.0));
    assertEquals("1.5", NodeUtil.getStringValue(1.5));
  }

  // TRUE/FALSE and NUMBER branches of getNumberValue.
  @Test
  public void testGetNumberValue_trueFalseAndNumber() throws Throwable {
    assertEquals(1.0, NodeUtil.getNumberValue(IR.trueNode()), 0.0);
    assertEquals(0.0, NodeUtil.getNumberValue(IR.falseNode()), 0.0);
    assertEquals(42.0, NodeUtil.getNumberValue(IR.number(42.0)), 0.0);
  }

  // VOID (no side effects) and NAME-constant branches of getNumberValue.
  @Test
  public void testGetNumberValue_voidAndNameConstants() throws Throwable {
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(IR.voidNode(IR.number(0)))));
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(IR.name("undefined"))));
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(IR.name("NaN"))));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(IR.name("Infinity")), 0.0);
    assertNull(NodeUtil.getNumberValue(IR.name("x")));
  }

  // NEG branch: only -Infinity form is recognized, otherwise null.
  @Test
  public void testGetNumberValue_negInfinityBranch() throws Throwable {
    Double negInf = NodeUtil.getNumberValue(IR.neg(IR.name("Infinity")));
    assertTrue(negInf != null && Double.isInfinite(negInf) && negInf < 0);
    assertNull(NodeUtil.getNumberValue(IR.neg(IR.number(5))));
  }

  // STRING branch of getNumberValue delegates to getStringNumberValue.
  @Test
  public void testGetNumberValue_stringDelegates() throws Throwable {
    assertEquals(3.5, NodeUtil.getNumberValue(IR.string("3.5")), 0.0);
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(IR.string("abc"))));
  }

  // vertical tab always makes the value unparsable; empty/whitespace-only is 0.
  @Test
  public void testGetStringNumberValue_verticalTabAndEmpty() throws Throwable {
    assertNull(NodeUtil.getStringNumberValue("12\u000b3"));
    assertEquals(0.0, NodeUtil.getStringNumberValue("   "), 0.0);
    assertEquals(42.0, NodeUtil.getStringNumberValue(" 42 "), 0.0);
  }

  // hex parsing: valid hex converts, invalid hex yields NaN.
  @Test
  public void testGetStringNumberValue_hexValidAndInvalid() throws Throwable {
    assertEquals(26.0, NodeUtil.getStringNumberValue("0x1A"), 0.0);
    assertTrue(Double.isNaN(NodeUtil.getStringNumberValue("0xZZ")));
  }

  // signed hex is unsupported (null); lowercase infinity words are also unsupported (null).
  @Test
  public void testGetStringNumberValue_signedHexAndInfinityWords() throws Throwable {
    assertNull(NodeUtil.getStringNumberValue("+0x1A"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
  }

  // capitalized "Infinity" falls through to Double.parseDouble which does support it.
  @Test
  public void testGetStringNumberValue_capitalInfinityParsed() throws Throwable {
    Double v = NodeUtil.getStringNumberValue("Infinity");
    assertTrue(v != null && Double.isInfinite(v) && v > 0);
    assertTrue(Double.isNaN(NodeUtil.getStringNumberValue("abc")));
  }

  // trimJsWhiteSpace strips leading/trailing JS whitespace only.
  @Test
  public void testTrimJsWhiteSpace() throws Throwable {
    assertEquals("hello", NodeUtil.trimJsWhiteSpace(" \t\nhello\r\n "));
    assertEquals("", NodeUtil.trimJsWhiteSpace(""));
  }

  // isStrWhiteSpaceChar: common whitespace true, letter false, vertical tab unknown.
  @Test
  public void testIsStrWhiteSpaceChar() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar(' '));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\n'));
    assertEquals(TernaryValue.FALSE, NodeUtil.isStrWhiteSpaceChar('a'));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.isStrWhiteSpaceChar('\u000B'));
  }

  // isImmutableValue: literals, NOT applicable, VOID/NEG recursion, NAME constants, default.
  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(IR.string("s")));
    assertTrue(NodeUtil.isImmutableValue(IR.voidNode(IR.number(0))));
    assertTrue(NodeUtil.isImmutableValue(IR.neg(IR.number(5))));
    assertTrue(NodeUtil.isImmutableValue(IR.name("Infinity")));
    assertFalse(NodeUtil.isImmutableValue(IR.name("x")));
    assertFalse(NodeUtil.isImmutableValue(IR.call(IR.name("f"))));
  }

  // getInverseOperator: known mappings and default ERROR.
  @Test
  public void testGetInverseOperator() throws Throwable {
    assertEquals(Token.LT, NodeUtil.getInverseOperator(Token.GT));
    assertEquals(Token.GT, NodeUtil.getInverseOperator(Token.LT));
    assertEquals(Token.LE, NodeUtil.getInverseOperator(Token.GE));
    assertEquals(Token.GE, NodeUtil.getInverseOperator(Token.LE));
    assertEquals(Token.ERROR, NodeUtil.getInverseOperator(Token.ADD));
  }

  // isValidDefineValue: literals true, NEG recurses, NAME checked against defines set, default false.
  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("FOO");
    assertTrue(NodeUtil.isValidDefineValue(IR.string("s"), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.neg(IR.number(5)), defines));
    assertTrue(NodeUtil.isValidDefineValue(IR.name("FOO"), defines));
    assertFalse(NodeUtil.isValidDefineValue(IR.name("BAR"), defines));
    assertFalse(NodeUtil.isValidDefineValue(IR.call(IR.name("f")), defines));
  }

  // isEmptyBlock: empty block / block with only EMPTY child true, block with statement false, non-block false.
  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node block = IR.block();
    assertTrue(NodeUtil.isEmptyBlock(block));
    block.addChildToBack(IR.empty());
    assertTrue(NodeUtil.isEmptyBlock(block));
    Node block2 = IR.block();
    block2.addChildToBack(IR.exprResult(IR.call(IR.name("f"))));
    assertFalse(NodeUtil.isEmptyBlock(block2));
    assertFalse(NodeUtil.isEmptyBlock(IR.call(IR.name("f"))));
  }

  // isSimpleOperatorType / isSimpleOperator true/false cases.
  @Test
  public void testIsSimpleOperatorTypeAndOperator() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.AND));
    assertFalse(NodeUtil.isSimpleOperator(IR.name("x")));
    Node getprop = IR.getprop(IR.name("a"), IR.string("b"));
    assertTrue(NodeUtil.isSimpleOperator(getprop));
  }

  // mayHaveSideEffects: literal false, unknown call true, builtin call false.
  @Test
  public void testMayHaveSideEffects() throws Throwable {
    assertFalse(NodeUtil.mayHaveSideEffects(IR.number(5)));
    assertTrue(NodeUtil.mayHaveSideEffects(IR.call(IR.name("doSomething"))));
    assertFalse(NodeUtil.mayHaveSideEffects(IR.call(IR.name("Object"))));
  }

  // constructorCallHasSideEffects requires a NEW node.
  @Test
  public void testConstructorCallHasSideEffects_precondition() throws Throwable {
    try {
      NodeUtil.constructorCallHasSideEffects(IR.call(IR.name("f")));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // functionCallHasSideEffects: precondition, builtin false, toString false, unknown true.
  @Test
  public void testFunctionCallHasSideEffects_variants() throws Throwable {
    try {
      NodeUtil.functionCallHasSideEffects(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
    assertFalse(NodeUtil.functionCallHasSideEffects(IR.call(IR.name("Array"))));
    Node toStringCall = IR.call(IR.getprop(IR.name("a"), IR.string("toString")));
    assertFalse(NodeUtil.functionCallHasSideEffects(toStringCall));
    Node mathFloor = IR.call(IR.getprop(IR.name("Math"), IR.string("floor")));
    assertFalse(NodeUtil.functionCallHasSideEffects(mathFloor));
  }

  // callHasLocalResult precondition and default-false flag; newHasLocalResult precondition.
  @Test
  public void testCallHasLocalResultAndNewHasLocalResultPrecondition() throws Throwable {
    try {
      NodeUtil.callHasLocalResult(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
    assertFalse(NodeUtil.callHasLocalResult(IR.call(IR.name("f"))));
    try {
      NodeUtil.newHasLocalResult(IR.call(IR.name("f")));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nodeTypeMayHaveSideEffects: NAME w/wo children, CALL delegates, default false.
  @Test
  public void testNodeTypeMayHaveSideEffects() throws Throwable {
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(IR.name("x")));
    Node nameWithChild = IR.name("x");
    nameWithChild.addChildToBack(IR.number(1));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(nameWithChild));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(IR.call(IR.name("doSomething"))));
    Node getprop = IR.getprop(IR.name("a"), IR.string("b"));
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(getprop));
  }

  // precedence: known mappings and default throws Error for unmapped type.
  @Test
  public void testPrecedence_mappingAndDefaultError() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(15, NodeUtil.precedence(Token.NAME));
    try {
      NodeUtil.precedence(Token.SCRIPT);
      fail("expected Error");
    } catch (Error expected) {
    }
  }

  // isUndefined/isNullOrUndefined: VOID true, NAME undefined true, other name/number false.
  @Test
  public void testIsUndefinedAndIsNullOrUndefined() throws Throwable {
    assertTrue(NodeUtil.isUndefined(IR.voidNode(IR.number(0))));
    assertTrue(NodeUtil.isUndefined(IR.name("undefined")));
    assertFalse(NodeUtil.isUndefined(IR.name("x")));
    assertFalse(NodeUtil.isUndefined(IR.number(5)));
    assertTrue(NodeUtil.isNullOrUndefined(IR.voidNode(IR.number(0))));
    assertFalse(NodeUtil.isNullOrUndefined(IR.name("x")));
  }

  // isAssociative includes AND/OR, isCommutative does not (side-effect ordering matters).
  @Test
  public void testIsAssociativeAndIsCommutative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.ADD));
    assertTrue(NodeUtil.isAssociative(Token.AND));
    assertTrue(NodeUtil.isCommutative(Token.MUL));
    assertFalse(NodeUtil.isCommutative(Token.ADD));
    assertFalse(NodeUtil.isCommutative(Token.AND));
  }

  // isAssignmentOp default false; getOpFromAssignmentOp throws for non-assignment node.
  @Test
  public void testIsAssignmentOpAndGetOpFromAssignmentOp() throws Throwable {
    assertFalse(NodeUtil.isAssignmentOp(IR.name("x")));
    assertFalse(NodeUtil.isAssignmentOp(IR.call(IR.name("f"))));
    try {
      NodeUtil.getOpFromAssignmentOp(IR.name("x"));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // isGet and isCallOrNew true/false branches.
  @Test
  public void testIsGetAndIsCallOrNew() throws Throwable {
    Node getprop = IR.getprop(IR.name("a"), IR.string("b"));
    assertTrue(NodeUtil.isGet(getprop));
    assertFalse(NodeUtil.isGet(IR.name("a")));
    assertTrue(NodeUtil.isCallOrNew(IR.call(IR.name("f"))));
    assertFalse(NodeUtil.isCallOrNew(IR.name("f")));
  }

  // isVarDeclaration true when parent is VAR, false otherwise.
  @Test
  public void testIsVarDeclaration() throws Throwable {
    Node nameNode = IR.name("x");
    IR.var(nameNode);
    assertTrue(NodeUtil.isVarDeclaration(nameNode));
    Node other = IR.name("y");
    IR.exprResult(other);
    assertFalse(NodeUtil.isVarDeclaration(other));
  }

  // getAssignedValue: precondition, VAR child value, non-var/assign parent returns null.
  @Test
  public void testGetAssignedValue() throws Throwable {
    try {
      NodeUtil.getAssignedValue(IR.string("s"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
    Node nameNode = IR.name("x");
    Node value = IR.number(5);
    nameNode.addChildToBack(value);
    IR.var(nameNode);
    assertSame(value, NodeUtil.getAssignedValue(nameNode));
    Node other = IR.name("y");
    IR.exprResult(other);
    assertNull(NodeUtil.getAssignedValue(other));
  }

  // isExprAssign false when no ASSIGN present; isExprCall true/false.
  @Test
  public void testIsExprAssignAndIsExprCall() throws Throwable {
    Node exprCall = IR.exprResult(IR.call(IR.name("f")));
    assertFalse(NodeUtil.isExprAssign(exprCall));
    assertTrue(NodeUtil.isExprCall(exprCall));
    Node exprName = IR.exprResult(IR.name("x"));
    assertFalse(NodeUtil.isExprCall(exprName));
  }

  // getLoopCodeBlock default returns null for non-loop node types.
  @Test
  public void testGetLoopCodeBlock_default() throws Throwable {
    assertNull(NodeUtil.getLoopCodeBlock(IR.block()));
    assertNull(NodeUtil.getLoopCodeBlock(IR.call(IR.name("f"))));
  }

  // isWithinLoop is false for a node with no loop ancestors.
  @Test
  public void testIsWithinLoop_standalone() throws Throwable {
    assertFalse(NodeUtil.isWithinLoop(IR.name("x")));
  }

  // isStatementBlock true for SCRIPT/BLOCK, false otherwise.
  @Test
  public void testIsStatementBlock() throws Throwable {
    assertTrue(NodeUtil.isStatementBlock(IR.block()));
    assertFalse(NodeUtil.isStatementBlock(IR.call(IR.name("f"))));
  }

  // isStatement true under BLOCK, false otherwise, precondition throws without parent.
  @Test
  public void testIsStatement() throws Throwable {
    Node block = IR.block();
    Node exprStmt = IR.exprResult(IR.call(IR.name("f")));
    block.addChildToBack(exprStmt);
    assertTrue(NodeUtil.isStatement(exprStmt));
    assertFalse(NodeUtil.isStatement(exprStmt.getFirstChild()));
    try {
      NodeUtil.isStatement(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // isReferenceName: non-empty name true, empty name false.
  @Test
  public void testIsReferenceName() throws Throwable {
    assertTrue(NodeUtil.isReferenceName(IR.name("x")));
    assertFalse(NodeUtil.isReferenceName(IR.name("")));
  }

  // removeChild: statement removal, block emptying, var cascade removal, invalid structure throws.
  @Test
  public void testRemoveChild_variants() throws Throwable {
    Node block = IR.block();
    Node exprStmt = IR.exprResult(IR.call(IR.name("f")));
    block.addChildToBack(exprStmt);
    NodeUtil.removeChild(block, exprStmt);
    assertFalse(block.hasChildren());
    Node outer = IR.block();
    Node inner = IR.block();
    outer.addChildToBack(inner);
    inner.addChildToBack(IR.exprResult(IR.call(IR.name("g"))));
    NodeUtil.removeChild(outer, inner);
    assertTrue(outer.hasChildren());
    assertFalse(inner.hasChildren());
  }

  // removeChild: var with single name child cascades removal of the VAR statement itself.
  @Test
  public void testRemoveChild_varCascade() throws Throwable {
    Node block = IR.block();
    Node nameNode = IR.name("x");
    Node varNode = IR.var(nameNode);
    block.addChildToBack(varNode);
    NodeUtil.removeChild(varNode, nameNode);
    assertFalse(block.hasChildren());
  }

  // removeChild throws for an unsupported node/parent combination.
  @Test
  public void testRemoveChild_invalidThrows() throws Throwable {
    Node call = IR.call(IR.name("f"));
    Node target = call.getFirstChild();
    try {
      NodeUtil.removeChild(call, target);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // tryMergeBlock: success merges children into parent block and removes the block.
  @Test
  public void testTryMergeBlock_success() throws Throwable {
    Node outer = IR.block();
    Node inner = IR.block();
    outer.addChildToBack(inner);
    Node stmt = IR.exprResult(IR.call(IR.name("f")));
    inner.addChildToBack(stmt);
    boolean result = NodeUtil.tryMergeBlock(inner);
    assertTrue(result);
    assertTrue(outer.hasChildren());
    assertTrue(outer.getFirstChild().isExprResult());
  }

  // tryMergeBlock: false when parent is not a statement block; precondition throws for non-block.
  @Test
  public void testTryMergeBlock_falseAndPrecondition() throws Throwable {
    Node call = IR.call(IR.name("f"));
    Node blk = IR.block();
    call.addChildToBack(blk);
    assertFalse(NodeUtil.tryMergeBlock(blk));
    try {
      NodeUtil.tryMergeBlock(IR.name("x"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // getFunctionBody precondition requires a FUNCTION node.
  @Test
  public void testGetFunctionBody_precondition() throws Throwable {
    try {
      NodeUtil.getFunctionBody(IR.name("x"));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // isObjectCallMethod/isFunctionObjectCall/isFunctionObjectApply via GETPROP targets.
  @Test
  public void testIsObjectCallMethodVariants() throws Throwable {
    Node callCall = IR.call(IR.getprop(IR.name("a"), IR.string("call")));
    assertTrue(NodeUtil.isFunctionObjectCall(callCall));
    Node applyCall = IR.call(IR.getprop(IR.name("a"), IR.string("apply")));
    assertTrue(NodeUtil.isFunctionObjectApply(applyCall));
    Node otherCall = IR.call(IR.getprop(IR.name("a"), IR.string("foo")));
    assertFalse(NodeUtil.isObjectCallMethod(otherCall, "call"));
  }

  // isVarOrSimpleAssignLhs: true when parent is VAR, false otherwise.
  @Test
  public void testIsVarOrSimpleAssignLhs() throws Throwable {
    Node varNode = IR.var(IR.name("x"));
    assertTrue(NodeUtil.isVarOrSimpleAssignLhs(IR.name("y"), varNode));
    Node block = IR.block();
    assertFalse(NodeUtil.isVarOrSimpleAssignLhs(IR.name("y"), block));
  }

  // isLValue: precondition, parent VAR true, no parent false.
  @Test
  public void testIsLValue() throws Throwable {
    try {
      NodeUtil.isLValue(IR.number(5));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
    Node nameNode = IR.name("x");
    IR.var(nameNode);
    assertTrue(NodeUtil.isLValue(nameNode));
    assertFalse(NodeUtil.isLValue(IR.name("y")));
  }

  // opToStr maps known operators and returns null for unknown; opToStrNoFail throws for unknown.
  @Test
  public void testOpToStrAndOpToStrNoFail() throws Throwable {
    assertEquals("|", NodeUtil.opToStr(Token.BITOR));
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertNull(NodeUtil.opToStr(Token.SCRIPT));
    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
    try {
      NodeUtil.opToStrNoFail(Token.SCRIPT);
      fail("expected Error");
    } catch (Error expected) {
    }
  }

  // containsType finds a matching descendant type and correctly reports absence.
  @Test
  public void testContainsType() throws Throwable {
    Node block = IR.block();
    block.addChildToBack(IR.exprResult(IR.call(IR.name("f"))));
    assertTrue(NodeUtil.containsType(block, Token.CALL));
    assertFalse(NodeUtil.containsType(block, Token.STRING));
  }

  // isNameReferenced / getNameReferenceCount count occurrences of a simple name.
  @Test
  public void testNameReferenceHelpers() throws Throwable {
    Node block = IR.block();
    block.addChildToBack(IR.exprResult(IR.name("x")));
    block.addChildToBack(IR.exprResult(IR.name("x")));
    assertTrue(NodeUtil.isNameReferenced(block, "x"));
    assertEquals(2, NodeUtil.getNameReferenceCount(block, "x"));
    assertEquals(0, NodeUtil.getNameReferenceCount(block, "y"));
  }

  // newUndefinedNode builds "void 0" which stringifies to "undefined".
  @Test
  public void testNewUndefinedNode() throws Throwable {
    Node result = NodeUtil.newUndefinedNode(null);
    assertEquals("undefined", NodeUtil.getStringValue(result));
  }

  // newVarNode attaches the value as a child of the name node when provided.
  @Test
  public void testNewVarNode() throws Throwable {
    Node value = IR.number(5);
    Node var = NodeUtil.newVarNode("x", value);
    assertTrue(var.isVar());
    Node nameChild = var.getFirstChild();
    assertEquals("x", nameChild.getString());
    assertSame(value, nameChild.getFirstChild());
    Node var2 = NodeUtil.newVarNode("y", null);
    assertFalse(var2.getFirstChild().hasChildren());
  }

  // getRootOfQualifiedName walks GETPROP chains to the root NAME; throws for unrelated nodes.
  @Test
  public void testGetRootOfQualifiedName() throws Throwable {
    Node root = IR.name("a");
    Node prop = IR.getprop(root, IR.string("b"));
    assertSame(root, NodeUtil.getRootOfQualifiedName(prop));
    try {
      NodeUtil.getRootOfQualifiedName(IR.call(IR.name("f")));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // getBestLValue returns the NAME parent for a value; default null otherwise.
  @Test
  public void testGetBestLValue() throws Throwable {
    Node nameNode = IR.name("x");
    Node value = IR.number(5);
    nameNode.addChildToBack(value);
    assertSame(nameNode, NodeUtil.getBestLValue(value));
    Node call = IR.call(IR.name("f"));
    assertNull(NodeUtil.getBestLValue(call.getFirstChild()));
  }

  // getRValueOfLValue: VAR case returns the first child, default returns null.
  @Test
  public void testGetRValueOfLValue() throws Throwable {
    Node nameNode = IR.name("x");
    Node value = IR.number(5);
    nameNode.addChildToBack(value);
    IR.var(nameNode);
    assertSame(value, NodeUtil.getRValueOfLValue(nameNode));
    Node call = IR.call(IR.name("f"));
    assertNull(NodeUtil.getRValueOfLValue(call.getFirstChild()));
  }

  // getBestLValueOwner/getBestLValueName: null input, and GETPROP owner/name resolution.
  @Test
  public void testGetBestLValueOwnerAndName() throws Throwable {
    assertNull(NodeUtil.getBestLValueOwner(null));
    assertNull(NodeUtil.getBestLValueName(null));
    Node root = IR.name("a");
    Node prop = IR.getprop(root, IR.string("b"));
    IR.exprResult(prop);
    assertSame(root, NodeUtil.getBestLValueOwner(prop));
    assertEquals("a.b", NodeUtil.getBestLValueName(prop));
  }

  // isExpressionResultUsed: BLOCK/EXPR_RESULT parents mean unused, other parents mean used.
  @Test
  public void testIsExpressionResultUsed() throws Throwable {
    Node block = IR.block();
    Node exprStmt = IR.exprResult(IR.call(IR.name("f")));
    block.addChildToBack(exprStmt);
    assertFalse(NodeUtil.isExpressionResultUsed(exprStmt));
    assertFalse(NodeUtil.isExpressionResultUsed(exprStmt.getFirstChild()));
    Node getprop = IR.getprop(IR.name("a"), IR.string("b"));
    assertTrue(NodeUtil.isExpressionResultUsed(getprop.getFirstChild()));
  }

  // booleanNode builds TRUE/FALSE nodes verified via getPureBooleanValue.
  @Test
  public void testBooleanNode() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(NodeUtil.booleanNode(true)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(NodeUtil.booleanNode(false)));
  }

  // numberNode: normal numbers, NaN/Infinity become special NAME nodes per JS semantics.
  @Test
  public void testNumberNode_specialValues() throws Throwable {
    Node normal = NodeUtil.numberNode(5.0, null);
    assertEquals(5.0, NodeUtil.getNumberValue(normal), 0.0);
    Node nanNode = NodeUtil.numberNode(Double.NaN, null);
    assertEquals("NaN", NodeUtil.getStringValue(nanNode));
    Node posInf = NodeUtil.numberNode(Double.POSITIVE_INFINITY, null);
    assertEquals("Infinity", NodeUtil.getStringValue(posInf));
    Double negVal = NodeUtil.getNumberValue(NodeUtil.numberNode(Double.NEGATIVE_INFINITY, null));
    assertTrue(negVal != null && Double.isInfinite(negVal) && negVal < 0);
  }

  // getArgumentForCallOrNew returns the nth argument or null when out of range.
  @Test
  public void testGetArgumentForCallOrNew() throws Throwable {
    Node call = IR.call(IR.name("f"));
    Node arg0 = IR.number(1);
    Node arg1 = IR.number(2);
    call.addChildToBack(arg0);
    call.addChildToBack(arg1);
    assertSame(arg0, NodeUtil.getArgumentForCallOrNew(call, 0));
    assertSame(arg1, NodeUtil.getArgumentForCallOrNew(call, 1));
    assertNull(NodeUtil.getArgumentForCallOrNew(call, 2));
  }

  // evaluatesToLocalValue: NAME constant true, plain NAME false, toString call true.
  @Test
  public void testEvaluatesToLocalValue_nameAndCall() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(IR.name("undefined")));
    assertFalse(NodeUtil.evaluatesToLocalValue(IR.name("x")));
    Node toStringCall = IR.call(IR.getprop(IR.name("a"), IR.string("toString")));
    assertTrue(NodeUtil.evaluatesToLocalValue(toStringCall));
    assertFalse(NodeUtil.evaluatesToLocalValue(IR.call(IR.name("f"))));
  }

  // evaluatesToLocalValue: default immutable true, unsupported node type throws.
  @Test
  public void testEvaluatesToLocalValue_defaultAndException() throws Throwable {
    assertTrue(NodeUtil.evaluatesToLocalValue(IR.number(5)));
    try {
      NodeUtil.evaluatesToLocalValue(IR.var(IR.name("z")));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // newCallNode sets FREE_CALL based on whether the target is a GET expression.
  @Test
  public void testNewCallNode_freeCallFlag() throws Throwable {
    Node call1 = NodeUtil.newCallNode(IR.name("f"), IR.number(1), IR.number(2));
    assertTrue(call1.getBooleanProp(Node.FREE_CALL));
    assertEquals(3, call1.getChildCount());
    Node call2 = NodeUtil.newCallNode(IR.getprop(IR.name("a"), IR.string("b")));
    assertFalse(call2.getBooleanProp(Node.FREE_CALL));
  }

  // newExpr wraps the given child in an EXPR_RESULT node.
  @Test
  public void testNewExpr() throws Throwable {
    Node child = IR.number(5);
    Node result = NodeUtil.newExpr(child);
    assertTrue(result.isExprResult());
    assertSame(child, result.getFirstChild());
  }

  // isLatin and isValidSimpleName cover ASCII checks, keywords, and invalid identifiers.
  @Test
  public void testIsLatinAndIsValidSimpleName() throws Throwable {
    assertTrue(NodeUtil.isLatin("hello"));
    assertFalse(NodeUtil.isLatin("caf\u00e9"));
    assertTrue(NodeUtil.isValidSimpleName("foo"));
    assertFalse(NodeUtil.isValidSimpleName("var"));
    assertFalse(NodeUtil.isValidSimpleName("1abc"));
  }

  // isValidQualifiedName rejects leading/trailing/empty-part dots; isValidPropertyName mirrors simple name rules.
  @Test
  public void testIsValidQualifiedNameAndPropertyName() throws Throwable {
    assertTrue(NodeUtil.isValidQualifiedName("a.b.c"));
    assertFalse(NodeUtil.isValidQualifiedName(".a"));
    assertFalse(NodeUtil.isValidQualifiedName("a."));
    assertFalse(NodeUtil.isValidQualifiedName("a..b"));
    assertTrue(NodeUtil.isValidPropertyName("valid"));
    assertFalse(NodeUtil.isValidPropertyName("123"));
  }
}
