package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.rhino.Node;

import java.util.Collection;

public class AnalyzePrototypePropertiesClaudeTest {

  private static class CompileResult {
    final Compiler compiler;
    final Node externsRoot;
    final Node jsRoot;

    CompileResult(Compiler compiler, Node externsRoot, Node jsRoot) {
      this.compiler = compiler;
      this.externsRoot = externsRoot;
      this.jsRoot = jsRoot;
    }
  }

  private CompileResult compile(String externsCode, String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", externsCode);
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
    Node root = compiler.getRoot();
    return new CompileResult(compiler, root.getFirstChild(), root.getLastChild());
  }

  private AnalyzePrototypeProperties.NameInfo findNameInfo(
      Collection<AnalyzePrototypeProperties.NameInfo> infos, String name) {
    for (AnalyzePrototypeProperties.NameInfo info : infos) {
      if (name.equals(info.toString())) {
        return info;
      }
    }
    return null;
  }

  // Constructor must register the implicitly-used properties length/toString/valueOf
  @Test
  public void testConstructor_registersImplicitlyUsedProperties() throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    Collection<AnalyzePrototypeProperties.NameInfo> infos = pass.getAllNameInfo();
    assertNotNull(findNameInfo(infos, "length"));
    assertNotNull(findNameInfo(infos, "toString"));
    assertNotNull(findNameInfo(infos, "valueOf"));
  }

  // Before process() runs, markReference propagation has not happened yet
  @Test
  public void testConstructor_implicitPropertiesNotReferencedBeforeProcess() throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    assertFalse(lengthInfo.isReferenced());
  }

  // process() runs fixed-point propagation from externNode, reaching implicit properties
  @Test
  public void testProcess_implicitPropertiesBecomeReferencedAfterProcess() throws Throwable {
    CompileResult cr = compile("", "var x = 1;");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    assertTrue(lengthInfo.isReferenced());
  }

  // A top-level function declaration should be registered under the VAR map
  @Test
  public void testProcess_globalFunctionDeclarationRegisteredAsVar() throws Throwable {
    CompileResult cr = compile("", "function Foo() {}");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo fooInfo = findNameInfo(pass.getAllNameInfo(), "Foo");
    assertNotNull(fooInfo);
    assertEquals(1, fooInfo.getDeclarations().size());
  }

  // Foo.prototype.bar = function(){...} must be recorded as an AssignmentProperty declaration
  @Test
  public void testProcess_prototypeAssignmentRegisteredAsAssignmentProperty() throws Throwable {
    String js = "function Foo() {} Foo.prototype.bar = function() { return 1; };";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNotNull(barInfo);
    assertEquals(1, barInfo.getDeclarations().size());
    assertTrue(barInfo.getDeclarations().peek()
        instanceof AnalyzePrototypeProperties.AssignmentProperty);
  }

  // Foo.prototype = {bar:..., baz:...} must register each key as a LiteralProperty
  @Test
  public void testProcess_prototypeLiteralAssignment_registersEachKeyAsLiteralProperty()
      throws Throwable {
    String js = "function Foo() {} Foo.prototype = "
        + "{bar: function() { return 1; }, baz: 5};";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    AnalyzePrototypeProperties.NameInfo bazInfo = findNameInfo(pass.getAllNameInfo(), "baz");
    assertNotNull(barInfo);
    assertNotNull(bazInfo);
    assertTrue(barInfo.getDeclarations().peek()
        instanceof AnalyzePrototypeProperties.LiteralProperty);
    assertTrue(bazInfo.getDeclarations().peek()
        instanceof AnalyzePrototypeProperties.LiteralProperty);
  }

  // Same property name on two different prototypes must share one NameInfo with 2 declarations
  @Test
  public void testProcess_samePropertyAcrossPrototypes_sharesNameInfoWithTwoDeclarations()
      throws Throwable {
    String js = "function Foo() {} Foo.prototype.bar = function() { return 1; };"
        + "function Baz() {} Baz.prototype.bar = function() { return 2; };";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertEquals(2, barInfo.getDeclarations().size());
  }

  // A property accessed via GETPROP from global scope must become referenced
  @Test
  public void testProcess_referencedPropertyMarkedReferenced() throws Throwable {
    String js = "function Foo() {}"
        + "Foo.prototype.bar = function() { return 1; };"
        + "var f = new Foo(); f.bar();";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertTrue(barInfo.isReferenced());
  }

  // A declared-but-never-accessed prototype property must remain unreferenced
  @Test
  public void testProcess_unreferencedPropertyNotMarked() throws Throwable {
    String js = "function Foo() {}"
        + "Foo.prototype.bar = function() { return 1; };"
        + "Foo.prototype.baz = function() { return 2; };"
        + "var f = new Foo(); f.bar();";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo bazInfo = findNameInfo(pass.getAllNameInfo(), "baz");
    assertFalse(bazInfo.isReferenced());
  }

  // anchorUnusedVars=true must force unused global functions to be marked referenced
  @Test
  public void testProcess_anchorUnusedVarsTrue_marksUnusedGlobalFunctionReferenced()
      throws Throwable {
    CompileResult cr = compile("", "function unusedFunc() { return 1; }");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, true);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo info = findNameInfo(pass.getAllNameInfo(), "unusedFunc");
    assertTrue(info.isReferenced());
  }

  // anchorUnusedVars=false must leave an unused global function unreferenced
  @Test
  public void testProcess_anchorUnusedVarsFalse_doesNotMarkUnusedGlobalFunctionReferenced()
      throws Throwable {
    CompileResult cr = compile("", "function unusedFunc() { return 1; }");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo info = findNameInfo(pass.getAllNameInfo(), "unusedFunc");
    assertFalse(info.isReferenced());
  }

  // canModifyExterns=false must traverse externs and mark extern properties referenced
  @Test
  public void testProcess_canModifyExternsFalse_externPropertyMarkedReferenced()
      throws Throwable {
    CompileResult cr = compile("Foo.prototype.bar;", "function Foo() {}");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, false, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNotNull(barInfo);
    assertTrue(barInfo.isReferenced());
  }

  // canModifyExterns=true must skip the externs traversal entirely
  @Test
  public void testProcess_canModifyExternsTrue_externPropertyNotAutoReferenced()
      throws Throwable {
    CompileResult cr = compile("Foo.prototype.bar;", "function Foo() {}");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNull(barInfo);
  }

  // A non-global (local) function-valued variable must not be registered as a global var
  @Test
  public void testProcess_localFunctionVariable_notRegisteredAsGlobalVar() throws Throwable {
    String js = "function outer() { var inner = function() { return 1; }; return inner(); }";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo innerInfo = findNameInfo(pass.getAllNameInfo(), "inner");
    assertNull(innerInfo);
  }

  // A prototype method reading an outer non-global closure variable must set readClosureVariables
  @Test
  public void testProcess_closureVariableRead_marksReadClosureVariablesTrue() throws Throwable {
    String js = "function outer() {"
        + "  var x = 1;"
        + "  function Foo() {}"
        + "  Foo.prototype.bar = function() { return x; };"
        + "}";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNotNull(barInfo);
    assertTrue(barInfo.readsClosureVariables());
  }

  // A prototype method with no outer-scope variable access must not set readClosureVariables
  @Test
  public void testProcess_noClosureVariableRead_readsClosureVariablesFalse() throws Throwable {
    String js = "function Foo() {} Foo.prototype.bar = function() { return 1; };";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertFalse(barInfo.readsClosureVariables());
  }

  // Quoted object-literal keys must be skipped by the generic property-use loop
  @Test
  public void testProcess_quotedObjectLiteralKey_notCountedAsPropertyUse() throws Throwable {
    String js = "var x = {\"bar\": 1};";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNull(barInfo);
  }

  // Object literal assigned directly to a prototype must be excluded from the generic use-loop
  @Test
  public void testProcess_objectLiteralAssignedToPrototype_excludedFromGenericUseLoop()
      throws Throwable {
    String js = "function Foo() {} Foo.prototype = {bar: function() { return 1; }};";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    assertNotNull(barInfo);
    assertFalse(barInfo.isReferenced());
  }

  // A numeric object-literal key must not be mistaken for a string property name (NUMBER vs STRING)
  @Test
  public void testProcess_objectLiteralWithNumericKey_skipsNumericKeySafely() throws Throwable {
    String js = "var x = {0: 1, bar: 2};";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    Collection<AnalyzePrototypeProperties.NameInfo> infos = pass.getAllNameInfo();
    for (AnalyzePrototypeProperties.NameInfo info : infos) {
      assertNotNull(info.toString());
    }
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(infos, "bar");
    assertNotNull(barInfo);
    assertTrue(barInfo.isReferenced());
  }

  // References propagate transitively: calling a global function references names it uses
  @Test
  public void testProcess_transitiveReferencePropagationThroughGlobalFunction()
      throws Throwable {
    String js = "function Foo() {}"
        + "function user() { return Foo; }"
        + "user();";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    Collection<AnalyzePrototypeProperties.NameInfo> infos = pass.getAllNameInfo();
    assertTrue(findNameInfo(infos, "user").isReferenced());
    assertTrue(findNameInfo(infos, "Foo").isReferenced());
  }

  // getAllNameInfo() initial size must match the 3 implicitly-used properties only
  @Test
  public void testGetAllNameInfo_initialSizeMatchesImplicitProperties() throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    assertEquals(3, pass.getAllNameInfo().size());
  }

  // getAllNameInfo() must combine both the property map and the var map
  @Test
  public void testGetAllNameInfo_containsBothPropertyAndVarEntries() throws Throwable {
    String js = "function Foo() {} Foo.prototype.bar = function() { return 1; };";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    Collection<AnalyzePrototypeProperties.NameInfo> infos = pass.getAllNameInfo();
    assertNotNull(findNameInfo(infos, "Foo"));
    assertNotNull(findNameInfo(infos, "bar"));
  }

  // First markReference call on an unreferenced NameInfo must return true and set referenced
  @Test
  public void testNameInfo_markReference_firstCallReturnsTrueAndSetsReferenced()
      throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    boolean changed = lengthInfo.markReference(null);
    assertTrue(changed);
    assertTrue(lengthInfo.isReferenced());
  }

  // A second markReference call (moduleGraph null) must report no further change
  @Test
  public void testNameInfo_markReference_secondCallWithNullModuleGraphReturnsFalse()
      throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    lengthInfo.markReference(null);
    boolean changedAgain = lengthInfo.markReference(null);
    assertFalse(changedAgain);
  }

  // With a null moduleGraph, deepestCommonModuleRef must remain null even after marking
  @Test
  public void testNameInfo_getDeepestCommonModuleRef_staysNullWhenModuleGraphNull()
      throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    assertNull(lengthInfo.getDeepestCommonModuleRef());
    lengthInfo.markReference(null);
    assertNull(lengthInfo.getDeepestCommonModuleRef());
  }

  // toString() must return the symbol's own name
  @Test
  public void testNameInfo_toString_returnsName() throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    assertEquals("length", lengthInfo.toString());
  }

  // An implicit property with no declarations in source must have an empty declarations deque
  @Test
  public void testNameInfo_getDeclarations_emptyForImplicitProperty() throws Throwable {
    CompileResult cr = compile("", "");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    AnalyzePrototypeProperties.NameInfo lengthInfo =
        findNameInfo(pass.getAllNameInfo(), "length");
    assertTrue(lengthInfo.getDeclarations().isEmpty());
  }

  // AssignmentProperty.getValue()/getPrototype() must return the RHS function and LHS GETPROP
  @Test
  public void testAssignmentProperty_getPrototypeAndGetValue_returnCorrectNodes()
      throws Throwable {
    String js = "function Foo() {} Foo.prototype.bar = function() { return 1; };";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    AnalyzePrototypeProperties.Property decl =
        (AnalyzePrototypeProperties.Property) barInfo.getDeclarations().peek();
    assertTrue(decl.getValue().isFunction());
    assertTrue(decl.getPrototype().isGetProp());
  }

  // LiteralProperty values must correspond to the actual declared expression per key
  @Test
  public void testLiteralProperty_valuesMatchDeclaredExpressions() throws Throwable {
    String js = "function Foo() {} Foo.prototype = "
        + "{bar: function() { return 1; }, baz: 5};";
    CompileResult cr = compile("", js);
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo barInfo = findNameInfo(pass.getAllNameInfo(), "bar");
    AnalyzePrototypeProperties.NameInfo bazInfo = findNameInfo(pass.getAllNameInfo(), "baz");
    AnalyzePrototypeProperties.Property barDecl =
        (AnalyzePrototypeProperties.Property) barInfo.getDeclarations().peek();
    AnalyzePrototypeProperties.Property bazDecl =
        (AnalyzePrototypeProperties.Property) bazInfo.getDeclarations().peek();
    assertTrue(barDecl.getValue().isFunction());
    assertFalse(bazDecl.getValue().isFunction());
  }

  // GlobalFunction.getFunctionNode() must return the actual FUNCTION node of the declaration
  @Test
  public void testGlobalFunction_getFunctionNode_returnsFunctionNode() throws Throwable {
    CompileResult cr = compile("", "function Foo() {}");
    AnalyzePrototypeProperties pass =
        new AnalyzePrototypeProperties(cr.compiler, null, true, false);
    pass.process(cr.externsRoot, cr.jsRoot);
    AnalyzePrototypeProperties.NameInfo fooInfo = findNameInfo(pass.getAllNameInfo(), "Foo");
    AnalyzePrototypeProperties.GlobalFunction decl =
        (AnalyzePrototypeProperties.GlobalFunction) fooInfo.getDeclarations().peek();
    assertTrue(decl.getFunctionNode().isFunction());
  }
}
