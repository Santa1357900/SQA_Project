package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FoldConstantsClaudeTest {

  private Compiler compiler;
  private FoldConstants foldConstants;
  private NodeTraversal t;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    foldConstants = new FoldConstants(compiler);
    t = new NodeTraversal(compiler, foldConstants);
  }

  private Node exprResultParent(Node n) {
    return new Node(Token.EXPR_RESULT, n);
  }

  private Node wrapAssignInBlock(Node rhs) {
    Node assign = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"), rhs);
    Node exprResult = new Node(Token.EXPR_RESULT, assign);
    return new Node(Token.BLOCK, exprResult);
  }

  private Node getAssignRhs(Node block) {
    return block.getFirstChild().getFirstChild().getLastChild();
  }

  // tryFoldBlock: removes side-effect-free statement, keeps statement with side effects
  @Test
  public void testTryFoldBlock_removesSideEffectFreeChildren() throws Throwable {
    Node deadStmt = new Node(Token.EXPR_RESULT, Node.newNumber(5));
    Node liveStmt = new Node(Token.EXPR_RESULT, new Node(Token.CALL, Node.newString(Token.NAME, "foo")));
    Node block = new Node(Token.BLOCK, deadStmt);
    block.addChildToBack(liveStmt);
    foldConstants.tryFoldBlock(t, block, null);
    assertEquals(1, block.getChildCount());
    assertEquals(liveStmt, block.getFirstChild());
  }

  // tryFoldHookIf: if(true){X} with no else -> replaced by X
  @Test
  public void testTryFoldHookIf_ifTrueNoElse_replacesWithThen() throws Throwable {
    Node condTrue = new Node(Token.TRUE);
    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    Node thenBlock = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, call));
    Node ifNode = new Node(Token.IF, condTrue, thenBlock);
    Node parentBlock = new Node(Token.BLOCK, ifNode);
    foldConstants.tryFoldHookIf(t, ifNode, parentBlock);
    assertEquals(thenBlock, parentBlock.getFirstChild());
  }

  // tryFoldHookIf: if(false){X} with no else -> if removed entirely
  @Test
  public void testTryFoldHookIf_ifFalseNoElse_removesIf() throws Throwable {
    Node condFalse = new Node(Token.FALSE);
    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    Node thenBlock = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, call));
    Node ifNode = new Node(Token.IF, condFalse, thenBlock);
    Node parentBlock = new Node(Token.BLOCK, ifNode);
    foldConstants.tryFoldHookIf(t, ifNode, parentBlock);
    assertEquals(0, parentBlock.getChildCount());
  }

  // tryFoldHookIf: true ? A : B -> A (HOOK, non-expression parent)
  @Test
  public void testTryFoldHookIf_hookTrueLiteral_choosesThenBranch() throws Throwable {
    Node condTrue = new Node(Token.TRUE);
    Node branchA = Node.newString("A");
    Node branchB = Node.newString("B");
    Node hook = new Node(Token.HOOK, condTrue, branchA, branchB);
    Node assign = new Node(Token.ASSIGN, Node.newString(Token.NAME, "z"), hook);
    boolean changed = foldConstants.tryFoldHookIf(t, hook, assign);
    assertTrue(changed);
    assertEquals(branchA, assign.getLastChild());
  }

  // tryMinimizeIf: if(!x)bar(); -> x||bar();
  @Test
  public void testTryMinimizeIf_notConditionSingleCall_convertsToOr() throws Throwable {
    Node cond = new Node(Token.NOT, Node.newString(Token.NAME, "x"));
    Node barCall = new Node(Token.CALL, Node.newString(Token.NAME, "bar"));
    Node thenBlock = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, barCall));
    Node ifNode = new Node(Token.IF, cond, thenBlock);
    Node parentBlock = new Node(Token.BLOCK, ifNode);
    foldConstants.tryMinimizeIf(t, ifNode, parentBlock);
    Node orNode = parentBlock.getFirstChild().getFirstChild();
    assertEquals(Token.OR, orNode.getType());
  }

  // tryFoldAndOr: (FALSE && x) => FALSE
  @Test
  public void testTryFoldAndOr_falseAnd_resultsFalse() throws Throwable {
    Node left = new Node(Token.FALSE);
    Node right = Node.newString(Token.NAME, "y");
    Node andNode = new Node(Token.AND, left, right);
    Node parent = exprResultParent(andNode);
    foldConstants.tryFoldAndOr(t, andNode, left, right, parent);
    assertEquals(Token.FALSE, parent.getFirstChild().getType());
  }

  // tryFoldAndOr: (TRUE || x) => TRUE
  @Test
  public void testTryFoldAndOr_trueOr_resultsTrue() throws Throwable {
    Node left = new Node(Token.TRUE);
    Node right = Node.newString(Token.NAME, "y");
    Node orNode = new Node(Token.OR, left, right);
    Node parent = exprResultParent(orNode);
    foldConstants.tryFoldAndOr(t, orNode, left, right, parent);
    assertEquals(Token.TRUE, parent.getFirstChild().getType());
  }

  // tryFoldLeftChildAdd: (foo()+'a')+'b' -> foo()+'ab'
  @Test
  public void testTryFoldLeftChildAdd_mergesAdjacentStrings() throws Throwable {
    Node fooCall = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    Node leftAdd = new Node(Token.ADD, fooCall, Node.newString("a"));
    Node rightB = Node.newString("b");
    Node topAdd = new Node(Token.ADD, leftAdd, rightB);
    foldConstants.tryFoldLeftChildAdd(t, topAdd, leftAdd, rightB, null);
    assertEquals(fooCall, topAdd.getFirstChild());
    assertEquals("ab", topAdd.getLastChild().getString());
  }

  // tryFoldAdd: string + string concatenation
  @Test
  public void testTryFoldAdd_stringConcat() throws Throwable {
    Node l = Node.newString("foo");
    Node r = Node.newString("bar");
    Node addNode = new Node(Token.ADD, l, r);
    Node parent = exprResultParent(addNode);
    foldConstants.tryFoldAdd(t, addNode, l, r, parent);
    assertEquals("foobar", parent.getFirstChild().getString());
  }

  // tryFoldArithmetic: 2 + 3 = 5
  @Test
  public void testTryFoldArithmetic_add() throws Throwable {
    Node l = Node.newNumber(2);
    Node r = Node.newNumber(3);
    Node addNode = new Node(Token.ADD, l, r);
    Node parent = exprResultParent(addNode);
    foldConstants.tryFoldArithmetic(t, addNode, l, r, parent);
    assertEquals(5.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // tryFoldArithmetic: divide by zero reports error, no fold
  @Test
  public void testTryFoldArithmetic_divByZero_noFold() throws Throwable {
    Node l = Node.newNumber(1);
    Node r = Node.newNumber(0);
    Node divNode = new Node(Token.DIV, l, r);
    Node parent = exprResultParent(divNode);
    foldConstants.tryFoldArithmetic(t, divNode, l, r, parent);
    assertEquals(Token.DIV, parent.getFirstChild().getType());
  }

  // tryFoldBitAndOr: 6 & 3 = 2
  @Test
  public void testTryFoldBitAndOr_bitand() throws Throwable {
    Node l = Node.newNumber(6);
    Node r = Node.newNumber(3);
    Node bitandNode = new Node(Token.BITAND, l, r);
    Node parent = exprResultParent(bitandNode);
    foldConstants.tryFoldBitAndOr(t, bitandNode, l, r, parent);
    assertEquals(2.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // tryFoldBitAndOr: operand out of int range -> no fold
  @Test
  public void testTryFoldBitAndOr_outOfRange_noFold() throws Throwable {
    Node l = Node.newNumber(3000000000.0);
    Node r = Node.newNumber(1);
    Node bitandNode = new Node(Token.BITAND, l, r);
    Node parent = exprResultParent(bitandNode);
    foldConstants.tryFoldBitAndOr(t, bitandNode, l, r, parent);
    assertEquals(Token.BITAND, parent.getFirstChild().getType());
  }

  // tryFoldShift: 1 << 3 = 8
  @Test
  public void testTryFoldShift_lsh() throws Throwable {
    Node l = Node.newNumber(1);
    Node r = Node.newNumber(3);
    Node lshNode = new Node(Token.LSH, l, r);
    Node parent = exprResultParent(lshNode);
    foldConstants.tryFoldShift(t, lshNode, l, r, parent);
    assertEquals(8.0, parent.getFirstChild().getDouble(), 1e-9);
  }



  // tryFoldShift: shift amount out of [0,32) -> no fold
  @Test
  public void testTryFoldShift_shiftAmountOutOfBounds_noFold() throws Throwable {
    Node l = Node.newNumber(1);
    Node r = Node.newNumber(32);
    Node lshNode = new Node(Token.LSH, l, r);
    Node parent = exprResultParent(lshNode);
    foldConstants.tryFoldShift(t, lshNode, l, r, parent);
    assertEquals(Token.LSH, parent.getFirstChild().getType());
  }

  // tryFoldComparison: 1 < 2 -> true
  @Test
  public void testTryFoldComparison_numbersLt() throws Throwable {
    Node l = Node.newNumber(1);
    Node r = Node.newNumber(2);
    Node ltNode = new Node(Token.LT, l, r);
    Node parent = exprResultParent(ltNode);
    foldConstants.tryFoldComparison(t, ltNode, l, r, parent);
    assertEquals(Token.TRUE, parent.getFirstChild().getType());
  }

  // tryFoldComparison: "a" == "a" -> true
  @Test
  public void testTryFoldComparison_stringsEq() throws Throwable {
    Node l = Node.newString("a");
    Node r = Node.newString("a");
    Node eqNode = new Node(Token.EQ, l, r);
    Node parent = exprResultParent(eqNode);
    foldConstants.tryFoldComparison(t, eqNode, l, r, parent);
    assertEquals(Token.TRUE, parent.getFirstChild().getType());
  }

  // tryFoldComparison: (void 0) == null -> true per JS loose-equality rule
  @Test
  public void testTryFoldComparison_voidNullEq() throws Throwable {
    Node l = new Node(Token.VOID, Node.newNumber(0));
    Node r = new Node(Token.NULL);
    Node eqNode = new Node(Token.EQ, l, r);
    Node parent = exprResultParent(eqNode);
    foldConstants.tryFoldComparison(t, eqNode, l, r, parent);
    assertEquals(Token.TRUE, parent.getFirstChild().getType());
  }

  // tryFoldStringIndexOf: "abcdef".indexOf("bc") -> 1
  @Test
  public void testTryFoldStringIndexOf_withFromIndex() throws Throwable {
    Node strNode = Node.newString("abcdefbc");
    Node methodName = Node.newString("indexOf");
    Node getProp = new Node(Token.GETPROP, strNode, methodName);
    Node arg = Node.newString("bc");
    Node fromArg = Node.newNumber(3);
    Node callNode = new Node(Token.CALL, getProp, arg, fromArg);
    Node parent = exprResultParent(callNode);
    foldConstants.tryFoldStringIndexOf(t, callNode, getProp, arg, parent);
    assertEquals(6.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // tryFoldStringJoin: ['a','b','c'].join('') -> 'abc'
  @Test
  public void testTryFoldStringJoin_basic() throws Throwable {
    Node arrLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"), Node.newString("c"));
    Node methodName = Node.newString("join");
    Node getProp = new Node(Token.GETPROP, arrLit, methodName);
    Node joinArg = Node.newString("");
    Node callNode = new Node(Token.CALL, getProp, joinArg);
    Node parent = exprResultParent(callNode);
    foldConstants.tryFoldStringJoin(t, callNode, getProp, joinArg, parent);
    assertEquals("abc", parent.getFirstChild().getString());
  }

  // tryFoldGetElem: [10,20,30][1] -> 20
  @Test
  public void testTryFoldGetElem_validIndex() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT, Node.newNumber(10), Node.newNumber(20), Node.newNumber(30));
    Node idx = Node.newNumber(1);
    Node getElemNode = new Node(Token.GETELEM, arr, idx);
    Node parent = exprResultParent(getElemNode);
    foldConstants.tryFoldGetElem(t, getElemNode, arr, idx, parent);
    assertEquals(20.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // tryFoldGetElem: index beyond array length -> no fold, error reported
  @Test
  public void testTryFoldGetElem_outOfBounds_noFold() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newNumber(2));
    Node idx = Node.newNumber(5);
    Node getElemNode = new Node(Token.GETELEM, arr, idx);
    Node parent = exprResultParent(getElemNode);
    foldConstants.tryFoldGetElem(t, getElemNode, arr, idx, parent);
    assertEquals(Token.GETELEM, parent.getFirstChild().getType());
  }

  // tryFoldGetProp: [1,2,3].length -> 3
  @Test
  public void testTryFoldGetProp_arrayLength() throws Throwable {
    Node arr = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newNumber(2), Node.newNumber(3));
    Node lengthProp = Node.newString("length");
    Node getPropNode = new Node(Token.GETPROP, arr, lengthProp);
    Node parent = exprResultParent(getPropNode);
    foldConstants.tryFoldGetProp(t, getPropNode, arr, lengthProp, parent);
    assertEquals(3.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // tryFoldRegularExpressionConstructor: new RegExp('^foo$') -> /^foo$/
  @Test
  public void testTryFoldRegularExpressionConstructor_simplePattern() throws Throwable {
    Node ctorName = Node.newString(Token.NAME, "RegExp");
    Node pattern = Node.newString("^foo$");
    Node newNode = new Node(Token.NEW, ctorName, pattern);
    Node parent = exprResultParent(newNode);
    foldConstants.tryFoldRegularExpressionConstructor(t, newNode, parent);
    assertEquals(Token.REGEXP, parent.getFirstChild().getType());
  }

  // tryFoldRegularExpressionConstructor: invalid flags -> no fold
  @Test
  public void testTryFoldRegularExpressionConstructor_invalidFlags_noFold() throws Throwable {
    Node ctorName = Node.newString(Token.NAME, "RegExp");
    Node pattern = Node.newString("abc");
    Node flags = Node.newString("x");
    Node newNode = new Node(Token.NEW, ctorName, pattern, flags);
    Node parent = exprResultParent(newNode);
    foldConstants.tryFoldRegularExpressionConstructor(t, newNode, parent);
    assertEquals(Token.NEW, parent.getFirstChild().getType());
  }

  // tryFoldWhile: while(false){} -> loop removed
  @Test
  public void testTryFoldWhile_falseCondition_removesLoop() throws Throwable {
    Node condFalse = new Node(Token.FALSE);
    Node body = new Node(Token.BLOCK);
    Node whileNode = new Node(Token.WHILE, condFalse, body);
    Node parentBlock = new Node(Token.BLOCK, whileNode);
    foldConstants.tryFoldWhile(t, whileNode, parentBlock);
    assertEquals(0, parentBlock.getChildCount());
  }

  // tryFoldFor: non-EMPTY initializer -> for-loop untouched
  @Test
  public void testTryFoldFor_nonEmptyInit_noChange() throws Throwable {
    Node init = new Node(Token.VAR, Node.newString(Token.NAME, "i"));
    Node condFalse = new Node(Token.FALSE);
    Node forNode = new Node(Token.FOR, init, condFalse);
    forNode.addChildToBack(new Node(Token.EMPTY));
    forNode.addChildToBack(new Node(Token.BLOCK));
    Node parentBlock = new Node(Token.BLOCK, forNode);
    foldConstants.tryFoldFor(t, forNode, parentBlock);
    assertEquals(1, parentBlock.getChildCount());
  }

  // tryFoldDo: do{X}while(false) -> replaced by body block X
  @Test
  public void testTryFoldDo_falseCondition_replacesWithBody() throws Throwable {
    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "foo"));
    Node body = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, call));
    Node condFalse = new Node(Token.FALSE);
    Node doNode = new Node(Token.DO, body, condFalse);
    Node parentBlock = new Node(Token.BLOCK, doNode);
    foldConstants.tryFoldDo(t, doNode, parentBlock);
    assertEquals(body, parentBlock.getFirstChild());
  }

  // hasBreakOrContinue: body contains BREAK -> true
  @Test
  public void testHasBreakOrContinue_withBreak_true() throws Throwable {
    Node bodyWithBreak = new Node(Token.BLOCK, new Node(Token.BREAK));
    Node doNode = new Node(Token.DO, bodyWithBreak, new Node(Token.TRUE));
    assertTrue(foldConstants.hasBreakOrContinue(doNode));
  }

  // tryMinimizeCondition: !!x -> x
  @Test
  public void testTryMinimizeCondition_doubleNot_eliminates() throws Throwable {
    Node innerX = Node.newString(Token.NAME, "x");
    Node innerNot = new Node(Token.NOT, innerX);
    Node outerNot = new Node(Token.NOT, innerNot);
    Node parent = exprResultParent(outerNot);
    foldConstants.tryMinimizeCondition(t, outerNot, parent);
    assertEquals(innerX, parent.getFirstChild());
  }

  // tryMinimizeCondition: literal TRUE condition root normalizes to number 1
  @Test
  public void testTryMinimizeCondition_trueLiteral_replacesWithNumberOne() throws Throwable {
    Node condTrue = new Node(Token.TRUE);
    Node parent = exprResultParent(condTrue);
    foldConstants.tryMinimizeCondition(t, condTrue, parent);
    assertEquals(1.0, parent.getFirstChild().getDouble(), 1e-9);
  }

  // visit(): !true -> false when NOT is used as a value (not a bare statement)
  @Test
  public void testVisitNot_literalTrue_foldsToFalse() throws Throwable {
    Node rhs = new Node(Token.NOT, new Node(Token.TRUE));
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    assertEquals(Token.FALSE, getAssignRhs(block).getType());
  }

  // visit(): !(a==b) -> a!=b
  @Test
  public void testVisitNot_eqChild_convertsToNe() throws Throwable {
    Node eq = new Node(Token.EQ, Node.newString(Token.NAME, "a"), Node.newString(Token.NAME, "b"));
    Node notEq = new Node(Token.NOT, eq);
    Node block = wrapAssignInBlock(notEq);
    foldConstants.process(null, block);
    assertEquals(Token.NE, getAssignRhs(block).getType());
  }

  // visit(): -5 (unary minus on literal) -> -5
  @Test
  public void testVisitNeg_literalNumber_foldsToNegative() throws Throwable {
    Node rhs = new Node(Token.NEG, Node.newNumber(5));
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    Node result = getAssignRhs(block);
    assertEquals(-5.0, result.getDouble(), 1e-9);
  }

  // visit(): -Infinity must not be folded away
  @Test
  public void testVisitNeg_infinityName_noFold() throws Throwable {
    Node infinityName = Node.newString(Token.NAME, "Infinity");
    Node rhs = new Node(Token.NEG, infinityName);
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    assertEquals(Token.NEG, getAssignRhs(block).getType());
  }

  // visit(): -NaN -> NaN
  @Test
  public void testVisitNeg_nanName_foldsToNanName() throws Throwable {
    Node nanName = Node.newString(Token.NAME, "NaN");
    Node rhs = new Node(Token.NEG, nanName);
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    Node result = getAssignRhs(block);
    assertEquals("NaN", result.getString());
  }

  // visit(): ~5 -> -6
  @Test
  public void testVisitBitnot_literalNumber_foldsToComplement() throws Throwable {
    Node rhs = new Node(Token.BITNOT, Node.newNumber(5));
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    assertEquals(-6.0, getAssignRhs(block).getDouble(), 1e-9);
  }

  // visit(): typeof "hi" -> "string"
  @Test
  public void testVisitTypeof_string_foldsToStringLiteral() throws Throwable {
    Node rhs = new Node(Token.TYPEOF, Node.newString("hi"));
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    assertEquals("string", getAssignRhs(block).getString());
  }

  // visit(): typeof null -> "object"
  @Test
  public void testVisitTypeof_nullValue_foldsToObjectLiteral() throws Throwable {
    Node rhs = new Node(Token.TYPEOF, new Node(Token.NULL));
    Node block = wrapAssignInBlock(rhs);
    foldConstants.process(null, block);
    assertEquals("object", getAssignRhs(block).getString());
  }

  // visit(): x = x + y -> x += y
  @Test
  public void testVisitAssign_xPlusY_convertsToAssignAdd() throws Throwable {
    Node xName1 = Node.newString(Token.NAME, "x");
    Node xName2 = Node.newString(Token.NAME, "x");
    Node yName = Node.newString(Token.NAME, "y");
    Node addExpr = new Node(Token.ADD, xName2, yName);
    Node assign = new Node(Token.ASSIGN, xName1, addExpr);
    Node block = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, assign));
    foldConstants.process(null, block);
    assertEquals(Token.ASSIGN_ADD, block.getFirstChild().getFirstChild().getType());
  }

  // visit(): return undefined; -> return;
  @Test
  public void testVisitReturn_undefinedName_reducesToBareReturn() throws Throwable {
    Node undefinedName = Node.newString(Token.NAME, "undefined");
    Node returnNode = new Node(Token.RETURN, undefinedName);
    Node block = new Node(Token.BLOCK, returnNode);
    foldConstants.process(null, block);
    assertFalse(returnNode.hasChildren());
  }

  // visit(): return void 0; -> return;
  @Test
  public void testVisitReturn_voidZero_reducesToBareReturn() throws Throwable {
    Node voidExpr = new Node(Token.VOID, Node.newNumber(0));
    Node returnNode = new Node(Token.RETURN, voidExpr);
    Node block = new Node(Token.BLOCK, returnNode);
    foldConstants.process(null, block);
    assertFalse(returnNode.hasChildren());
  }

  // visit(): return 5; stays unchanged (neither VOID nor "undefined")
  @Test
  public void testVisitReturn_numberLiteral_noReduce() throws Throwable {
    Node five = Node.newNumber(5);
    Node returnNode = new Node(Token.RETURN, five);
    Node block = new Node(Token.BLOCK, returnNode);
    foldConstants.process(null, block);
    assertTrue(returnNode.hasChildren());
    assertEquals(5.0, returnNode.getFirstChild().getDouble(), 1e-9);
  }

  // visit(): new Array() -> []
  @Test
  public void testVisitNewArray_foldsToArrayLiteral() throws Throwable {
    Node ctorArray = Node.newString(Token.NAME, "Array");
    Node newArrayNode = new Node(Token.NEW, ctorArray);
    Node block = wrapAssignInBlock(newArrayNode);
    foldConstants.process(null, block);
    assertEquals(Token.ARRAYLIT, getAssignRhs(block).getType());
  }
}
