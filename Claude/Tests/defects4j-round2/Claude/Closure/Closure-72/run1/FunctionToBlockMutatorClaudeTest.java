package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class FunctionToBlockMutatorClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  private Node parseScript(String js) {
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("input.js", js),
        options);
    Node root = compiler.getRoot();
    Node mainRoot = root.getLastChild();
    return mainRoot.getFirstChild();
  }

  private Node findCallNode(Node script) {
    return script.getFirstChild().getNext().getFirstChild();
  }

  private Supplier<String> newIdSupplier() {
    return new Supplier<String>() {
      private int id = 0;
      public String get() {
        return String.valueOf(id++);
      }
    };
  }

  private boolean containsType(Node n, int type) {
    if (n.getType() == type) {
      return true;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (containsType(c, type)) {
        return true;
      }
    }
    return false;
  }

  private int countChildren(Node n) {
    int count = 0;
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      count++;
    }
    return count;
  }

  // Covers constructor: creates a usable instance
  @Test
  public void testConstructor_createsNonNullInstance() throws Throwable {
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    assertNotNull(mutator);
  }

  // Covers isCallInLoop == false: fixUnitializedVarDeclarations is never invoked, var stays uninitialized
  @Test
  public void testMutate_notCallInLoop_varNotInitialized() throws Throwable {
    Node script = parseScript("function foo(){ var a; return 1; } foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    Node varNode = result.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    assertFalse(varNode.getFirstChild().hasChildren());
  }

  // Covers fixUnitializedVarDeclarations VAR branch: BOTH names in "var a, b;" must be initialized (bug Closure-72)
  @Test
  public void testMutate_callInLoop_multipleVarNames_allInitializedToUndefined() throws Throwable {
    Node script = parseScript("function foo(){ var a, b; a = 1; return a + b; } foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, true);
    Node varNode = result.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    Node firstName = varNode.getFirstChild();
    Node secondName = firstName.getNext();
    assertNotNull(secondName);
    assertTrue(firstName.hasChildren());
    assertTrue(secondName.hasChildren());
  }

  // Covers fixUnitializedVarDeclarations isLoopStructure branch: vars inside nested loop are left untouched
  @Test
  public void testMutate_callInLoop_varInsideNestedLoop_notInitialized() throws Throwable {
    Node script = parseScript("function foo(){ while(a){ var c; c = 1; } return 1; } foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, true);
    Node whileNode = result.getFirstChild();
    assertEquals(Token.WHILE, whileNode.getType());
    Node whileBody = whileNode.getFirstChild().getNext();
    Node varNode = whileBody.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    assertFalse(varNode.getFirstChild().hasChildren());
  }

  // Covers hasArgs==true with no aliasing needed: argument value is inlined directly into the body
  @Test
  public void testMutate_withSimpleArgument_inlinesValueDirectly() throws Throwable {
    Node script = parseScript("function foo(a){return a;} foo(5);");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(1, countChildren(result));
    Node exprResult = result.getFirstChild();
    assertEquals(Token.EXPR_RESULT, exprResult.getType());
    assertEquals(Token.NUMBER, exprResult.getFirstChild().getType());
  }

  // Covers aliasAndInlineArguments "else" branch: modified parameter gets a prepended VAR alias
  @Test
  public void testMutate_withModifiedParameter_createsAliasVar() throws Throwable {
    Node script = parseScript("function foo(a){a=a+1;return a;} foo(5);");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(3, countChildren(result));
    assertEquals(Token.VAR, result.getFirstChild().getType());
  }

  // Covers LabelNameSupplier.get(): prefixes the wrapped supplier value
  @Test
  public void testLabelNameSupplier_get_prependsPrefix() throws Throwable {
    Supplier<String> idSupplier = new Supplier<String>() {
      public String get() {
        return "42";
      }
    };
    FunctionToBlockMutator.LabelNameSupplier supplier =
        new FunctionToBlockMutator.LabelNameSupplier(idSupplier);
    assertEquals("JSCompiler_inline_label_42", supplier.get());
  }

  // Covers LabelNameSupplier.get() across multiple calls reflecting the underlying supplier state
  @Test
  public void testLabelNameSupplier_get_multipleCalls_reflectsUnderlyingSupplier() throws Throwable {
    Supplier<String> idSupplier = new Supplier<String>() {
      private int n = 0;
      public String get() {
        return String.valueOf(n++);
      }
    };
    FunctionToBlockMutator.LabelNameSupplier supplier =
        new FunctionToBlockMutator.LabelNameSupplier(idSupplier);
    assertEquals("JSCompiler_inline_label_0", supplier.get());
    assertEquals("JSCompiler_inline_label_1", supplier.get());
  }

  // Covers getLabelNameForFunction: empty fnName falls back to "anon"
  @Test
  public void testMutate_emptyFnName_usesAnonLabel() throws Throwable {
    Node script = parseScript("function foo(){if(a){return 1;}return 2;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("", fnNode, callNode, null, false, false);
    Node labelName = result.getFirstChild().getFirstChild();
    assertEquals("JSCompiler_inline_label_anon_0", labelName.getString());
  }

  // Covers getLabelNameForFunction: null fnName falls back to "anon"
  @Test
  public void testMutate_nullFnName_usesAnonLabel() throws Throwable {
    Node script = parseScript("function foo(){if(a){return 1;}return 2;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate(null, fnNode, callNode, null, false, false);
    Node labelName = result.getFirstChild().getFirstChild();
    assertEquals("JSCompiler_inline_label_anon_0", labelName.getString());
  }

  // Covers replaceReturns: returnCount==0, resultName null so block is unchanged
  @Test
  public void testMutate_noReturn_noResultName_blockUnchanged() throws Throwable {
    Node script = parseScript("function foo(){x=1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(Token.BLOCK, result.getType());
    assertEquals(1, countChildren(result));
    assertEquals(Token.EXPR_RESULT, result.getFirstChild().getType());
  }

  // Covers addDummyAssignment: resultMustBeSet && !hasReturnAtExit && resultName != null
  @Test
  public void testMutate_noReturn_needsDefaultResult_addsDummyAssignment() throws Throwable {
    Node script = parseScript("function foo(){x=1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, "result", true, false);
    assertEquals(2, countChildren(result));
    Node dummy = result.getFirstChild().getNext();
    assertEquals(Token.EXPR_RESULT, dummy.getType());
    Node assign = dummy.getFirstChild();
    assertEquals(Token.ASSIGN, assign.getType());
    assertEquals("result", assign.getFirstChild().getString());
  }

  // Covers addDummyAssignment guard: resultName == null prevents the dummy assignment
  @Test
  public void testMutate_noReturn_needsDefaultResultButNullResultName_noDummyAssignment() throws Throwable {
    Node script = parseScript("function foo(){x=1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, true, false);
    assertEquals(1, countChildren(result));
  }

  // Covers addDummyAssignment guard: needsDefaultResult == false prevents the dummy assignment
  @Test
  public void testMutate_noReturn_resultNameProvidedButNotNeeded_noDummyAssignment() throws Throwable {
    Node script = parseScript("function foo(){x=1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, "result", false, false);
    assertEquals(1, countChildren(result));
  }

  // Covers hasReturnAtExit true with resultName null: becomes a bare expression statement
  @Test
  public void testMutate_returnAtEnd_noResultName_convertsToExpressionStatement() throws Throwable {
    Node script = parseScript("function foo(){return 1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(1, countChildren(result));
    assertEquals(Token.EXPR_RESULT, result.getFirstChild().getType());
    assertEquals(Token.NUMBER, result.getFirstChild().getFirstChild().getType());
  }

  // Covers hasReturnAtExit true with resultName set: converted into an assignment statement
  @Test
  public void testMutate_returnAtEnd_withResultName_convertsToAssignment() throws Throwable {
    Node script = parseScript("function foo(){return 1;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, "result", true, false);
    assertEquals(1, countChildren(result));
    Node exprResult = result.getFirstChild();
    assertEquals(Token.EXPR_RESULT, exprResult.getType());
    Node assign = exprResult.getFirstChild();
    assertEquals(Token.ASSIGN, assign.getType());
    assertEquals("result", assign.getFirstChild().getString());
  }

  // Covers getReplacementReturnStatement: empty return with resultName assigns an undefined value
  @Test
  public void testMutate_emptyReturnAtEnd_withResultName_assignsUndefinedValue() throws Throwable {
    Node script = parseScript("function foo(){return;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, "result", false, false);
    Node assign = result.getFirstChild().getFirstChild();
    assertEquals(Token.ASSIGN, assign.getType());
    assertNotNull(assign.getFirstChild().getNext());
  }

  // Covers convertLastReturnToStatement: empty return with no resultName is removed entirely
  @Test
  public void testMutate_emptyReturnAtEnd_noResultName_removesReturnStatement() throws Throwable {
    Node script = parseScript("function foo(){x=1;return;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(1, countChildren(result));
    assertEquals(Token.EXPR_RESULT, result.getFirstChild().getType());
  }

  // Covers returnCount>0 after tail conversion: wraps block in LABEL, remaining return becomes BREAK
  @Test
  public void testMutate_multipleReturns_wrapsInLabelAndBreak() throws Throwable {
    Node script = parseScript("function foo(){if(a){return 1;}return 2;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(Token.BLOCK, result.getType());
    Node label = result.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    Node labelName = label.getFirstChild();
    assertEquals(Token.LABEL_NAME, labelName.getType());
    assertEquals("JSCompiler_inline_label_foo_0", labelName.getString());
    assertTrue(containsType(label, Token.BREAK));
  }

  // Covers hasReturnAtExit false with single non-tail return: label+break plus trailing dummy assignment
  @Test
  public void testMutate_returnNotAtEnd_dummyAssignmentAfterLabelWrap() throws Throwable {
    Node script = parseScript("function foo(){if(a){return 1;}x=2;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, "result", true, false);
    Node label = result.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    Node innerBlock = label.getFirstChild().getNext();
    Node lastChild = innerBlock.getFirstChild();
    while (lastChild.getNext() != null) {
      lastChild = lastChild.getNext();
    }
    assertEquals(Token.EXPR_RESULT, lastChild.getType());
    assertEquals(Token.ASSIGN, lastChild.getFirstChild().getType());
    assertEquals("result", lastChild.getFirstChild().getFirstChild().getString());
  }

  // Covers shallow return counting: nested function's return is not counted/touched
  @Test
  public void testMutate_nestedFunctionReturn_notCountedShallow() throws Throwable {
    Node script = parseScript("function foo(){var g=function(){return 1;};return 2;} foo();");
    Node fnNode = script.getFirstChild();
    Node callNode = findCallNode(script);
    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, newIdSupplier());
    Node result = mutator.mutate("foo", fnNode, callNode, null, false, false);
    assertEquals(2, countChildren(result));
    assertEquals(Token.VAR, result.getFirstChild().getType());
    assertEquals(Token.EXPR_RESULT, result.getFirstChild().getNext().getType());
    assertTrue(containsType(result, Token.RETURN));
  }
}
