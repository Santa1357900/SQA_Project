package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ScopedAliasesTest {

  @Test
  public void testScopedAliasesInstantiation() throws Throwable {
    Compiler compiler = new Compiler();
    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, null);
    assertNotNull(scopedAliases);
  }

  @Test
  public void testProcessWithEmptyNodes() throws Throwable {
    Compiler compiler = new Compiler();
    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, null);
    Node externs = IR.block();
    Node root = IR.block();
    scopedAliases.process(externs, root);
  }
}