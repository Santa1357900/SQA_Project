package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnreachableCodeEliminationClaudeTest {

  private String optimize(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("test.js", js),
        options);
    return compiler.toSource();
  }

  // Constructor with removeNoOpStatements = true must build a usable instance.
  @Test
  public void testConstructor_trueFlag_createsInstance() throws Throwable {
    Compiler compiler = new Compiler();
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    assertNotNull(pass);
  }

  // Constructor with removeNoOpStatements = false must build a usable instance.
  @Test
  public void testConstructor_falseFlag_createsInstance() throws Throwable {
    Compiler compiler = new Compiler();
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, false);
    assertNotNull(pass);
  }

  // Code following an unconditional return is unreachable and must be removed.
  @Test
  public void testProcess_codeAfterUnconditionalReturn_removed() throws Throwable {
    String out = optimize("function f(){ return 1; g(99); } function g(x){ return x; } f();");
    assertFalse(out.contains("g(99)"));
    assertTrue(out.contains("1"));
  }

  // Code following a return inside an if-branch is unreachable and must be removed.
  @Test
  public void testProcess_codeAfterReturnInsideIf_removed() throws Throwable {
    String out = optimize("function f(a){ if(a){ return; g(98);} return 2;} f(1);");
    assertFalse(out.contains("g(98)"));
    assertTrue(out.contains("2"));
  }

  // Only one branch returns, so code after the if must remain reachable.
  @Test
  public void testProcess_codeReachableWhenOnlyOneBranchReturns_kept() throws Throwable {
    String out = optimize("function f(a){ if(a){ return 1;} g(97);} function g(x){return x;} f(1);");
    assertTrue(out.contains("g(97)"));
  }

  // Both branches of if/else return, so trailing code is unreachable.
  @Test
  public void testProcess_codeAfterIfElseBothReturn_removed() throws Throwable {
    String out = optimize("function f(a){ if(a){return 1;} else {return 2;} g(96);} function g(x){return x;} f(1);");
    assertFalse(out.contains("g(96)"));
  }

  // Code after an unconditional break inside a for loop body is unreachable.
  @Test
  public void testProcess_codeAfterBreakInForLoop_removed() throws Throwable {
    String out = optimize("function f(a){ for(;;){ if(a){ break; g(95);} g(94);} } function g(x){return x;} f(1);");
    assertFalse(out.contains("g(95)"));
    assertTrue(out.contains("g(94)"));
  }

  // Code after an unconditional break inside a while loop body is unreachable.
  @Test
  public void testProcess_codeAfterBreakInWhileLoop_removed() throws Throwable {
    String out = optimize("function f(a){ while(true){ if(a){ break; g(93);} g(92);} } function g(x){return x;} f(1);");
    assertFalse(out.contains("g(93)"));
    assertTrue(out.contains("g(92)"));
  }

  // Loop can exit via conditional break, so code after the loop is reachable.
  @Test
  public void testProcess_codeReachableAfterLoopThatCanBreak_kept() throws Throwable {
    String out = optimize("function f(a){ for(;;){ if(a){break;} } g(91);} function g(x){return x;} f(1);");
    assertTrue(out.contains("g(91)"));
  }

  // Code after an unconditional continue in a for loop is unreachable.
  @Test
  public void testProcess_codeAfterContinueInForLoop_removed() throws Throwable {
    String out = optimize("function f(a){ for(var i=0;i<10;i++){ if(a){ continue; g(90);} g(89);} } function g(x){return x;} f(1);");
    assertFalse(out.contains("g(90)"));
    assertTrue(out.contains("g(89)"));
  }

  // Code after an unconditional continue in a while loop is unreachable.
  @Test
  public void testProcess_codeAfterContinueInWhileLoop_removed() throws Throwable {
    String out = optimize("function f(a){ while(a){ if(a){ continue; g(88);} g(87);} } function g(x){return x;} f(1);");
    assertFalse(out.contains("g(88)"));
    assertTrue(out.contains("g(87)"));
  }

  // Statement right after break in a switch case, before the next case label, is unreachable.
  @Test
  public void testProcess_codeAfterSwitchBreak_unreachableStatementRemoved() throws Throwable {
    String out = optimize("function f(a){ switch(a){ case 1: g(86); break; g(85); case 2: g(84); break;} } function g(x){return x;} f(1);");
    assertFalse(out.contains("g(85)"));
    assertTrue(out.contains("g(86)"));
    assertTrue(out.contains("g(84)"));
  }

  // Case without break falls through, so next case's code remains reachable.
  @Test
  public void testProcess_codeInNextSwitchCase_kept() throws Throwable {
    String out = optimize("function f(a){ switch(a){ case 1: g(83); case 2: g(82); break;} } function g(x){return x;} f(1);");
    assertTrue(out.contains("g(83)"));
    assertTrue(out.contains("g(82)"));
  }

  // Code following an unconditional throw is unreachable.
  @Test
  public void testProcess_codeAfterThrow_removed() throws Throwable {
    String out = optimize("function f(){ throw 1; g(81);} function g(x){return x;} try{f();}catch(e){}");
    assertFalse(out.contains("g(81)"));
  }

  // Both branches of if/else throw, trailing code is unreachable.
  @Test
  public void testProcess_codeAfterIfElseBothThrow_removed() throws Throwable {
    String out = optimize("function f(a){ if(a){throw 1;} else {throw 2;} g(80);} function g(x){return x;} try{f(1);}catch(e){}");
    assertFalse(out.contains("g(80)"));
  }



  // A reachable do-while loop's body must execute, so it must be kept.
  @Test
  public void testProcess_reachableDoWhileBody_kept() throws Throwable {
    String out = optimize("function f(){ do{ g(78);} while(false); return 2;} function g(x){return x;} f();");
    assertTrue(out.contains("g(78)"));
  }

  // A break at the end of the only/last switch case is redundant and should be removed.
  @Test
  public void testProcess_redundantBreakAtEndOfLastSwitchCase_removed() throws Throwable {
    String out = optimize("function f(a){ switch(a){ case 1: g(77); break;} g(76);} function g(x){return x;} f(1);");
    assertFalse(out.contains("break"));
    assertTrue(out.contains("g(77)"));
    assertTrue(out.contains("g(76)"));
  }

  // A break before another case is not redundant (changes control flow) so it must stay.
  @Test
  public void testProcess_nonRedundantBreakBeforeNextCase_kept() throws Throwable {
    String out = optimize("function f(a){ switch(a){ case 1: g(75); break; case 2: g(74);} g(73);} function g(x){return x;} f(1);");
    assertTrue(out.contains("break"));
  }

  // A continue at the very end of a loop body is redundant and should be removed.
  @Test
  public void testProcess_redundantContinueAtEndOfLoopBody_removed() throws Throwable {
    String out = optimize("function f(a){ for(;;){ g(72); continue; } } function g(x){return x;} f(1);");
    assertFalse(out.contains("continue"));
    assertTrue(out.contains("g(72)"));
  }



  // A bare trailing return at the end of a function body is redundant and should be removed.
  @Test
  public void testProcess_redundantTrailingReturn_removed() throws Throwable {
    String out = optimize("function f(){ g(70); return; } function g(x){return x;} f();");
    assertFalse(out.contains("return;"));
    assertTrue(out.contains("g(70)"));
  }



  // A reachable bare literal statement with no side effects must be removed.
  @Test
  public void testProcess_trueLiteralStatement_removed() throws Throwable {
    String out = optimize("function f(){ true; return 1;} f();");
    assertFalse(out.contains("true"));
  }

  // A normal reachable return value must be preserved exactly.
  @Test
  public void testProcess_normalReturnValuePreserved() throws Throwable {
    String out = optimize("function f(){ return 55;} f();");
    assertTrue(out.contains("55"));
  }

  // Reachable code inside a nested function must remain untouched.
  @Test
  public void testProcess_nestedFunctionReachableCodeUnaffected() throws Throwable {
    String out = optimize("function f(){ function inner(){ return 69; } return inner(); } f();");
    assertTrue(out.contains("69"));
  }
}
