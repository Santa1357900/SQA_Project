package com.google.javascript.jscomp;

import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DevirtualizePrototypeMethodsClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
  }

  /** Parses js, runs the pass directly on the parsed roots, returns whitespace-free source. */
  private String runPass(String js) throws Throwable {
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("input.js", js));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    DevirtualizePrototypeMethods pass = new DevirtualizePrototypeMethods(compiler);
    pass.process(externsRoot, mainRoot);
    return compiler.toSource().replaceAll("\\s+", "");
  }

  // Constructor: should create an instance without throwing for a valid compiler reference.
  @Test
  public void testConstructor_validCompiler_createsInstance() throws Throwable {
    DevirtualizePrototypeMethods pass = new DevirtualizePrototypeMethods(compiler);
    assertNotNull(pass);
  }

  // process(): zero definition sites loop iterations, empty program produces empty output.
  @Test
  public void testProcess_emptyProgram_noChange() throws Throwable {
    String out = runPass("");
    assertEquals("", out);
  }

  // Happy path from class Javadoc: single eligible prototype method, single call site.
  @Test
  public void testProcess_simplePrototypeMethodSingleCall_isDevirtualized() throws Throwable {
    String js = "function A(){} A.prototype.accumulate=function(value){this.total+=value;return this.total;};"
        + "var a=new A(); var total=a.accumulate(2);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_accumulate(a,2)"));
    assertFalse(out.contains("a.accumulate("));
    assertTrue(out.contains("JSCompiler_StaticMethods_accumulate$self.total"));
    assertFalse(out.contains("this."));
  }

  // rewriteDefinition with zero original parameters: call site becomes name(self) only.
  @Test
  public void testProcess_zeroArgMethod_callSiteRewrittenWithSelfOnly() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(){return 1;}; var a=new A(); var r=a.foo();";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a)"));
  }

  // rewriteDefinition/rewriteCallSites preserve argument order for multi-argument methods.
  @Test
  public void testProcess_multiArgMethod_callSitePreservesArgumentOrder() throws Throwable {
    String js = "function A(){} A.prototype.sum=function(x,y,z){return x+y+z;}; var a=new A(); var r=a.sum(1,2,3);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_sum(a,1,2,3)"));
  }

  // Multiple call sites of the same definition are all rewritten with their own receivers.
  @Test
  public void testProcess_multipleCallSites_bothRewrittenWithCorrectReceiver() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;};"
        + "var a=new A(); var b=new A(); a.foo(1); b.foo(2);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,1)"));
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(b,2)"));
  }

  // isEligibleDefinition: useSites.isEmpty() -> unused method must not be rewritten.
  @Test
  public void testProcess_methodNeverCalled_notRewritten() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;};";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("A.prototype.foo=function(x)"));
  }

  // isCall(site): property read (no call) prevents rewrite.
  @Test
  public void testProcess_propertyAccessedWithoutCall_notRewritten() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;}; var a=new A(); var g=a.foo;";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("a.foo"));
  }

  // rewriteDefinitionIfEligible: definition nested in control structure is skipped.
  @Test
  public void testProcess_definitionInsideControlStructure_notRewritten() throws Throwable {
    String js = "function A(){} if(true){A.prototype.foo=function(x){return x;};} var a=new A(); a.foo(1);";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
  }

  // isPrototypeMethodDefinition: direct static property (not .prototype.) is not eligible.
  @Test
  public void testProcess_staticPropertyNotPrototype_notRewritten() throws Throwable {
    String js = "function A(){} A.foo=function(x){return x;}; A.foo(1);";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("A.foo(1)"));
  }

  // isPrototypeMethodDefinition: object-literal method definitions are not supported (per TODO).
  @Test
  public void testProcess_objectLiteralMethod_notRewritten() throws Throwable {
    String js = "var obj={foo:function(x){return x;}}; obj.foo(1);";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("obj.foo(1)"));
  }

  // isEligibleDefinition: functions using "arguments" (var-args) are not devirtualized.
  @Test
  public void testProcess_varArgsFunctionUsingArguments_notRewritten() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(){return arguments.length;};"
        + "var a=new A(); var r=a.foo(1,2);";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
  }

  // isEligibleDefinition: rValue must be a function; non-function rvalue is skipped.
  @Test
  public void testProcess_nonFunctionRValue_notRewritten() throws Throwable {
    String js = "function A(){} A.prototype.foo=5; var a=new A(); var x=a.foo;";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("A.prototype.foo=5"));
  }

  // isEligibleDefinition: ambiguous same-named property defined on two prototypes -> neither rewritten.
  @Test
  public void testProcess_ambiguousSameNameAcrossTwoPrototypes_neitherRewritten() throws Throwable {
    String js = "function A(){} function B(){} A.prototype.foo=function(x){return x;};"
        + "B.prototype.foo=function(y){return y+1;}; var a=new A(); a.foo(1);";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertTrue(out.contains("a.foo(1)"));
  }

  // Distinct property names on two different classes are each independently devirtualized.
  @Test
  public void testProcess_distinctMethodNamesTwoClasses_bothIndependentlyRewritten() throws Throwable {
    String js = "function A(){} function B(){} A.prototype.foo=function(x){return x;};"
        + "B.prototype.bar=function(y){return y;};"
        + "var a=new A(); var b=new B(); a.foo(1); b.bar(2);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,1)"));
    assertTrue(out.contains("JSCompiler_StaticMethods_bar(b,2)"));
  }

  // replaceReferencesToThis: replaces "this" in body but does not cross a nested function boundary.
  @Test
  public void testProcess_thisBoundary_outerReplacedInnerUntouched() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(){var inner=function(){return this;};return this.x;};"
        + "var a=new A(); a.foo();";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo$self.x"));
    assertTrue(out.contains("returnthis"));
  }

  // rewriteCallSites: devirtualized call works correctly when embedded in a larger expression.
  @Test
  public void testProcess_callSiteWithinExpression_rewrittenCorrectly() throws Throwable {
    String js = "function A(){} A.prototype.accumulate=function(value){this.total+=value;return this.total;};"
        + "var a=new A(); var total=a.accumulate(2)+1;";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_accumulate(a,2)+1"));
  }

  // rewriteCallSites: string-literal arguments are preserved through the rewrite.
  @Test
  public void testProcess_stringArgumentPreserved() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;}; var a=new A(); var r=a.foo(\"hi\");";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,"));
    assertTrue(out.contains("hi"));
  }

  // Works when the constructor itself is a var-assigned function expression, not a declaration.
  @Test
  public void testProcess_varAssignedConstructor_isDevirtualized() throws Throwable {
    String js = "var A=function(){}; A.prototype.foo=function(x){return x;}; var a=new A(); var r=a.foo(9);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,9)"));
  }

  // Negative-number argument is preserved exactly at the rewritten call site.
  @Test
  public void testProcess_negativeNumberArgumentPreserved() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;}; var a=new A(); var r=a.foo(-1);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,-1)"));
  }

  // Use site appearing before the definition in source order is still rewritten (same scope/module).
  @Test
  public void testProcess_useBeforeDefinitionInSameScope_stillRewritten() throws Throwable {
    String js = "function A(){} var a=new A(); a.foo(5); A.prototype.foo=function(x){return x;};";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(a,5)"));
  }

  // rewriteCallSites: a non-trivial receiver expression (call result) is correctly extracted.
  @Test
  public void testProcess_complexReceiverExpression_isDevirtualized() throws Throwable {
    String js = "function A(){} A.prototype.foo=function(x){return x;};"
        + "function make(){return new A();} var r=make().foo(3);";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_foo(make(),3)"));
  }

  // Two unrelated never-used definitions: neither should be rewritten.
  @Test
  public void testProcess_twoUnusedDefinitions_noneRewritten() throws Throwable {
    String js = "function A(){} function B(){} A.prototype.foo=function(x){return x;};"
        + "B.prototype.bar=function(y){return y;};";
    String out = runPass(js);
    assertFalse(out.contains("JSCompiler_StaticMethods_foo"));
    assertFalse(out.contains("JSCompiler_StaticMethods_bar"));
  }

  // Sanity: definition with a different method name produces a distinctly-named static function.
  @Test
  public void testProcess_methodNamePrefixAppliedConsistently() throws Throwable {
    String js = "function A(){} A.prototype.reset=function(){this.total=0;}; var a=new A(); a.reset();";
    String out = runPass(js);
    assertTrue(out.contains("JSCompiler_StaticMethods_reset(a)"));
    assertTrue(out.contains("JSCompiler_StaticMethods_reset$self.total=0"));
  }
}
