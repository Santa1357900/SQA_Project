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

    RemoveUnusedVars removeUnusedVars = new RemoveUnusedVars(compiler, true, true, false);
    removeUnusedVars.process(externs, root);
    assertNotNull(root);
  }

  @Test
  public void testProcessWithCallSiteOptimizer() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    RemoveUnusedVars removeUnusedVars = new RemoveUnusedVars(compiler, true, true, true);
    removeUnusedVars.process(externs, root);
    assertNotNull(root);
  }

  @Test
  public void testAssignMaybeCreateAssign() throws Throwable {
    Node assignNode = IR.assign(IR.name("x"), IR.number(1));
    RemoveUnusedVars.Assign assign = RemoveUnusedVars.Assign.maybeCreateAssign(assignNode);
    assertNotNull(assign);
  }

  @Test
  public void testAssignMaybeCreateAssignWithGetProp() throws Throwable {
    Node getProp = IR.getprop(IR.name("x"), IR.string("p"));
    Node assignNode = IR.assign(getProp, IR.number(1));
    RemoveUnusedVars.Assign assign = RemoveUnusedVars.Assign.maybeCreateAssign(assignNode);
    assertNotNull(assign);
  }

  @Test
  public void testAssignMaybeCreateAssignInvalid() throws Throwable {
    Node assignNode = IR.assign(IR.number(1), IR.number(2));
    RemoveUnusedVars.Assign assign = RemoveUnusedVars.Assign.maybeCreateAssign(assignNode);
    assertNull(assign);
  }
}