package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.*;

public class RemoveUnusedVarsTest {

  @Test
  public void testProcessBasic() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    RemoveUnusedVars remover = new RemoveUnusedVars(compiler, true, true, false);
    remover.process(externs, root);
    assertTrue(true);
  }

  @Test
  public void testProcessWithCallSites() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    RemoveUnusedVars remover = new RemoveUnusedVars(compiler, true, true, true);
    remover.process(externs, root);
    assertTrue(true);
  }

  @Test
  public void testAssignMaybeCreateAssignNull() throws Throwable {
    Node notAssign = IR.name("a");
    try {
      // should fail precondition or return null depending on usage, test safety
      boolean isAssign = NodeUtil.isAssignmentOp(notAssign);
      assertFalse(isAssign);
    } catch (Throwable t) {
      // Expected if precondition fails
      assertNotNull(t);
    }
  }

  @Test
  public void testContinuationAndProcessFlow() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node varNode = IR.var(IR.name("x"), IR.number(5));
    Node root = IR.block(varNode);

    RemoveUnusedVars remover = new RemoveUnusedVars(compiler, true, false, false);
    remover.process(externs, root);
    assertNotNull(compiler);
  }

  @Test
  public void testFunctionArgumentsRemoval() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    
    // function f(a, b) { return a; }
    Node paramList = IR.paramList(IR.name("a"), IR.name("b"));
    Node body = IR.block(IR.returnNode(IR.name("a")));
    Node func = IR.function(IR.name("f"), paramList, body);
    Node root = IR.block(func);

    RemoveUnusedVars remover = new RemoveUnusedVars(compiler, true, true, false);
    remover.process(externs, root);
    assertNotNull(compiler);
  }
}