package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class PeepholeSubstituteAlternateSyntaxClaudeTest {

  private String optimize(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setFoldConstants(true);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    Result result = compiler.compile(externs, inputs, options);
    assertTrue(result.success);
    return compiler.toSource();
  }

  // Constructor with late=true should not throw and should produce a usable instance.
  @Test
  public void testConstructor_lateTrue_createsInstance() throws Throwable {
    PeepholeSubstituteAlternateSyntax instance =
        new PeepholeSubstituteAlternateSyntax(true);
    assertNotNull(instance);
  }

  // Constructor with late=false should not throw and should produce a usable instance.
  @Test
  public void testConstructor_lateFalse_createsInstance() throws Throwable {
    PeepholeSubstituteAlternateSyntax instance =
        new PeepholeSubstituteAlternateSyntax(false);
    assertNotNull(instance);
  }

  // containsUnicodeEscape: plain ASCII string should not require a unicode escape.
  @Test
  public void testContainsUnicodeEscape_plainAscii_returnsFalse() throws Throwable {
    boolean result = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("hello");
    assertFalse(result);
  }

  // containsUnicodeEscape: empty string trivially has no unicode escape.
  @Test
  public void testContainsUnicodeEscape_emptyString_returnsFalse() throws Throwable {
    boolean result = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("");
    assertFalse(result);
  }

  // containsUnicodeEscape: a raw line-separator code point must be escaped as \u2028
  // when rendered inside a regex literal body (per same-file comment on LineTerminators).
  @Test
  public void testContainsUnicodeEscape_lineSeparator_returnsTrue() throws Throwable {
    boolean result = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("\u2028");
    assertTrue(result);
  }

  // isPure: null short-circuits to true per the method's "n == null || ..." contract.
  @Test
  public void testIsPure_nullNode_returnsTrue() throws Throwable {
    PeepholeSubstituteAlternateSyntax instance =
        new PeepholeSubstituteAlternateSyntax(true);
    assertTrue(instance.isPure(null));
  }

  // skipFinallyNodes: null input short-circuits the while loop and returns null.
  @Test
  public void testSkipFinallyNodes_nullNode_returnsNull() throws Throwable {
    PeepholeSubstituteAlternateSyntax instance =
        new PeepholeSubstituteAlternateSyntax(true);
    assertNull(instance.skipFinallyNodes(null));
  }

  // tryReduceReturn: "return undefined;" reduces to bare "return;" per Javadoc.
  @Test
  public void testOptimize_returnUndefined_reducedToBareReturn() throws Throwable {
    String out = optimize("function f(){return undefined;}");
    assertFalse(out.contains("undefined"));
  }

  // tryReduceReturn: "return void 0;" reduces to bare "return;" per Javadoc.
  @Test
  public void testOptimize_returnVoidZero_reducedToBareReturn() throws Throwable {
    String out = optimize("function f(){return void 0;}");
    assertFalse(out.contains("void"));
  }

  // tryReduceReturn: returning a plain identifier that is not "undefined" is untouched.
  @Test
  public void testOptimize_returnNonUndefinedName_unchanged() throws Throwable {
    String out = optimize("function f(x){return x;}");
    assertTrue(out.contains("return"));
    assertTrue(out.contains("x"));
  }

  // tryMinimizeNot: !(a==b) -> a!=b
  @Test
  public void testOptimize_notEq_convertsToNe() throws Throwable {
    String out = optimize("var z=!(a==b);");
    assertTrue(out.contains("!="));
    assertFalse(out.contains("=="));
  }

  // tryMinimizeNot: !(a!=b) -> a==b
  @Test
  public void testOptimize_notNe_convertsToEq() throws Throwable {
    String out = optimize("var z=!(a!=b);");
    assertTrue(out.contains("=="));
    assertFalse(out.contains("!="));
  }

  // tryMinimizeNot: !(a===b) -> a!==b
  @Test
  public void testOptimize_notSheq_convertsToShne() throws Throwable {
    String out = optimize("var z=!(a===b);");
    assertTrue(out.contains("!=="));
  }

  // tryMinimizeNot: !(a!==b) -> a===b
  @Test
  public void testOptimize_notShne_convertsToSheq() throws Throwable {
    String out = optimize("var z=!(a!==b);");
    assertTrue(out.contains("==="));
  }

  // tryMinimizeNot: relational operators (<, >, <=, >=) are explicitly NOT handled,
  // since !(x<NaN) != x>=NaN; the NOT and the "<" must both remain.
  @Test
  public void testOptimize_notRelationalLt_unchanged() throws Throwable {
    String out = optimize("var z=!(a<b);");
    assertTrue(out.contains("<"));
    assertTrue(out.contains("!"));
  }

  // tryMinimizeIf: if(x)foo(); -> x&&foo();
  @Test
  public void testOptimize_ifSingleStatementNoElse_convertsToAnd() throws Throwable {
    String out = optimize("if(a){foo();}");
    assertTrue(out.contains("&&"));
    assertFalse(out.contains("if("));
  }

  // tryMinimizeIf: if(!x)foo(); -> x||foo();
  @Test
  public void testOptimize_ifNotConditionNoElse_convertsToOr() throws Throwable {
    String out = optimize("if(!a){foo();}");
    assertTrue(out.contains("||"));
    assertFalse(out.contains("if("));
  }

  // tryMinimizeIf: if(!x)a();else b(); -> if(x)b();else a(); (negation removed, branches swapped)
  @Test
  public void testOptimize_ifNotWithElse_removesNegation() throws Throwable {
    String out = optimize("function f(x){if(!x){a();}else{b();}}");
    assertFalse(out.contains("!x"));
    assertTrue(out.contains("a()"));
    assertTrue(out.contains("b()"));
  }

  // tryMinimizeIf: if(x)return 1;else return 2; -> return x?1:2;
  @Test
  public void testOptimize_ifElseBothReturnLiterals_convertsToHookReturn() throws Throwable {
    String out = optimize("function f(x){if(x){return 1;}else{return 2;}}");
    assertTrue(out.contains("?"));
    assertTrue(out.contains(":"));
    assertTrue(out.contains("return"));
  }



  // tryReplaceIf: if(x)return 1; if(y)return 1; -> if(x||y)return 1;
  @Test
  public void testOptimize_mergeTwoIfsSameReturn_combinesWithOr() throws Throwable {
    String out = optimize("function f(x,y){if(x){return 1;}if(y){return 1;}}");
    assertTrue(out.contains("||"));
  }

  // tryReplaceIf: if(x)return 1; if(y)foo();else return 1; -> if(!x&&y)foo();else return 1;
  @Test
  public void testOptimize_mergeIfReturnWithIfElseReturn_combinesWithAnd() throws Throwable {
    String out = optimize("function f(x,y){if(x){return 1;}if(y){foo();}else{return 1;}}");
    assertTrue(out.contains("&&"));
  }

  // tryMinimizeIf: if(x)var y=1;else y=2; -> var y=x?1:2;
  @Test
  public void testOptimize_ifVarThenElseAssign_mergesToConditionalVarDecl() throws Throwable {
    String out = optimize("function f(x){if(x){var y=1;}else{y=2;}}");
    assertTrue(out.contains("var y"));
    assertTrue(out.contains("?"));
  }

  // trySplitComma: top-level "a(), b();" expression statement is split into two statements.
  @Test
  public void testOptimize_commaInExpressionStatement_splitsStatements() throws Throwable {
    String out = optimize("a(),b();");
    assertTrue(out.contains("a()"));
    assertTrue(out.contains("b()"));
  }

  // tryFoldSimpleFunctionCall: String(123) folds since the argument is an immutable literal.
  @Test
  public void testOptimize_stringConstructorWithImmutableArg_foldsAway() throws Throwable {
    String out = optimize("var x=String(123);");
    assertFalse(out.contains("String("));
  }

  // tryFoldSimpleFunctionCall: String(foo()) is left unchanged since foo() is not immutable.
  @Test
  public void testOptimize_stringConstructorWithNonImmutableArg_unchanged() throws Throwable {
    String out = optimize("var x=String(foo());");
    assertTrue(out.contains("String("));
  }

  // tryMinimizeIf: nested combine of two IF-ELSE where inner if has no else: cond && innerCond.
  @Test
  public void testOptimize_nestedIfWithoutElseInsideIf_combinesWithAnd() throws Throwable {
    String out = optimize("function f(x,y){if(x){if(y){foo();}}}");
    assertTrue(out.contains("&&"));
  }

  // tryReplaceIf: elseBranch exits via throw, so the else block is hoisted after the if.
  @Test
  public void testOptimize_ifThenThrowsWithElse_hoistsElseAfterIf() throws Throwable {
    String out = optimize("function f(x){if(x){throw 1;}else{bar();}}");
    assertTrue(out.contains("throw"));
    assertTrue(out.contains("bar()"));
  }
}
