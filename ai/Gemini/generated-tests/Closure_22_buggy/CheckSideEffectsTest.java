package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class CheckSideEffectsTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testProcessWithNoSideEffects() throws Throwable {
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    Node expr = IR.exprResult(IR.string("useless"));
    root.addChildToBack(expr);

    CheckSideEffects pass = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    pass.process(externs, root);

    assertEquals(1, compiler.getWarnings().length);
    assertTrue(compiler.getWarnings()[0].description.contains("missing '+'"));
  }

  public void testProcessWithProtectSideEffectFreeCode() throws Throwable {
    Node externs = new Node(Token.BLOCK);
    Node script = new Node(Token.SCRIPT);
    Node root = new Node(Token.BLOCK);
    root.addChildToBack(script);
    Node expr = IR.exprResult(IR.number(1.0));
    script.addChildToBack(expr);

    CheckSideEffects pass = new CheckSideEffects(compiler, CheckLevel.WARNING, true);
    pass.process(externs, root);

    assertEquals(1, compiler.getWarnings().length);
  }

  public void testHotSwapScript() throws Throwable {
    Node scriptRoot = new Node(Token.SCRIPT);
    Node expr = IR.exprResult(IR.number(2.0));
    scriptRoot.addChildToBack(expr);

    CheckSideEffects pass = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    pass.hotSwapScript(scriptRoot, null);

    assertEquals(1, compiler.getWarnings().length);
  }

  public void testVisitEmptyAndComma() throws Throwable {
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    Node empty = new Node(Token.EMPTY);
    root.addChildToBack(empty);

    CheckSideEffects pass = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    pass.process(externs, root);

    assertEquals(0, compiler.getWarnings().length);
  }

  public void testStripProtectionPass() throws Throwable {
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    Node name = IR.name("JSCOMPILER_PRESERVE");
    Node call = IR.call(name);
    Node stringArg = IR.string("protectedString");
    call.addChildToBack(stringArg);
    Node expr = IR.exprResult(call);
    root.addChildToBack(expr);

    CheckSideEffects.StripProtection stripper = new CheckSideEffects.StripProtection(compiler);
    stripper.process(externs, root);

    assertEquals(Token.STRING, root.getFirstChild().getFirstChild().getType());
  }
}