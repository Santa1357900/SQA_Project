package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.Result;

import java.util.ArrayList;
import java.util.List;

public class IRFactoryClaudeTest {

  private Compiler compiler;

  private Result compileJs(String js) {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    return compiler.compile(externs, inputs, options);
  }

  // processVariableDeclaration / processVariableInitializer: simple var statement compiles cleanly.
  @Test
  public void testProcessVariableDeclaration_simpleVar_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = 1;");
    assertTrue(result.success);
  }

  // processVariableDeclaration loop over multiple VariableInitializer children.
  @Test
  public void testProcessVariableDeclaration_multipleDeclarators_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = 1, b = 2, c = 3;");
    assertTrue(result.success);
  }

  // processArrayLiteral: zero elements, skipCount stays 0.
  @Test
  public void testProcessArrayLiteral_emptyArray_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = [];");
    assertTrue(result.success);
  }

  // processArrayLiteral: no EMPTY children (skipCount==0 branch), values preserved in output.
  @Test
  public void testProcessArrayLiteral_noHoles_elementsPreserved() throws Throwable {
    Result result = compileJs("var a = [1,2,3];");
    assertTrue(result.success);
    String normalized = compiler.toSource().replace(" ", "");
    assertTrue(normalized.contains("1,2,3"));
  }

  // processArrayLiteral: a single hole triggers the skipCount>0 branch.
  @Test
  public void testProcessArrayLiteral_singleHole_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = [1,,3];");
    assertTrue(result.success);
  }

  // processArrayLiteral: multiple holes exercise the skipIndexes loop more than once.
  @Test
  public void testProcessArrayLiteral_multipleHoles_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = [1,,3,,5];");
    assertTrue(result.success);
  }

  // processArrayLiteral: destructuring assignment target must be rejected (reportDestructuringAssign).
  @Test
  public void testProcessArrayLiteral_destructuringAssignment_reportsError() throws Throwable {
    Result result = compileJs("var a, b; [a, b] = [1, 2];");
    assertFalse(result.success);
  }

  // processObjectLiteral: simple unquoted key/value pairs.
  @Test
  public void testProcessObjectLiteral_simpleProperties_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var o = {a: 1, b: 2};");
    assertTrue(result.success);
  }

  // processObjectLiteral + transformAsString: quoted key stays a quoted string key.
  @Test
  public void testProcessObjectLiteral_quotedKey_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var o = {'a': 1};");
    assertTrue(result.success);
  }

  // processFunctionNode: named function declaration (not the anonymous branch).
  @Test
  public void testProcessFunctionNode_namedFunctionDeclaration_compilesSuccessfully() throws Throwable {
    Result result = compileJs("function foo(x, y) { return x + y; }");
    assertTrue(result.success);
  }

  // processFunctionNode: anonymous function expression triggers isUnnamedFunction branch.
  @Test
  public void testProcessFunctionNode_anonymousFunctionExpression_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var f = function() { return 1; };");
    assertTrue(result.success);
  }

  // processFunctionCall: loop over call arguments.
  @Test
  public void testProcessFunctionCall_withArguments_compilesSuccessfully() throws Throwable {
    Result result = compileJs("foo(1, 2, 3);");
    assertTrue(result.success);
  }

  // processNewExpression delegates to processFunctionCall.
  @Test
  public void testProcessNewExpression_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var o = new Foo(1, 2);");
    assertTrue(result.success);
  }

  // processPropertyGet + transformAsString(NAME -> STRING) path.
  @Test
  public void testProcessPropertyGet_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = a.b;");
    assertTrue(result.success);
  }

  // processElementGet.
  @Test
  public void testProcessElementGet_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = a[b];");
    assertTrue(result.success);
  }

  // processConditionalExpression (ternary HOOK).
  @Test
  public void testProcessConditionalExpression_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = (a ? 1 : 2);");
    assertTrue(result.success);
  }

  // processIfStatement: both then-part and else-part present.
  @Test
  public void testProcessIfStatement_withElse_compilesSuccessfully() throws Throwable {
    Result result = compileJs("if (a) { x = 1; } else { x = 2; }");
    assertTrue(result.success);
  }

  // transformBlock: non-block statement body gets wrapped into a BLOCK node.
  @Test
  public void testTransformBlock_ifWithoutBraces_compilesSuccessfully() throws Throwable {
    Result result = compileJs("if (a) x = 1;");
    assertTrue(result.success);
  }

  // transformBlock: EMPTY body becomes a BLOCK marked wasEmptyNode.
  @Test
  public void testTransformBlock_ifWithEmptyStatement_compilesSuccessfully() throws Throwable {
    Result result = compileJs("if (a) ; else x = 1;");
    assertTrue(result.success);
  }

  // processWhileLoop.
  @Test
  public void testProcessWhileLoop_compilesSuccessfully() throws Throwable {
    Result result = compileJs("while (a) { x = 1; }");
    assertTrue(result.success);
  }

  // processDoLoop.
  @Test
  public void testProcessDoLoop_compilesSuccessfully() throws Throwable {
    Result result = compileJs("do { x = 1; } while (a);");
    assertTrue(result.success);
  }

  // processForLoop with init/condition/increment and block body.
  @Test
  public void testProcessForLoop_compilesSuccessfully() throws Throwable {
    Result result = compileJs("for (var i = 0; i < 10; i++) { x = i; }");
    assertTrue(result.success);
  }

  // processForInLoop.
  @Test
  public void testProcessForInLoop_compilesSuccessfully() throws Throwable {
    Result result = compileJs("for (var k in obj) { x = k; }");
    assertTrue(result.success);
  }

  // processSwitchStatement + processSwitchCase (CASE and DEFAULT branches).
  @Test
  public void testProcessSwitchStatement_withCaseAndDefault_compilesSuccessfully() throws Throwable {
    Result result = compileJs("switch (x) { case 1: y = 1; break; default: y = 2; }");
    assertTrue(result.success);
  }

  // processTryStatement: catch clause present, lineSet becomes true inside the loop.
  @Test
  public void testProcessTryStatement_withCatchAndFinally_compilesSuccessfully() throws Throwable {
    Result result = compileJs("try { x = 1; } catch (e) { x = 2; } finally { x = 3; }");
    assertTrue(result.success);
  }

  // processTryStatement: no catch clauses, only finally (lineSet stays false branch).
  @Test
  public void testProcessTryStatement_finallyOnlyNoCatch_compilesSuccessfully() throws Throwable {
    Result result = compileJs("try { x = 1; } finally { x = 2; }");
    assertTrue(result.success);
  }

  // processThrowStatement.
  @Test
  public void testProcessThrowStatement_compilesSuccessfully() throws Throwable {
    Result result = compileJs("function f() { throw new Error('e'); }");
    assertTrue(result.success);
  }

  // processWithStatement.
  @Test
  public void testProcessWithStatement_compilesSuccessfully() throws Throwable {
    Result result = compileJs("with (obj) { x = 1; }");
    assertTrue(result.success);
  }

  // processLabeledStatement + processBreakStatement with a label.
  @Test
  public void testProcessLabeledStatement_breakWithLabel_compilesSuccessfully() throws Throwable {
    Result result = compileJs("outer: while (a) { break outer; }");
    assertTrue(result.success);
  }

  // processLabeledStatement + processContinueStatement with a label.
  @Test
  public void testProcessLabeledStatement_continueWithLabel_compilesSuccessfully() throws Throwable {
    Result result = compileJs("outer: while (a) { continue outer; }");
    assertTrue(result.success);
  }

  // processRegExpLiteral: flags-present branch.
  @Test
  public void testProcessRegExpLiteral_withFlags_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var r = /abc/gi;");
    assertTrue(result.success);
  }

  // processRegExpLiteral: no-flags branch (flags null/empty).
  @Test
  public void testProcessRegExpLiteral_withoutFlags_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var r = /abc/;");
    assertTrue(result.success);
  }

  // processStringLiteral: literal content round-trips through the compile.
  @Test
  public void testProcessStringLiteral_contentPreserved() throws Throwable {
    Result result = compileJs("var s = 'hello';");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("hello"));
  }

  // processUnaryExpression: NEG folded directly into the NUMBER literal.
  @Test
  public void testProcessUnaryExpression_negativeNumberLiteral_foldedCorrectly() throws Throwable {
    Result result = compileJs("var x = -5;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("-5"));
  }

  // processUnaryExpression: INC on a valid NAME target, no error reported.
  @Test
  public void testProcessUnaryExpression_incrementValidTarget_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = 0; x++;");
    assertTrue(result.success);
  }

  // processUnaryExpression: DEC on a valid NAME target, no error reported.
  @Test
  public void testProcessUnaryExpression_decrementValidTarget_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x = 0; x--;");
    assertTrue(result.success);
  }

  // processAssignment: invalid assignment target (number literal) must report an error.
  @Test
  public void testProcessAssignment_invalidTarget_reportsError() throws Throwable {
    Result result = compileJs("5 = 3;");
    assertFalse(result.success);
  }

  // processUnaryExpression: invalid increment target must report an error.
  @Test
  public void testProcessUnaryExpression_invalidIncrementTarget_reportsError() throws Throwable {
    Result result = compileJs("5++;");
    assertFalse(result.success);
  }

  // processUnaryExpression: invalid decrement target must report an error.
  @Test
  public void testProcessUnaryExpression_invalidDecrementTarget_reportsError() throws Throwable {
    Result result = compileJs("5--;");
    assertFalse(result.success);
  }

  // parseDirectives/isDirective: a leading 'use strict' directive is recognized and stripped.
  @Test
  public void testParseDirectives_useStrict_compilesSuccessfully() throws Throwable {
    Result result = compileJs("'use strict'; var x = 1;");
    assertTrue(result.success);
  }

  // processInfixExpression: comma operator token mapping.
  @Test
  public void testProcessInfixExpression_commaOperator_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a; a = (1, 2, 3);");
    assertTrue(result.success);
  }

  // processParenthesizedExpression: PARENTHESIZED_PROP branch does not break compilation.
  @Test
  public void testProcessParenthesizedExpression_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var y = (1 + 2) * 3;");
    assertTrue(result.success);
  }

  // processUnaryExpression: typeof/void/delete token mappings.
  @Test
  public void testProcessUnaryExpression_typeofVoidDelete_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = {b:1}; typeof a; void 0; delete a.b;");
    assertTrue(result.success);
  }

  // processKeywordLiteral: true/false/null/this token mappings.
  @Test
  public void testProcessKeywordLiteral_trueFalseNullThis_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var a = true; var b = false; var c = null; function f(){ return this; }");
    assertTrue(result.success);
  }

  // processVariableInitializer: VariableInitializer without an initializer (no addChildToBack call).
  @Test
  public void testProcessVariableInitializer_withoutInitializer_compilesSuccessfully() throws Throwable {
    Result result = compileJs("var x;");
    assertTrue(result.success);
  }
}
