package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;

public class CheckGlobalThisClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  private void runPass(String js, CheckLevel level) {
    Node root = compiler.parseTestCode(js);
    CheckGlobalThis pass = new CheckGlobalThis(compiler, level);
    NodeTraversal.traverse(compiler, root, pass);
  }

  // Javadoc example: this.useful = undefined; inside a plain global function must be flagged.
  @Test
  public void testVisit_GlobalFunctionPropertyAssignment_ReportsWarning() throws Throwable {
    runPass("function evil() { this.useful = undefined; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // Javadoc explicit counter-example: assigning this to a var and using that var is NOT flagged.
  @Test
  public void testVisit_ThisStoredInVariableThenUsed_NoWarning() throws Throwable {
    runPass("function evil() { var a = this; a.useful = undefined; }", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // shouldTraverse: assigning a function to Foo.prototype.bar makes it a prototype method; skip it.
  @Test
  public void testShouldTraverse_PrototypeMethodAssignment_NotTraversed_NoWarning() throws Throwable {
    runPass("Foo.prototype.bar = function() { this.y = 3; };", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // shouldTraverse: direct assignment to Foo.prototype (lastChild == "prototype") is skipped too.
  @Test
  public void testShouldTraverse_DirectPrototypeAssignment_NotTraversed_NoWarning() throws Throwable {
    runPass("Foo.prototype = function() { this.y = 3; };", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // Non-prototype property function assignment is NOT exempt and must be flagged.
  @Test
  public void testVisit_NonPrototypePropertyFunctionAssignment_ReportsWarning() throws Throwable {
    runPass("var obj = {}; obj.method = function() { this.x = 1; };", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // shouldTraverse: an IIFE's function parent is CALL, not in {BLOCK,SCRIPT,NAME,ASSIGN} -> skipped.
  @Test
  public void testShouldTraverse_ImmediatelyInvokedFunctionExpression_NotTraversed_NoWarning() throws Throwable {
    runPass("(function() { this.x = 1; })();", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // Top-level (script scope) this.x = 1 without any enclosing function must still be flagged.
  @Test
  public void testVisit_TopLevelThisPropertyAssignment_ReportsWarning() throws Throwable {
    runPass("this.x = 1;", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // Javadoc: "on the left side of ... a property access" -> reading this.x is also unsafe.
  @Test
  public void testVisit_ThisPropertyReadViaVarInitializer_ReportsWarning() throws Throwable {
    runPass("function f() { var v = this.x; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // shouldReportThis: bare `this` used only as a call argument is neither assign-lhs nor a get.
  @Test
  public void testVisit_BareThisAsCallArgument_NoWarning() throws Throwable {
    runPass("function f() { bar(this); }", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // Comment in source: nested assignment (a = this).property = c; must still flag the nested this.
  @Test
  public void testVisit_NestedAssignmentThisOnLeftSide_ReportsWarning() throws Throwable {
    runPass("var a; (a = this).property = c;", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // shouldTraverse: a function declared inside a BLOCK (nested in another function) is still checked.
  @Test
  public void testShouldTraverse_FunctionDeclaredInsideBlock_ReportsWarning() throws Throwable {
    runPass("function outer() { function evil() { this.x = 1; } }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // visit: multiple unsafe this usages in the same function each produce their own warning.
  @Test
  public void testVisit_MultipleUnsafeThisUsagesInSameFunction_ReportsTwoWarnings() throws Throwable {
    runPass("function evil() { this.a = 1; this.b = 2; }", CheckLevel.WARNING);
    assertEquals(2, compiler.getWarningCount());
  }

  // visit: assignLhsChild must be reset after the first statement so the second this is not flagged.
  @Test
  public void testVisit_AssignLhsChildResetBetweenStatements_ReportsOnlyFirstWarning() throws Throwable {
    runPass("function evil() { this.a = 1; var c = this; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // getFunctionJsDocInfo path through NAME parent / VAR grandparent still yields no exemption.
  @Test
  public void testShouldTraverse_FunctionAssignedViaVarDeclaration_ReportsWarning() throws Throwable {
    runPass("var x = function() { this.y = 1; };", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // Sanity: code without any `this` usage must never produce a warning.
  @Test
  public void testVisit_CleanCodeWithoutThis_NoWarnings() throws Throwable {
    runPass("var a = 1; function f(g) { return g + 1; }", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // shouldReportThis: a bare `this;` expression statement is neither assign-lhs nor a get.
  @Test
  public void testVisit_BareThisExpressionStatement_NoWarning() throws Throwable {
    runPass("function f() { this; }", CheckLevel.WARNING);
    assertEquals(0, compiler.getWarningCount());
  }

  // visit: this['x'] = 1 sets assignLhsChild to the GETELEM lhs, so the this inside is flagged.
  @Test
  public void testVisit_GetElemAsAssignmentTarget_ReportsWarning() throws Throwable {
    runPass("function f() { this['x'] = 1; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // shouldReportThis: reading this['x'] (GETELEM, not assignment) is still a property access.
  @Test
  public void testVisit_GetElemAsReadAccess_ReportsWarning() throws Throwable {
    runPass("function f() { var v = this['x']; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // constructor's level parameter is honored: CheckLevel.ERROR routes the report to errors.
  @Test
  public void testVisit_ErrorCheckLevel_ReportsAsError() throws Throwable {
    runPass("function f() { this.x = 1; }", CheckLevel.ERROR);
    assertEquals(1, compiler.getErrorCount());
  }



  // shouldTraverse: a qualified name without "prototype" is not exempt and must be flagged.
  @Test
  public void testVisit_NonPrototypeDeepPropertyAssignment_ReportsWarning() throws Throwable {
    runPass("a.b.c = function() { this.z = 1; };", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // shouldTraverse: lhs is a plain NAME (not GETPROP) - prototype checks are safely skipped.
  @Test
  public void testVisit_AssignmentToPlainNameLhs_ReportsWarning() throws Throwable {
    runPass("x = function() { this.n = 1; };", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }

  // Each nested function declaration is independently checked; both unsafe uses are flagged.
  @Test
  public void testVisit_NestedFunctionsIndependentlyChecked_ReportsTwoWarnings() throws Throwable {
    runPass("function Outer() { this.x = 1; function inner() { this.y = 2; } }", CheckLevel.WARNING);
    assertEquals(2, compiler.getWarningCount());
  }

  // Naming convention alone (capitalized function name) does not imply @constructor exemption.
  @Test
  public void testVisit_CapitalizedFunctionNameWithoutAnnotation_ReportsWarning() throws Throwable {
    runPass("function Foo() { this.x = 1; }", CheckLevel.WARNING);
    assertEquals(1, compiler.getWarningCount());
  }
}
