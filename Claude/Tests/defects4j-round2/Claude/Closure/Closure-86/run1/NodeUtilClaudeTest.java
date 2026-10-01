package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodeUtilClaudeTest {

  // Token.STRING branch: empty -> FALSE, non-empty -> TRUE
  @Test
  public void testGetBooleanValue_string() throws Throwable {
    assertEquals(TernaryValue.FALSE,
        NodeUtil.getBooleanValue(Node.newString(Token.STRING, "")));
    assertEquals(TernaryValue.TRUE,
        NodeUtil.getBooleanValue(Node.newString(Token.STRING, "x")));
  }

  // Token.NUMBER branch: zero -> FALSE, nonzero -> TRUE
  @Test
  public void testGetBooleanValue_number() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(Node.newNumber(0)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(Node.newNumber(-5)));
  }

  // NULL / FALSE / VOID branches -> FALSE
  @Test
  public void testGetBooleanValue_nullFalseVoid() throws Throwable {
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.NULL)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.FALSE)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getBooleanValue(new Node(Token.VOID)));
  }

  // NAME "undefined"/"NaN" -> FALSE
  @Test
  public void testGetBooleanValue_nameUndefinedNaN() throws Throwable {
    assertEquals(TernaryValue.FALSE,
        NodeUtil.getBooleanValue(Node.newString(Token.NAME, "undefined")));
    assertEquals(TernaryValue.FALSE,
        NodeUtil.getBooleanValue(Node.newString(Token.NAME, "NaN")));
  }

  // NAME "Infinity" -> TRUE
  @Test
  public void testGetBooleanValue_nameInfinity() throws Throwable {
    assertEquals(TernaryValue.TRUE,
        NodeUtil.getBooleanValue(Node.newString(Token.NAME, "Infinity")));
  }

  // NAME other (not matched) -> UNKNOWN
  @Test
  public void testGetBooleanValue_nameOtherUnknown() throws Throwable {
    assertEquals(TernaryValue.UNKNOWN,
        NodeUtil.getBooleanValue(Node.newString(Token.NAME, "x")));
  }

  // TRUE/ARRAYLIT/OBJECTLIT/REGEXP -> TRUE
  @Test
  public void testGetBooleanValue_trueArrayObjectRegexp() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.TRUE)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.ARRAYLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.OBJECTLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getBooleanValue(new Node(Token.REGEXP)));
  }

  // NOT branch inverts the operand value
  @Test
  public void testGetExpressionBooleanValue_not() throws Throwable {
    Node notN = new Node(Token.NOT, new Node(Token.FALSE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getExpressionBooleanValue(notN));
  }

  // AND / OR branches combine lhs and rhs values
  @Test
  public void testGetExpressionBooleanValue_andOr() throws Throwable {
    Node andN = new Node(Token.AND, new Node(Token.TRUE));
    andN.addChildToBack(new Node(Token.FALSE));
    assertEquals(TernaryValue.FALSE, NodeUtil.getExpressionBooleanValue(andN));

    Node orN = new Node(Token.OR, new Node(Token.FALSE));
    orN.addChildToBack(new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getExpressionBooleanValue(orN));
  }

  // HOOK branch: same branch values -> that value
  @Test
  public void testGetExpressionBooleanValue_hookSameBranches() throws Throwable {
    Node hookN = new Node(Token.HOOK, Node.newString(Token.NAME, "c"));
    hookN.addChildToBack(new Node(Token.TRUE));
    hookN.addChildToBack(new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getExpressionBooleanValue(hookN));
  }

  // HOOK branch: differing branch values -> UNKNOWN
  @Test
  public void testGetExpressionBooleanValue_hookDifferentBranches() throws Throwable {
    Node hookN = new Node(Token.HOOK, Node.newString(Token.NAME, "c"));
    hookN.addChildToBack(new Node(Token.TRUE));
    hookN.addChildToBack(new Node(Token.FALSE));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getExpressionBooleanValue(hookN));
  }

  // COMMA (and ASSIGN) branch uses last child's value
  @Test
  public void testGetExpressionBooleanValue_commaLastChild() throws Throwable {
    Node commaN = new Node(Token.COMMA, new Node(Token.FALSE));
    commaN.addChildToBack(new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getExpressionBooleanValue(commaN));
  }

  // STRING returns itself, integer NUMBER returns no decimal point
  @Test
  public void testGetStringValue_stringAndIntegerNumber() throws Throwable {
    assertEquals("hello", NodeUtil.getStringValue(Node.newString(Token.STRING, "hello")));
    assertEquals("5", NodeUtil.getStringValue(Node.newNumber(5)));
    assertEquals("-3", NodeUtil.getStringValue(Node.newNumber(-3)));
  }

  // Non-integer NUMBER uses Double.toString
  @Test
  public void testGetStringValue_nonIntegerNumber() throws Throwable {
    assertEquals(Double.toString(1.5), NodeUtil.getStringValue(Node.newNumber(1.5)));
  }

  // NAME "undefined"/"Infinity" recognized, other NAME -> null
  @Test
  public void testGetStringValue_nameUndefinedAndOther() throws Throwable {
    assertEquals("undefined",
        NodeUtil.getStringValue(Node.newString(Token.NAME, "undefined")));
    assertEquals("Infinity",
        NodeUtil.getStringValue(Node.newString(Token.NAME, "Infinity")));
    assertNull(NodeUtil.getStringValue(Node.newString(Token.NAME, "foo")));
  }

  // TRUE/FALSE/NULL/VOID string casts
  @Test
  public void testGetStringValue_trueFalseNullVoid() throws Throwable {
    assertEquals("true", NodeUtil.getStringValue(new Node(Token.TRUE)));
    assertEquals("false", NodeUtil.getStringValue(new Node(Token.FALSE)));
    assertEquals("null", NodeUtil.getStringValue(new Node(Token.NULL)));
    assertEquals("undefined", NodeUtil.getStringValue(new Node(Token.VOID)));
  }

  // TRUE->1, FALSE/NULL->0 Number casts
  @Test
  public void testGetNumberValue_trueFalseNull() throws Throwable {
    assertEquals(1.0, NodeUtil.getNumberValue(new Node(Token.TRUE)), 1e-9);
    assertEquals(0.0, NodeUtil.getNumberValue(new Node(Token.FALSE)), 1e-9);
    assertEquals(0.0, NodeUtil.getNumberValue(new Node(Token.NULL)), 1e-9);
  }

  // NUMBER returns its own value, VOID -> NaN
  @Test
  public void testGetNumberValue_numberAndVoid() throws Throwable {
    assertEquals(3.5, NodeUtil.getNumberValue(Node.newNumber(3.5)), 1e-9);
    assertTrue(Double.isNaN(NodeUtil.getNumberValue(new Node(Token.VOID))));
  }

  // NAME "undefined"/"NaN" -> NaN, "Infinity" -> +Inf, other -> null
  @Test
  public void testGetNumberValue_nameVariants() throws Throwable {
    assertTrue(Double.isNaN(
        NodeUtil.getNumberValue(Node.newString(Token.NAME, "undefined"))));
    assertTrue(Double.isNaN(
        NodeUtil.getNumberValue(Node.newString(Token.NAME, "NaN"))));
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY),
        NodeUtil.getNumberValue(Node.newString(Token.NAME, "Infinity")), 1e-9);
    assertNull(NodeUtil.getNumberValue(Node.newString(Token.NAME, "foo")));
  }

  // Immutable value checks over several literal / NAME cases
  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.STRING, "s")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(5)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "x")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NEG, Node.newNumber(3))));
  }

  // ARRAYLIT literal only if all children are literal
  @Test
  public void testIsLiteralValue_arrayLit() throws Throwable {
    Node arr1 = new Node(Token.ARRAYLIT, Node.newNumber(1));
    arr1.addChildToBack(Node.newNumber(2));
    assertTrue(NodeUtil.isLiteralValue(arr1, false));

    Node arr2 = new Node(Token.ARRAYLIT, Node.newString(Token.NAME, "x"));
    assertFalse(NodeUtil.isLiteralValue(arr2, false));
  }

  // isValidDefineValue: literals true, binary op true, NAME depends on defines set
  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("FOO");

    assertTrue(NodeUtil.isValidDefineValue(Node.newString(Token.STRING, "s"), defines));

    Node addVal = new Node(Token.ADD, Node.newString(Token.STRING, "a"));
    addVal.addChildToBack(Node.newString(Token.STRING, "b"));
    assertTrue(NodeUtil.isValidDefineValue(addVal, defines));

    assertTrue(NodeUtil.isValidDefineValue(Node.newString(Token.NAME, "FOO"), defines));
    assertFalse(NodeUtil.isValidDefineValue(Node.newString(Token.NAME, "BAR"), defines));
    assertFalse(NodeUtil.isValidDefineValue(new Node(Token.OBJECTLIT), defines));
  }

  // isEmptyBlock: empty, only EMPTY children, non-empty, non-block
  @Test
  public void testIsEmptyBlock() throws Throwable {
    assertTrue(NodeUtil.isEmptyBlock(new Node(Token.BLOCK)));
    assertTrue(NodeUtil.isEmptyBlock(new Node(Token.BLOCK, new Node(Token.EMPTY))));
    assertFalse(NodeUtil.isEmptyBlock(
        new Node(Token.BLOCK, Node.newString(Token.STRING, "x"))));
    assertFalse(NodeUtil.isEmptyBlock(Node.newString(Token.NAME, "x")));
  }

  // simple operator type true/false cases
  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.GETPROP));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.CALL));
  }

  // known precedence levels per operator-precedence table
  @Test
  public void testPrecedence_knownValues() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(12, NodeUtil.precedence(Token.MUL));
    assertEquals(15, NodeUtil.precedence(Token.NAME));
  }

  // unknown type -> Error thrown
  @Test
  public void testPrecedence_unknownThrows() throws Throwable {
    try {
      NodeUtil.precedence(Token.VAR);
      fail("expected Error");
    } catch (Error expected) {
      // expected
    }
  }

  // isAssignmentOp / getOpFromAssignmentOp / associative / commutative
  @Test
  public void testIsAssignmentOpAndAssociativeCommutative() throws Throwable {
    Node assignAdd = new Node(Token.ASSIGN_ADD, Node.newString(Token.NAME, "x"));
    assertTrue(NodeUtil.isAssignmentOp(assignAdd));
    assertEquals(Token.ADD, NodeUtil.getOpFromAssignmentOp(assignAdd));

    assertFalse(NodeUtil.isAssignmentOp(new Node(Token.ADD, Node.newNumber(1))));
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.SUB));
    assertTrue(NodeUtil.isCommutative(Token.BITAND));
    assertFalse(NodeUtil.isCommutative(Token.SUB));
  }

  // plain ASSIGN is not a compound op -> throws IllegalArgumentException
  @Test
  public void testGetOpFromAssignmentOp_invalidThrows() throws Throwable {
    Node assignPlain = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"));
    try {
      NodeUtil.getOpFromAssignmentOp(assignPlain);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  // basic node-type predicate checks
  @Test
  public void testBasicTypePredicates() throws Throwable {
    Node getProp = new Node(Token.GETPROP, Node.newString(Token.NAME, "a"),
        Node.newString(Token.STRING, "b"), 0, 0);
    Node name = Node.newString(Token.NAME, "x");
    Node newNode = new Node(Token.NEW, Node.newString(Token.NAME, "Foo"));
    Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "y"));
    Node strNode = Node.newString(Token.STRING, "s");
    Node assignNode = new Node(Token.ASSIGN, name);
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "f"));
    Node fnNode = new Node(Token.FUNCTION, 0, 0);
    Node thisNode = new Node(Token.THIS);

    assertTrue(NodeUtil.isGet(getProp));
    assertTrue(NodeUtil.isGetProp(getProp));
    assertTrue(NodeUtil.isName(name));
    assertTrue(NodeUtil.isNew(newNode));
    assertTrue(NodeUtil.isVar(varNode));
    assertTrue(NodeUtil.isString(strNode));
    assertTrue(NodeUtil.isAssign(assignNode));
    assertTrue(NodeUtil.isCall(callNode));
    assertTrue(NodeUtil.isCallOrNew(newNode));
    assertTrue(NodeUtil.isFunction(fnNode));
    assertTrue(NodeUtil.isThis(thisNode));
    assertFalse(NodeUtil.isGet(name));
  }

  // isVarDeclaration / getAssignedValue for VAR, ASSIGN and free name
  @Test
  public void testIsVarDeclarationAndGetAssignedValue() throws Throwable {
    Node valNode = Node.newNumber(5);
    Node nameInVar = Node.newString(Token.NAME, "a");
    nameInVar.addChildToBack(valNode);
    new Node(Token.VAR, nameInVar);
    assertTrue(NodeUtil.isVarDeclaration(nameInVar));
    assertSame(valNode, NodeUtil.getAssignedValue(nameInVar));

    Node lhsName = Node.newString(Token.NAME, "b");
    Node rhsVal = Node.newNumber(9);
    Node assign2 = new Node(Token.ASSIGN, lhsName);
    assign2.addChildToBack(rhsVal);
    assertSame(rhsVal, NodeUtil.getAssignedValue(lhsName));

    Node freeName = Node.newString(Token.NAME, "c");
    new Node(Token.BLOCK, freeName);
    assertFalse(NodeUtil.isVarDeclaration(freeName));
    assertNull(NodeUtil.getAssignedValue(freeName));
  }

  // isForIn (3 vs 4 children), isLoopStructure, getLoopCodeBlock
  @Test
  public void testIsForInAndLoopStructure() throws Throwable {
    Node forIn = new Node(Token.FOR, Node.newString(Token.NAME, "k"));
    Node body = new Node(Token.BLOCK);
    forIn.addChildToBack(Node.newString(Token.NAME, "o"));
    forIn.addChildToBack(body);
    assertTrue(NodeUtil.isForIn(forIn));
    assertTrue(NodeUtil.isLoopStructure(forIn));
    assertSame(body, NodeUtil.getLoopCodeBlock(forIn));

    Node forClassic = new Node(Token.FOR, Node.newString(Token.NAME, "i"));
    forClassic.addChildToBack(Node.newNumber(1));
    forClassic.addChildToBack(Node.newNumber(2));
    forClassic.addChildToBack(new Node(Token.BLOCK));
    assertFalse(NodeUtil.isForIn(forClassic));

    Node doBody = new Node(Token.BLOCK);
    Node doNode = new Node(Token.DO, doBody);
    doNode.addChildToBack(Node.newNumber(1));
    assertTrue(NodeUtil.isLoopStructure(doNode));
    assertSame(doBody, NodeUtil.getLoopCodeBlock(doNode));
  }

  // getConditionExpression for IF/WHILE/DO/FOR(3)/FOR(4)/CASE
  @Test
  public void testGetConditionExpression() throws Throwable {
    Node ifCond = Node.newNumber(1);
    Node ifNode = new Node(Token.IF, ifCond);
    ifNode.addChildToBack(new Node(Token.BLOCK));
    assertSame(ifCond, NodeUtil.getConditionExpression(ifNode));

    Node whileCond = Node.newNumber(1);
    Node whileNode = new Node(Token.WHILE, whileCond);
    whileNode.addChildToBack(new Node(Token.BLOCK));
    assertSame(whileCond, NodeUtil.getConditionExpression(whileNode));

    Node doCond = Node.newNumber(1);
    Node doNode = new Node(Token.DO, new Node(Token.BLOCK));
    doNode.addChildToBack(doCond);
    assertSame(doCond, NodeUtil.getConditionExpression(doNode));

    Node forIn = new Node(Token.FOR, Node.newString(Token.NAME, "k"));
    forIn.addChildToBack(Node.newString(Token.NAME, "o"));
    forIn.addChildToBack(new Node(Token.BLOCK));
    assertNull(NodeUtil.getConditionExpression(forIn));

    Node forCond = Node.newNumber(1);
    Node forClassic = new Node(Token.FOR, Node.newString(Token.NAME, "i"));
    forClassic.addChildToBack(forCond);
    forClassic.addChildToBack(Node.newNumber(2));
    forClassic.addChildToBack(new Node(Token.BLOCK));
    assertSame(forCond, NodeUtil.getConditionExpression(forClassic));

    Node caseNode = new Node(Token.CASE, Node.newNumber(1));
    caseNode.addChildToBack(new Node(Token.BLOCK));
    assertNull(NodeUtil.getConditionExpression(caseNode));
  }

  // unsupported node type -> IllegalArgumentException
  @Test
  public void testGetConditionExpression_invalidThrows() throws Throwable {
    try {
      NodeUtil.getConditionExpression(new Node(Token.BLOCK));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  // function declaration (statement) vs expression, and empty function expression
  @Test
  public void testIsFunctionDeclarationExpressionAndEmpty() throws Throwable {
    Node fnDecl = new Node(Token.FUNCTION, 0, 0);
    new Node(Token.BLOCK, fnDecl);
    assertTrue(NodeUtil.isFunctionDeclaration(fnDecl));
    assertFalse(NodeUtil.isFunctionExpression(fnDecl));

    Node fnExprEmpty = new Node(Token.FUNCTION, 0, 0);
    fnExprEmpty.addChildToBack(new Node(Token.BLOCK));
    new Node(Token.CALL, fnExprEmpty);
    assertFalse(NodeUtil.isFunctionDeclaration(fnExprEmpty));
    assertTrue(NodeUtil.isFunctionExpression(fnExprEmpty));
    assertTrue(NodeUtil.isEmptyFunctionExpression(fnExprEmpty));

    Node fnExprNonEmpty = new Node(Token.FUNCTION, 0, 0);
    fnExprNonEmpty.addChildToBack(
        new Node(Token.BLOCK, Node.newString(Token.STRING, "x")));
    new Node(Token.CALL, fnExprNonEmpty);
    assertFalse(NodeUtil.isEmptyFunctionExpression(fnExprNonEmpty));
  }

  // x.call(...) / x.apply(...) detection
  @Test
  public void testFunctionObjectCallApply() throws Throwable {
    Node getpropCall = new Node(Token.GETPROP, Node.newString(Token.NAME, "x"),
        Node.newString(Token.STRING, "call"), 0, 0);
    Node callNode = new Node(Token.CALL, getpropCall);
    assertTrue(NodeUtil.isFunctionObjectCall(callNode));
    assertTrue(NodeUtil.isFunctionObjectCallOrApply(callNode));
    assertTrue(NodeUtil.isSimpleFunctionObjectCall(callNode));

    Node getpropApply = new Node(Token.GETPROP, Node.newString(Token.NAME, "y"),
        Node.newString(Token.STRING, "apply"), 0, 0);
    Node applyNode = new Node(Token.CALL, getpropApply);
    assertTrue(NodeUtil.isFunctionObjectApply(applyNode));
    assertFalse(NodeUtil.isFunctionObjectCall(applyNode));
  }

  // opToStr known / unknown, opToStrNoFail known / throws
  @Test
  public void testOpToStrAndOpToStrNoFail() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertNull(NodeUtil.opToStr(Token.BLOCK));
    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
    try {
      NodeUtil.opToStrNoFail(Token.BLOCK);
      fail("expected Error");
    } catch (Error expected) {
      // expected
    }
  }

  // isLatin ascii vs non-ascii, isValidPropertyName identifier rules
  @Test
  public void testIsLatinAndIsValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isLatin("abc"));
    assertFalse(NodeUtil.isLatin("caf\u00e9"));
    assertTrue(NodeUtil.isValidPropertyName("foo"));
    assertFalse(NodeUtil.isValidPropertyName("var"));
    assertFalse(NodeUtil.isValidPropertyName("123abc"));
  }

  // getPrototypeClassName / getPrototypePropertyName / isPrototypeProperty
  @Test
  public void testPrototypeHelpers() throws Throwable {
    Node fooName = Node.newString(Token.NAME, "Foo");
    Node protoStr = Node.newString(Token.STRING, "prototype");
    Node innerGetProp = new Node(Token.GETPROP, fooName, protoStr, 0, 0);
    Node barStr = Node.newString(Token.STRING, "bar");
    Node outerGetProp = new Node(Token.GETPROP, innerGetProp, barStr, 0, 0);

    Node classNode = NodeUtil.getPrototypeClassName(outerGetProp);
    assertEquals("Foo", classNode.getString());
    assertEquals("bar", NodeUtil.getPrototypePropertyName(outerGetProp));
    assertTrue(NodeUtil.isPrototypeProperty(outerGetProp));

    Node simpleGetProp = new Node(Token.GETPROP, Node.newString(Token.NAME, "Foo2"),
        Node.newString(Token.STRING, "bar2"), 0, 0);
    assertFalse(NodeUtil.isPrototypeProperty(simpleGetProp));
  }

  // newUndefinedNode builds "void 0", newVarNode wraps NAME with value
  @Test
  public void testNewUndefinedNodeAndNewVarNode() throws Throwable {
    Node undef = NodeUtil.newUndefinedNode(null);
    assertEquals(Token.VOID, undef.getType());
    assertEquals(Token.NUMBER, undef.getFirstChild().getType());
    assertEquals(0.0, undef.getFirstChild().getDouble(), 1e-9);

    Node valueNode = Node.newNumber(42);
    Node varNode = NodeUtil.newVarNode("x", valueNode);
    assertEquals(Token.VAR, varNode.getType());
    Node nameNode = varNode.getFirstChild();
    assertEquals(Token.NAME, nameNode.getType());
    assertEquals("x", nameNode.getString());
    assertSame(valueNode, nameNode.getFirstChild());
  }

  // newFunctionNode builds params/body, getFnParameters/getFunctionBody read them back
  @Test
  public void testFnParametersAndNewFunctionNode() throws Throwable {
    List<Node> params = new ArrayList<Node>();
    params.add(Node.newString(Token.NAME, "a"));
    Node body = new Node(Token.BLOCK);
    Node fn = NodeUtil.newFunctionNode("foo", params, body, 1, 1);

    assertEquals("foo", fn.getFirstChild().getString());
    Node lp = NodeUtil.getFnParameters(fn);
    assertEquals(Token.LP, lp.getType());
    assertEquals(1, lp.getChildCount());
    assertEquals("a", lp.getFirstChild().getString());
    assertSame(body, NodeUtil.getFunctionBody(fn));
  }

  // constructorCallHasSideEffects: builtin ctor -> false, other -> true, wrong type throws
  @Test
  public void testConstructorCallHasSideEffects() throws Throwable {
    Node newArray = new Node(Token.NEW, Node.newString(Token.NAME, "Array"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(newArray));

    Node newFoo = new Node(Token.NEW, Node.newString(Token.NAME, "Foo"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(newFoo));

    try {
      NodeUtil.constructorCallHasSideEffects(new Node(Token.BLOCK));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  // functionCallHasSideEffects: builtin fn -> false, other -> true, wrong type throws
  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node callString = new Node(Token.CALL, Node.newString(Token.NAME, "String"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callString));

    Node callFoo = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    assertTrue(NodeUtil.functionCallHasSideEffects(callFoo));

    try {
      NodeUtil.functionCallHasSideEffects(new Node(Token.BLOCK));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  // mayHaveSideEffects vs mayEffectMutableState on NUMBER / OBJECTLIT / CALL
  @Test
  public void testMayHaveSideEffectsAndMutableState() throws Throwable {
    Node numberNode = Node.newNumber(5);
    assertFalse(NodeUtil.mayHaveSideEffects(numberNode));
    assertFalse(NodeUtil.mayEffectMutableState(numberNode));

    Node emptyObjLit = new Node(Token.OBJECTLIT);
    assertTrue(NodeUtil.mayEffectMutableState(emptyObjLit));
    assertFalse(NodeUtil.mayHaveSideEffects(emptyObjLit));

    Node callFoo = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    assertTrue(NodeUtil.mayHaveSideEffects(callFoo));
  }

  // removeChild on a statement block, tryMergeBlock merges/doesn't merge
  @Test
  public void testRemoveChildAndTryMergeBlock() throws Throwable {
    Node strChild = Node.newString(Token.STRING, "x");
    Node block = new Node(Token.BLOCK, strChild);
    NodeUtil.removeChild(block, strChild);
    assertFalse(block.hasChildren());

    Node inner = new Node(Token.BLOCK);
    inner.addChildToBack(Node.newNumber(1));
    inner.addChildToBack(Node.newNumber(2));
    Node outer = new Node(Token.BLOCK, inner);
    assertTrue(NodeUtil.tryMergeBlock(inner));
    assertEquals(2, outer.getChildCount());

    Node labelBlock = new Node(Token.BLOCK);
    Node label = new Node(Token.LABEL, Node.newString(Token.LABEL_NAME, "L"));
    label.addChildToBack(labelBlock);
    assertFalse(NodeUtil.tryMergeBlock(labelBlock));
  }

  // evaluatesToLocalValue across NEW / ARRAYLIT / NAME / GETPROP
  @Test
  public void testEvaluatesToLocalValue() throws Throwable {
    Node newNode = new Node(Token.NEW, Node.newString(Token.NAME, "Foo"));
    assertTrue(NodeUtil.evaluatesToLocalValue(newNode));

    assertTrue(NodeUtil.evaluatesToLocalValue(new Node(Token.ARRAYLIT)));
    assertTrue(NodeUtil.evaluatesToLocalValue(Node.newString(Token.NAME, "undefined")));
    assertFalse(NodeUtil.evaluatesToLocalValue(Node.newString(Token.NAME, "x")));

    Node getPropNode = new Node(Token.GETPROP, Node.newString(Token.NAME, "a"),
        Node.newString(Token.STRING, "b"), 0, 0);
    assertFalse(NodeUtil.evaluatesToLocalValue(getPropNode));
  }
}
