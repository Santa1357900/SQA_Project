package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

public class CheckGlobalThisClaudeTest {

  private Result runCheck(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckGlobalThisLevel(CheckLevel.WARNING);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    return compiler.compile(externs, input, options);
  }

  // shouldTraverse: ASSIGN lhs is this.x -> assignLhsChild set -> visit reports warning
  @Test
  public void testShouldTraverse_globalPropertyAssignmentThis_reportsWarning() throws Throwable {
    Result result = runCheck("this.x = 1;");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // visit: this.x as plain property read (not assignment) -> NodeUtil.isGet(parent) true -> reports
  @Test
  public void testVisit_globalPropertyReadThis_reportsWarning() throws Throwable {
    Result result = runCheck("var y = this.x;");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // visit: bare this statement, parent is EXPR_RESULT, not a get -> no warning
  @Test
  public void testVisit_bareGlobalThisStatement_noWarning() throws Throwable {
    Result result = runCheck("this;");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // Javadoc's own example of unsafe usage inside an unannotated function
  @Test
  public void testVisit_javadocExampleEvilFunction_reportsWarning() throws Throwable {
    Result result = runCheck("function evil() { this.useful = undefined; }");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // shouldTraverse: FUNCTION with @constructor JSDoc -> traversal skipped -> no warning
  @Test
  public void testShouldTraverse_constructorAnnotatedFunction_noWarning() throws Throwable {
    Result result = runCheck("/** @constructor */ function Foo() { this.x = 1; }");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: FUNCTION with @this JSDoc -> traversal skipped -> no warning
  @Test
  public void testShouldTraverse_thisAnnotatedFunction_noWarning() throws Throwable {
    Result result = runCheck("/** @this {Object} */ function foo() { this.x = 1; }");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: FUNCTION with @interface JSDoc -> traversal skipped -> no warning
  @Test
  public void testShouldTraverse_interfaceAnnotatedFunction_noWarning() throws Throwable {
    Result result = runCheck("/** @interface */ function Foo() { this.x = 1; }");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: FUNCTION with @override JSDoc directly attached -> traversal skipped
  @Test
  public void testShouldTraverse_overrideAnnotatedFunction_noWarning() throws Throwable {
    Result result = runCheck("x.foo = /** @override */ function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // getFunctionJsDocInfo: JSDoc attached via "var ... x = function() {};" pattern (gramps VAR)
  @Test
  public void testGetFunctionJsDocInfo_varDeclarationJsDoc_noWarning() throws Throwable {
    Result result = runCheck("/** @constructor */ var Foo = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // getFunctionJsDocInfo: JSDoc attached via "... x = function() {};" pattern (parent ASSIGN)
  @Test
  public void testGetFunctionJsDocInfo_assignJsDoc_noWarning() throws Throwable {
    Result result = runCheck("/** @constructor */ Foo = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: Foo.prototype.bar = function(){} is exempted as a prototype method
  @Test
  public void testShouldTraverse_prototypeMethodAssignment_noWarning() throws Throwable {
    Result result = runCheck("Foo.prototype.bar = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: Foo.prototype = function(){} is exempted directly
  @Test
  public void testShouldTraverse_directPrototypeAssignment_noWarning() throws Throwable {
    Result result = runCheck("Foo.prototype = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse boundary: Foo.bar (not .prototype.bar) is NOT exempted -> warning expected
  @Test
  public void testShouldTraverse_nonPrototypePropertyAssignment_reportsWarning() throws Throwable {
    Result result = runCheck("Foo.bar = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // shouldTraverse: pType == Token.STRING branch (object literal string key) -> traversed, reports
  @Test
  public void testShouldTraverse_objectLiteralStringKeyFunction_reportsWarning() throws Throwable {
    Result result = runCheck("var a = {foo: function() { this.x = 1; }};");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // shouldTraverse: pType == Token.NUMBER branch (object literal numeric key) -> traversed, reports
  @Test
  public void testShouldTraverse_objectLiteralNumberKeyFunction_reportsWarning() throws Throwable {
    Result result = runCheck("var a = {1: function() { this.x = 1; }};");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // shouldTraverse: function passed as call argument has parent CALL, not in allowed list -> skipped
  @Test
  public void testShouldTraverse_functionArgumentToCall_noWarning() throws Throwable {
    Result result = runCheck("foo(function() { this.x = 1; });");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // Javadoc note: assigning this to a variable is not tracked -> no warning
  @Test
  public void testVisit_varAssignedThis_noWarning() throws Throwable {
    Result result = runCheck("var a = this;");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // plain assignment "a = this;" - this is rhs, assignLhsChild cleared before visiting it -> no warning
  @Test
  public void testVisit_plainAssignedThis_noWarning() throws Throwable {
    Result result = runCheck("var a; a = this;");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // both lhs and rhs of assignment use global this in property access -> two warnings
  @Test
  public void testVisit_bothSidesThisPropertyAccess_reportsTwoWarnings() throws Throwable {
    Result result = runCheck("this.x = this.y;");
    assertEquals(0, result.errors.length);
    assertEquals(2, result.warnings.length);
  }

  // constructor exemption also blocks traversal of nested functions inside it
  @Test
  public void testShouldTraverse_constructorWithNestedFunction_noWarning() throws Throwable {
    Result result = runCheck(
        "/** @constructor */ function Foo() { function helper() { this.x = 1; } }");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // nested function declaration without exemption (parent BLOCK) is still checked
  @Test
  public void testShouldTraverse_nestedFunctionDeclaration_reportsWarning() throws Throwable {
    Result result = runCheck("function outer() { function inner() { this.x = 1; } }");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // multiple independent statements accumulate separate warnings and reset state correctly
  @Test
  public void testVisit_multipleStatements_reportsTwoWarnings() throws Throwable {
    Result result = runCheck("this.x = 1; this.y = 2;");
    assertEquals(0, result.errors.length);
    assertEquals(2, result.warnings.length);
  }

  // baseline: no this usage at all -> no warnings, no errors
  @Test
  public void testRunCheck_noThisUsage_noWarning() throws Throwable {
    Result result = runCheck("var x = 1;");
    assertEquals(0, result.errors.length);
    assertEquals(0, result.warnings.length);
  }

  // shouldTraverse: pType == Token.NAME branch (function expression assigned to var) -> reports
  @Test
  public void testShouldTraverse_functionExpressionAssignedToVar_reportsWarning() throws Throwable {
    Result result = runCheck("var f = function() { this.x = 1; };");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }

  // shouldTraverse: pType == Token.BLOCK branch (function declared inside an if-block) -> reports
  @Test
  public void testShouldTraverse_blockScopedFunctionDeclaration_reportsWarning() throws Throwable {
    Result result = runCheck("if (true) { function f() { this.x = 1; } }");
    assertEquals(0, result.errors.length);
    assertEquals(1, result.warnings.length);
  }
}
