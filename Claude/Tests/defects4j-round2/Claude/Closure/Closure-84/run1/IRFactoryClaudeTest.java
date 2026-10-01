package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import org.junit.Test;

public class IRFactoryClaudeTest {

  private String compileToSource(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(extern, input, options);
    return compiler.toSource();
  }

  // processArrayLiteral: zero elements produces an empty array literal
  @Test
  public void testArrayLiteral_empty() throws Throwable {
    String out = compileToSource("var x = [];");
    assertTrue(out.contains("x=[]"));
  }

  // processArrayLiteral: multiple elements, skipCount == 0 branch
  @Test
  public void testArrayLiteral_simpleElements() throws Throwable {
    String out = compileToSource("var x = [1,2,3];");
    assertTrue(out.contains("x=[1,2,3]"));
  }

  // processArrayLiteral: skipCount > 0, hole recorded in the middle of the array
  @Test
  public void testArrayLiteral_holeInMiddle() throws Throwable {
    String out = compileToSource("var x = [1,,3];");
    assertTrue(out.contains("x=[1,,3]"));
  }

  // processArrayLiteral: skipCount > 0, hole recorded as the first element
  @Test
  public void testArrayLiteral_holeAtStart() throws Throwable {
    String out = compileToSource("var x = [,1,2];");
    assertTrue(out.contains("x=[,1,2]"));
  }

  // processAssignment / processInfixExpression: simple '=' assignment
  @Test
  public void testAssignment_simple() throws Throwable {
    String out = compileToSource("a=1;");
    assertTrue(out.contains("a=1"));
  }

  // processAssignment: compound assignment operator ASSIGN_ADD
  @Test
  public void testAssignment_compoundAdd() throws Throwable {
    String out = compileToSource("a+=1;");
    assertTrue(out.contains("a+=1"));
  }

  // processBreakStatement: break with a label child converted to LABEL_NAME
  @Test
  public void testBreakStatement_withLabel() throws Throwable {
    String out = compileToSource("foo:while(a){break foo}");
    assertTrue(out.contains("break foo"));
  }

  // processConditionalExpression: ternary hook expression
  @Test
  public void testConditionalExpression_ternary() throws Throwable {
    String out = compileToSource("var x = a?1:2;");
    assertTrue(out.contains("x=a?1:2"));
  }

  // processContinueStatement: continue with a label child
  @Test
  public void testContinueStatement_withLabel() throws Throwable {
    String out = compileToSource("foo:while(a){continue foo}");
    assertTrue(out.contains("continue foo"));
  }

  // processDoLoop: body executed, condition appended as second child
  @Test
  public void testDoLoop_structure() throws Throwable {
    String out = compileToSource("do{a()}while(b);");
    assertTrue(out.contains("while(b)"));
  }

  // processElementGet: computed property access via brackets
  @Test
  public void testElementGet_bracketAccess() throws Throwable {
    String out = compileToSource("a[b];");
    assertTrue(out.contains("a[b]"));
  }

  // processForLoop + processEmptyExpression: all three clauses empty
  @Test
  public void testForLoop_emptyClauses() throws Throwable {
    String out = compileToSource("for(;;){}");
    assertTrue(out.contains("for(;;)"));
  }

  // processForInLoop: for-in over an object
  @Test
  public void testForInLoop_structure() throws Throwable {
    String out = compileToSource("for(var k in o){}");
    assertTrue(out.contains("k in o"));
  }

  // processForLoop: initializer, condition and increment all present
  @Test
  public void testForLoop_fullClauses() throws Throwable {
    String out = compileToSource("for(var i=0;i<10;i++){}");
    assertTrue(out.contains("i<10"));
    assertTrue(out.contains("i++"));
  }

  // processFunctionCall: call with multiple arguments
  @Test
  public void testFunctionCall_withArguments() throws Throwable {
    String out = compileToSource("f(1,2);");
    assertTrue(out.contains("f(1,2)"));
  }

  // processNewExpression: reuses processFunctionCall for 'new'
  @Test
  public void testNewExpression_callsConstructor() throws Throwable {
    String out = compileToSource("new F();");
    assertTrue(out.contains("new F"));
  }

  // processFunctionNode: named function declaration with parameters
  @Test
  public void testFunctionNode_namedWithParams() throws Throwable {
    String out = compileToSource("function foo(a,b){return a+b;}");
    assertTrue(out.contains("function foo(a,b)"));
    assertTrue(out.contains("a+b"));
  }

  // processFunctionNode: anonymous function expression, isUnnamedFunction branch
  @Test
  public void testFunctionNode_anonymousExpression() throws Throwable {
    String out = compileToSource("var f = function(a){return a;};");
    assertTrue(out.contains("function(a)"));
  }

  // processIfStatement: no else branch present
  @Test
  public void testIfStatement_withoutElse() throws Throwable {
    String out = compileToSource("if(a)b();");
    assertTrue(out.contains("if(a)"));
    assertTrue(out.contains("b()"));
  }

  // processIfStatement: else branch present and transformed
  @Test
  public void testIfStatement_withElse() throws Throwable {
    String out = compileToSource("if(a){b()}else{c()}");
    assertTrue(out.contains("if(a)"));
    assertTrue(out.contains("c()"));
  }

  // processInfixExpression: plain binary ADD operator
  @Test
  public void testInfixExpression_binaryAdd() throws Throwable {
    String out = compileToSource("var x = a+b;");
    assertTrue(out.contains("x=a+b"));
  }

  // processKeywordLiteral: true/false/null/this keyword tokens
  @Test
  public void testKeywordLiterals_trueFalseNullThis() throws Throwable {
    String out = compileToSource("var x=true;var y=false;var z=null;var w=this;");
    assertTrue(out.contains("x=true"));
    assertTrue(out.contains("y=false"));
    assertTrue(out.contains("z=null"));
    assertTrue(out.contains("w=this"));
  }

  // processLabel/processLabeledStatement: multiple chained labels, prev!=null branch
  @Test
  public void testLabeledStatement_multipleLabels() throws Throwable {
    String out = compileToSource("outer:inner:x();");
    assertTrue(out.contains("outer:"));
    assertTrue(out.contains("inner:"));
  }

  // processNumberLiteral: integer value
  @Test
  public void testNumberLiteral_integer() throws Throwable {
    String out = compileToSource("var x = 5;");
    assertTrue(out.contains("x=5"));
  }

  // processNumberLiteral: decimal value
  @Test
  public void testNumberLiteral_decimal() throws Throwable {
    String out = compileToSource("var x = 3.14;");
    assertTrue(out.contains("x=3.14"));
  }

  // processObjectLiteral: a single unquoted-key property
  @Test
  public void testObjectLiteral_singleProperty() throws Throwable {
    String out = compileToSource("var x = {a:1};");
    assertTrue(out.contains("x={a:1}"));
  }

  // processObjectLiteral: loop over multiple properties
  @Test
  public void testObjectLiteral_multipleProperties() throws Throwable {
    String out = compileToSource("var x = {a:1,b:2};");
    assertTrue(out.contains("x={a:1,b:2}"));
  }

  // processParenthesizedExpression: parens needed to preserve operator precedence
  @Test
  public void testParenthesizedExpression_precedencePreserved() throws Throwable {
    String out = compileToSource("var x = (1+2)*3;");
    assertTrue(out.contains("(1+2)*3"));
  }

  // processPropertyGet: dotted property access, property transformed to STRING
  @Test
  public void testPropertyGet_dotAccess() throws Throwable {
    String out = compileToSource("a.b;");
    assertTrue(out.contains("a.b"));
  }

  // processRegExpLiteral: pattern plus non-empty flags string
  @Test
  public void testRegExpLiteral_withFlags() throws Throwable {
    String out = compileToSource("var x = /ab+c/gi;");
    assertTrue(out.contains("/ab+c/gi"));
  }

  // processSwitchStatement/processSwitchCase: case and default both present
  @Test
  public void testSwitchStatement_caseAndDefault() throws Throwable {
    String out = compileToSource("switch(a){case 1:b();break;default:c()}");
    assertTrue(out.contains("case 1"));
    assertTrue(out.contains("default"));
  }

  // processSwitchCase: default-only switch, isDefault() branch
  @Test
  public void testSwitchStatement_defaultOnly() throws Throwable {
    String out = compileToSource("switch(a){default:b()}");
    assertTrue(out.contains("default"));
    assertTrue(out.contains("b()"));
  }

  // processThrowStatement: throw expression
  @Test
  public void testThrowStatement_simple() throws Throwable {
    String out = compileToSource("throw a;");
    assertTrue(out.contains("throw a"));
  }

  // processTryStatement: try with a single catch clause
  @Test
  public void testTryStatement_withCatch() throws Throwable {
    String out = compileToSource("try{a()}catch(e){b()}");
    assertTrue(out.contains("catch(e)"));
  }

  // processTryStatement: try with a finally block, no catch clauses
  @Test
  public void testTryStatement_withFinally() throws Throwable {
    String out = compileToSource("try{a()}finally{b()}");
    assertTrue(out.contains("finally"));
  }

  // processUnaryExpression: NEG applied to a NUMBER literal folds into a negative literal
  @Test
  public void testUnaryExpression_negativeNumberFolded() throws Throwable {
    String out = compileToSource("var x = -5;");
    assertTrue(out.contains("x=-5"));
  }

  // processUnaryExpression: double negation folds back to the original positive value
  @Test
  public void testUnaryExpression_doubleNegationFoldsToPositive() throws Throwable {
    String out = compileToSource("var x = - -5;");
    assertTrue(out.contains("x=5"));
  }

  // processUnaryExpression: postfix increment, isPostfix() true branch
  @Test
  public void testUnaryExpression_postfixIncrement() throws Throwable {
    String out = compileToSource("a++;");
    assertTrue(out.contains("a++"));
  }

  // processUnaryExpression: prefix increment, isPostfix() false branch
  @Test
  public void testUnaryExpression_prefixIncrement() throws Throwable {
    String out = compileToSource("++a;");
    assertTrue(out.contains("++a"));
  }

  // processUnaryExpression: delete and typeof take the non-NEG else branch
  @Test
  public void testUnaryExpression_deleteAndTypeof() throws Throwable {
    String out = compileToSource("delete a.b; typeof a;");
    assertTrue(out.contains("delete a.b"));
    assertTrue(out.contains("typeof a"));
  }

  // processVariableDeclaration: multiple variable declarators in one statement
  @Test
  public void testVariableDeclaration_multipleDeclarators() throws Throwable {
    String out = compileToSource("var a=1,b=2;");
    assertTrue(out.contains("a=1"));
    assertTrue(out.contains("b=2"));
  }

  // processWhileLoop: condition and block body
  @Test
  public void testWhileLoop_structure() throws Throwable {
    String out = compileToSource("while(a){b()}");
    assertTrue(out.contains("while(a)"));
  }

  // processWithStatement: with-block wraps statement into a transformed block
  @Test
  public void testWithStatement_structure() throws Throwable {
    String out = compileToSource("with(a){b()}");
    assertTrue(out.contains("with(a)"));
  }
}
