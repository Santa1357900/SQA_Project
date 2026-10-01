package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class UnreachableCodeEliminationClaudeTest {

  private UnreachableCodeElimination lastPass;

  private Node run(String js, boolean removeNoOp) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    Result result = compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("test.js", js),
        options);
    assertTrue(result.success);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node jsRoot = externsRoot.getNext();
    lastPass = new UnreachableCodeElimination(compiler, removeNoOp);
    lastPass.process(externsRoot, jsRoot);
    return jsRoot.getFirstChild();
  }

  private Node functionBody(Node script) {
    Node function = script.getFirstChild();
    return function.getFirstChild().getNext().getNext();
  }

  private int countChildren(Node n) {
    int count = 0;
    Node c = n.getFirstChild();
    while (c != null) {
      count++;
      c = c.getNext();
    }
    return count;
  }

  private boolean containsType(Node n, int type) {
    if (n.getType() == type) {
      return true;
    }
    Node c = n.getFirstChild();
    while (c != null) {
      if (containsType(c, type)) {
        return true;
      }
      c = c.getNext();
    }
    return false;
  }

  // visit(): expression statement following unconditional return is unreachable -> removed
  @Test
  public void testVisit_expressionAfterReturn_removed() throws Throwable {
    Node script = run("function f(g){ return 1; g(); }", false);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
    assertFalse(containsType(body, Token.CALL));
  }

  // visit(): entirely dead nested block is emptied but the empty BLOCK node itself is kept
  @Test
  public void testVisit_nestedBlockAfterReturn_leavesEmptyBlock() throws Throwable {
    Node script = run("function f(g, h){ return; { g(); h(); } }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
    Node second = body.getFirstChild().getNext();
    assertEquals(Token.BLOCK, second.getType());
    assertFalse(second.hasChildren());
  }

  // visit(): removeNoOpStatements=true removes reachable statement with no side effects ("true;")
  @Test
  public void testVisit_trueStatement_removedWhenRemoveNoOpTrue() throws Throwable {
    Node script = run("function f(g){ true; g(); }", true);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
  }

  // visit(): removeNoOpStatements=false keeps the no-op reachable statement intact
  @Test
  public void testVisit_trueStatement_keptWhenRemoveNoOpFalse() throws Throwable {
    Node script = run("function f(g){ true; g(); }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
  }

  // visit(): Javadoc example of no-op property access statement removed when flag is set
  @Test
  public void testVisit_propertyAccessStatement_removedWhenRemoveNoOpTrue() throws Throwable {
    Node script = run("function f(a, g){ a.b.MyClass.prototype.propertyName; g(); }", true);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
  }

  // tryRemoveUnconditionalBranching(): break at end of while body is NOT redundant (differs from continue)
  @Test
  public void testTryRemoveUnconditionalBranching_breakInWhileLoop_notRemoved() throws Throwable {
    Node script = run("function f(cond, g){ while (cond()) { g(); break; } }", false);
    Node body = functionBody(script);
    assertTrue(containsType(body, Token.BREAK));
  }

  // tryRemoveUnconditionalBranching(): trailing continue at end of while body is redundant -> removed
  @Test
  public void testTryRemoveUnconditionalBranching_continueAtEndOfWhileLoop_removed() throws Throwable {
    Node script = run("function f(cond, g){ while (cond()) { g(); continue; } }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.CONTINUE));
    assertTrue(containsType(body, Token.CALL));
  }

  // tryRemoveUnconditionalBranching(): trailing break as last stmt of last switch case is redundant -> removed
  @Test
  public void testTryRemoveUnconditionalBranching_breakAtEndOfSwitchCase_removed() throws Throwable {
    Node script = run("function f(x, g){ switch (x) { case 1: g(); break; } }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.BREAK));
  }

  // tryRemoveUnconditionalBranching(): cascading removal of nested redundant breaks (per Javadoc example)
  @Test
  public void testTryRemoveUnconditionalBranching_cascadingBreaks_allRemoved() throws Throwable {
    Node script = run(
        "function f(x, y){ switch (x) { case 1: if (y) { break; } break; } }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.BREAK));
  }

  // tryRemoveUnconditionalBranching(): break followed by other code in same case is NOT redundant
  @Test
  public void testTryRemoveUnconditionalBranching_breakBeforeOtherCode_notRemoved() throws Throwable {
    Node script = run(
        "function f(x, y, h){ switch (x) { case 1: if (y) { break; } h(); } }", false);
    Node body = functionBody(script);
    assertTrue(containsType(body, Token.BREAK));
  }

  // tryRemoveUnconditionalBranching(): bare return as last statement is redundant -> removed
  @Test
  public void testTryRemoveUnconditionalBranching_returnAtEndOfFunction_removed() throws Throwable {
    Node script = run("function f(g){ g(); return; }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.RETURN));
    assertTrue(containsType(body, Token.CALL));
  }

  // tryRemoveUnconditionalBranching(): return with a value never attempts removal (hasChildren breaks switch)
  @Test
  public void testTryRemoveUnconditionalBranching_returnWithValue_notRemoved() throws Throwable {
    Node script = run("function f(g){ g(); return 1; }", false);
    Node body = functionBody(script);
    assertTrue(containsType(body, Token.RETURN));
  }

  // visit(): primary Javadoc example - code after return inside an if-block is removed
  @Test
  public void testVisit_javadocExample_removesCodeAfterReturnInsideIf() throws Throwable {
    Node script = run("function f(x, alertFn){ if (x) { return; alertFn(1); } }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.CALL));
  }

  // visit(): reachable statements following an if without return are kept untouched
  @Test
  public void testVisit_reachableCodeAfterIfWithoutReturn_notRemoved() throws Throwable {
    Node script = run("function f(x, g, h){ if (x) { g(); } h(); }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
  }

  // removeDeadExprStatementSafely(): an unreachable DO loop is never removed, only its body's statements
  @Test
  public void testRemoveDeadExprStatementSafely_doLoopNeverRemoved() throws Throwable {
    Node script = run("function f(g, x){ return; do { g(); } while (x); }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
    assertTrue(containsType(body, Token.DO));
    assertFalse(containsType(body, Token.CALL));
  }

  // process(): after full traversal completes, internal cfgStack/curCfg state is fully reset
  @Test
  public void testProcess_emptyScript_stateResetAfterTraversal() throws Throwable {
    run(";", true);
    assertTrue(lastPass.cfgStack.isEmpty());
    assertNull(lastPass.curCfg);
  }

  // process(): nested function scopes correctly removed dead code in both outer and inner scope
  @Test
  public void testProcess_nestedFunctions_deadCodeRemovedAndStateReset() throws Throwable {
    Node script = run(
        "function outer(bbb){ function inner(aaa){ return; aaa(); } return; bbb(); }", false);
    assertFalse(containsType(script, Token.CALL));
    assertTrue(lastPass.cfgStack.isEmpty());
    assertNull(lastPass.curCfg);
  }

  // visit(): an entire if-statement located after an unconditional return gets removed
  @Test
  public void testVisit_ifStatementEntirelyAfterReturn_removed() throws Throwable {
    Node script = run("function f(g, x){ return; if (x) { g(); } }", false);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
  }

  // visit(): several statements following an unconditional return are all removed
  @Test
  public void testVisit_multipleStatementsAfterReturn_allRemoved() throws Throwable {
    Node script = run("function f(g, h){ return; g(); h(); }", false);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
  }

  // tryRemoveUnconditionalBranching(): hoisted function decl after return doesn't block removal of return
  @Test
  public void testTryRemoveUnconditionalBranching_functionDeclarationAfterReturn_onlyReturnRemoved()
      throws Throwable {
    Node script = run("function f(){ return; function g(){ return 1; } }", false);
    Node body = functionBody(script);
    assertEquals(1, countChildren(body));
    assertEquals(Token.FUNCTION, body.getFirstChild().getType());
  }

  // visit(): code after an if/else where both branches unconditionally return is unreachable
  @Test
  public void testVisit_codeAfterIfElseBothReturning_removed() throws Throwable {
    Node script = run("function f(x, g){ if (x) { return; } else { return; } g(); }", false);
    Node body = functionBody(script);
    assertFalse(containsType(body, Token.CALL));
  }

  // visit(): removeNoOpStatements=false keeps a side-effect-free GETPROP statement intact
  @Test
  public void testVisit_propertyAccessStatement_keptWhenRemoveNoOpFalse() throws Throwable {
    Node script = run("function f(a, g){ a.b.c; g(); }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
  }

  // visit(): a statement with real side effects is always kept regardless of the flag
  @Test
  public void testVisit_sideEffectStatementAlwaysKept() throws Throwable {
    Node script = run("function f(g){ g(); }", true);
    Node body = functionBody(script);
    assertTrue(containsType(body, Token.CALL));
  }

  // process(): no exception is thrown and no removal happens for a fully reachable simple function
  @Test
  public void testProcess_simpleReachableFunction_bodyUnchangedCount() throws Throwable {
    Node script = run("function f(g){ g(); g(); }", false);
    Node body = functionBody(script);
    assertEquals(2, countChildren(body));
  }
}
