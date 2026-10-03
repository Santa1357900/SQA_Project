package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class MinimizeExitPointsClaudeTest {

  private Compiler compiler;
  private MinimizeExitPoints pass;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    pass = new MinimizeExitPoints(compiler);
  }

  private Node block(Node... stmts) {
    Node b = IR.block();
    for (Node s : stmts) {
      b.addChildToBack(s);
    }
    return b;
  }

  private Node ifNode(Node cond, Node trueBlock, Node falseBlock) {
    Node n = new Node(Token.IF);
    n.addChildToBack(cond);
    n.addChildToBack(trueBlock);
    if (falseBlock != null) {
      n.addChildToBack(falseBlock);
    }
    return n;
  }

  private Node ret() {
    return new Node(Token.RETURN);
  }

  private Node breakStmt(String label) {
    Node b = new Node(Token.BREAK);
    if (label != null) {
      b.addChildToBack(Node.newString(Token.NAME, label));
    }
    return b;
  }

  // Tests the constructor assigns the compiler field.
  @Test
  public void testConstructor_storesCompilerReference() throws Throwable {
    Compiler c2 = new Compiler();
    MinimizeExitPoints p2 = new MinimizeExitPoints(c2);
    assertSame(c2, p2.compiler);
  }

  // Branch: visit() case Token.FUNCTION, delegates to tryMinimizeExits on body.
  @Test
  public void testVisit_functionNode_minimizesReturnInBody() throws Throwable {
    Node body = block(ret());
    Node fn = new Node(Token.FUNCTION);
    fn.addChildToBack(IR.name("f"));
    fn.addChildToBack(IR.name("params"));
    fn.addChildToBack(body);
    pass.visit(null, fn, null);
    assertFalse(body.hasChildren());
  }

  // Branch: visit() case Token.LABEL, uses first child's string as label name.
  @Test
  public void testVisit_labelNode_minimizesBreakWithLabelName() throws Throwable {
    Node body = block(breakStmt("L"));
    Node labelN = new Node(Token.LABEL);
    labelN.addChildToBack(Node.newString(Token.NAME, "L"));
    labelN.addChildToBack(body);
    pass.visit(null, labelN, null);
    assertFalse(body.hasChildren());
  }

  // Branch: visit() switch default (no matching case) causes no change.
  @Test
  public void testVisit_unrelatedNodeType_noStructuralChange() throws Throwable {
    Node name = IR.name("x");
    pass.visit(null, name, null);
    assertFalse(name.hasChildren());
  }

  // Branch: matchingExitNode true for bare RETURN -> node removed.
  @Test
  public void testTryMinimizeExits_bareReturn_removedFromParent() throws Throwable {
    Node parent = block();
    Node r = ret();
    parent.addChildToBack(r);
    pass.tryMinimizeExits(r, Token.RETURN, null);
    assertFalse(parent.hasChildren());
  }

  // Branch: RETURN with a value is not a matching exit, no removal.
  @Test
  public void testTryMinimizeExits_returnWithValue_notRemoved() throws Throwable {
    Node parent = block();
    Node r = ret();
    r.addChildToBack(IR.name("x"));
    parent.addChildToBack(r);
    pass.tryMinimizeExits(r, Token.RETURN, null);
    assertTrue(parent.hasChildren());
    assertSame(r, parent.getFirstChild());
  }

  // Branch: matchingExitNode true for bare CONTINUE -> node removed.
  @Test
  public void testTryMinimizeExits_bareContinue_removedFromParent() throws Throwable {
    Node parent = block();
    Node c = new Node(Token.CONTINUE);
    parent.addChildToBack(c);
    pass.tryMinimizeExits(c, Token.CONTINUE, null);
    assertFalse(parent.hasChildren());
  }

  // Branch: BREAK with matching label name -> removed.
  @Test
  public void testTryMinimizeExits_breakMatchingLabel_removedFromParent() throws Throwable {
    Node parent = block();
    Node b = breakStmt("foo");
    parent.addChildToBack(b);
    pass.tryMinimizeExits(b, Token.BREAK, "foo");
    assertFalse(parent.hasChildren());
  }

  // Branch: BREAK with mismatched label name -> not removed.
  @Test
  public void testTryMinimizeExits_breakMismatchedLabel_notRemoved() throws Throwable {
    Node parent = block();
    Node b = breakStmt("foo");
    parent.addChildToBack(b);
    pass.tryMinimizeExits(b, Token.BREAK, "bar");
    assertTrue(parent.hasChildren());
  }

  // Branch: searching with a labelName but node has no children -> not matched.
  @Test
  public void testTryMinimizeExits_labelSearchButNodeHasNoLabel_notRemoved() throws Throwable {
    Node parent = block();
    Node b = new Node(Token.BREAK);
    parent.addChildToBack(b);
    pass.tryMinimizeExits(b, Token.BREAK, "foo");
    assertTrue(parent.hasChildren());
  }

  // Branch: searching unlabeled (labelName null) but node has a label child -> not matched.
  @Test
  public void testTryMinimizeExits_unlabeledSearchButNodeHasLabel_notRemoved() throws Throwable {
    Node parent = block();
    Node b = breakStmt("foo");
    parent.addChildToBack(b);
    pass.tryMinimizeExits(b, Token.BREAK, null);
    assertTrue(parent.hasChildren());
  }

  // Branch: node type does not match requested exitType -> not matched.
  @Test
  public void testTryMinimizeExits_typeMismatch_notRemoved() throws Throwable {
    Node parent = block();
    Node r = ret();
    parent.addChildToBack(r);
    pass.tryMinimizeExits(r, Token.BREAK, null);
    assertTrue(parent.hasChildren());
  }

  // Branch: empty block (getLastChild() == null) bails out immediately.
  @Test
  public void testTryMinimizeExits_emptyBlock_noChange() throws Throwable {
    Node emptyBlock = block();
    pass.tryMinimizeExits(emptyBlock, Token.RETURN, null);
    assertFalse(emptyBlock.hasChildren());
  }

  // Branch: block whose only statement is not an exit -> no change.
  @Test
  public void testTryMinimizeExits_blockWithNonExitStatement_noChange() throws Throwable {
    Node stmt = IR.name("stmt");
    Node fnBody = block(stmt);
    pass.tryMinimizeExits(fnBody, Token.RETURN, null);
    assertSame(stmt, fnBody.getFirstChild());
    assertNull(stmt.getNext());
  }

  // Branch: n.isIf() recurses into both true and false blocks, removing exits in each.
  @Test
  public void testTryMinimizeExits_ifBothBranchesHaveExit_bothRemoved() throws Throwable {
    Node trueBlock = block(ret());
    Node falseBlock = block(ret());
    Node ifN = ifNode(IR.name("x"), trueBlock, falseBlock);
    pass.tryMinimizeExits(ifN, Token.RETURN, null);
    assertFalse(trueBlock.hasChildren());
    assertFalse(falseBlock.hasChildren());
  }

  // Branch: n.isIf() with null elseBlock does not NPE, only true branch processed.
  @Test
  public void testTryMinimizeExits_ifNoElseBranch_trueBranchProcessedNoNpe() throws Throwable {
    Node trueBlock = block(ret());
    Node ifN = ifNode(IR.name("x"), trueBlock, null);
    pass.tryMinimizeExits(ifN, Token.RETURN, null);
    assertFalse(trueBlock.hasChildren());
    assertSame(trueBlock, ifN.getFirstChild().getNext());
    assertNull(trueBlock.getNext());
  }

  // Core javadoc example: if(x) return; else blah(); foo(); -> merges foo into else block.
  @Test
  public void testTryMinimizeExits_ifReturnElseStatementThenTrailing_mergesIntoExistingElseBlock()
      throws Throwable {
    Node trueBlock = block(ret());
    Node blahCall = IR.name("blah");
    Node falseBlock = block(blahCall);
    Node ifN = ifNode(IR.name("x"), trueBlock, falseBlock);
    Node fooCall = IR.name("foo");
    Node fnBody = block(ifN, fooCall);

    pass.tryMinimizeExits(fnBody, Token.RETURN, null);

    assertSame(ifN, fnBody.getFirstChild());
    assertNull(ifN.getNext());
    assertFalse(trueBlock.hasChildren());
    assertSame(blahCall, falseBlock.getFirstChild());
    assertSame(fooCall, blahCall.getNext());
    assertNull(fooCall.getNext());
  }

  // if(x) return; (no else) foo(); -> creates new else block containing foo().
  @Test
  public void testTryMinimizeExits_ifReturnNoElseWithTrailing_createsNewElseBlock()
      throws Throwable {
    Node trueBlock = block(ret());
    Node ifN = ifNode(IR.name("x"), trueBlock, null);
    Node fooCall = IR.name("foo");
    Node fnBody = block(ifN, fooCall);

    pass.tryMinimizeExits(fnBody, Token.RETURN, null);

    assertSame(ifN, fnBody.getFirstChild());
    assertNull(ifN.getNext());
    assertFalse(trueBlock.hasChildren());
    Node newElse = trueBlock.getNext();
    assertNotNull(newElse);
    assertTrue(newElse.isBlock());
    assertSame(fooCall, newElse.getFirstChild());
  }

  // if(x) return; else blah; (else is a bare statement, not a block) foo(); -> wraps else in block.
  @Test
  public void testTryMinimizeExits_ifReturnNonBlockElseWithTrailing_wrapsElseStatementInBlock()
      throws Throwable {
    Node trueBlock = block(ret());
    Node elseStmt = IR.name("blah");
    Node ifN = ifNode(IR.name("x"), trueBlock, elseStmt);
    Node fooCall = IR.name("foo");
    Node fnBody = block(ifN, fooCall);

    pass.tryMinimizeExits(fnBody, Token.RETURN, null);

    assertSame(ifN, fnBody.getFirstChild());
    assertNull(ifN.getNext());
    assertFalse(trueBlock.hasChildren());
    Node newElse = trueBlock.getNext();
    assertTrue(newElse.isBlock());
    assertSame(elseStmt, newElse.getFirstChild());
    assertSame(fooCall, elseStmt.getNext());
  }

  // if is the sole/last statement of a block: exit removed, no else is fabricated.
  @Test
  public void testTryMinimizeExits_ifAsSoleLastStatement_noElseCreatedExitStillRemoved()
      throws Throwable {
    Node trueBlock = block(ret());
    Node ifN = ifNode(IR.name("x"), trueBlock, null);
    Node fnBody = block(ifN);

    pass.tryMinimizeExits(fnBody, Token.RETURN, null);

    assertSame(ifN, fnBody.getFirstChild());
    assertNull(ifN.getNext());
    assertFalse(trueBlock.hasChildren());
    assertNull(trueBlock.getNext());
  }

  // Javadoc "multiple if-exits" example fully nested merge.
  @Test
  public void testTryMinimizeExits_multipleSequentialIfExits_nestedMergeMatchesJavadoc()
      throws Throwable {
    Node if1Body = block(breakStmt(null));
    Node if1 = ifNode(IR.name("blah"), if1Body, null);
    Node if2Body = block(breakStmt(null));
    Node if2 = ifNode(IR.name("blah2"), if2Body, null);
    Node otherStmt = IR.name("other_stmt");
    Node fnBody = block(if1, if2, otherStmt);

    pass.tryMinimizeExits(fnBody, Token.BREAK, null);

    assertSame(if1, fnBody.getFirstChild());
    assertNull(if1.getNext());
    assertFalse(if1Body.hasChildren());
    Node elseOfIf1 = if1Body.getNext();
    assertTrue(elseOfIf1.isBlock());
    assertSame(if2, elseOfIf1.getFirstChild());
    Node if2TrueBlock = if2.getFirstChild().getNext();
    assertFalse(if2TrueBlock.hasChildren());
    Node elseOfIf2 = if2TrueBlock.getNext();
    assertSame(otherStmt, elseOfIf2.getFirstChild());
  }

  // Branch: n.isLabel() recurses into the label's body block.
  @Test
  public void testTryMinimizeExits_labelNode_recursesIntoBodyForReturn() throws Throwable {
    Node body = block(ret());
    Node labelN = new Node(Token.LABEL);
    labelN.addChildToBack(Node.newString(Token.NAME, "L"));
    labelN.addChildToBack(body);
    pass.tryMinimizeExits(labelN, Token.RETURN, null);
    assertFalse(body.hasChildren());
  }

  // Neither branch contains a matching exit -> structure left untouched.
  @Test
  public void testTryMinimizeExits_ifBranchesWithoutMatchingExit_noChange() throws Throwable {
    Node aStmt = IR.name("a");
    Node bStmt = IR.name("b");
    Node trueBlock = block(aStmt);
        Node bStmt = IR.name("b");
    Node trueBlock = block(aStmt);
    Node falseBlock = block(bStmt);
    Node ifN = ifNode(IR.name("x"), trueBlock, falseBlock);
    Node fnBody = block(ifN);

    pass.tryMinimizeExits(fnBody, Token.RETURN, null);

    assertSame(ifN, fnBody.getFirstChild());
    assertSame(aStmt, trueBlock.getFirstChild());
    assertNull(aStmt.getNext());
    assertSame(bStmt, falseBlock.getFirstChild());
    assertNull(bStmt.getNext());
  }
}
