package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class IRFactoryClaudeTest {

  private Compiler compiler;

  private Node parse(String js) {
    CompilerOptions options = new CompilerOptions();
    compiler = new Compiler();
    compiler.initOptions(options);
    return compiler.parseTestCode(js);
  }

  private Node parseAcceptingConst(String js) {
    CompilerOptions options = new CompilerOptions();
    options.setAcceptConstKeyword(true);
    compiler = new Compiler();
    compiler.initOptions(options);
    return compiler.parseTestCode(js);
  }

  // processArrayLiteral: non-empty literal produces ARRAYLIT with numeric children
  @Test
  public void testProcessArrayLiteral_nonEmpty_createsArrayLitWithChildren() throws Throwable {
    Node script = parse("[1, 2, 3];");
    Node arrayLit = script.getFirstChild().getFirstChild();
    assertEquals(Token.ARRAYLIT, arrayLit.getType());
    assertTrue(arrayLit.hasChildren());
    assertEquals(1.0, arrayLit.getFirstChild().getDouble(), 1e-9);
  }

  // processArrayLiteral: empty literal has no children
  @Test
  public void testProcessArrayLiteral_empty_hasNoChildren() throws Throwable {
    Node script = parse("[];");
    Node arrayLit = script.getFirstChild().getFirstChild();
    assertEquals(Token.ARRAYLIT, arrayLit.getType());
    assertFalse(arrayLit.hasChildren());
  }

  // processAssignment: valid assignment target (NAME) produces no errors
  @Test
  public void testProcessAssignment_validNameTarget_noError() throws Throwable {
    parse("x = 1;");
    assertEquals(0, compiler.getErrorCount());
  }

  // processAssignment: invalid assignment target (CALL) must report an error
  @Test
  public void testProcessAssignment_invalidCallTarget_reportsError() throws Throwable {
    parse("foo() = 1;");
    assertTrue(compiler.getErrorCount() > 0);
  }

  // parseDirectives: allowed "use strict" directive is stripped from the body
  @Test
  public void testParseDirectives_useStrict_removedFromScriptBody() throws Throwable {
    Node script = parse("\"use strict\";\nvar x = 1;");
    assertEquals(Token.VAR, script.getFirstChild().getType());
  }

  // parseDirectives: disallowed directive string is NOT removed
  @Test
  public void testParseDirectives_disallowedDirective_remainsAsExprResult() throws Throwable {
    Node script = parse("\"use asm\";\nvar x = 1;");
    assertEquals(Token.EXPR_RESULT, script.getFirstChild().getType());
  }

  // processBreakStatement: break without label has no children
  @Test
  public void testProcessBreakStatement_withoutLabel_hasNoChildren() throws Throwable {
    Node script = parse("while (true) { break; }");
    Node whileNode = script.getFirstChild();
    Node block = whileNode.getFirstChild().getNext();
    Node breakNode = block.getFirstChild();
    assertEquals(Token.BREAK, breakNode.getType());
    assertFalse(breakNode.hasChildren());
  }

  // processContinueStatement: continue without label has no children
  @Test
  public void testProcessContinueStatement_withoutLabel_hasNoChildren() throws Throwable {
    Node script = parse("while (true) { continue; }");
    Node whileNode = script.getFirstChild();
    Node block = whileNode.getFirstChild().getNext();
    Node continueNode = block.getFirstChild();
    assertEquals(Token.CONTINUE, continueNode.getType());
    assertFalse(continueNode.hasChildren());
  }

  // processConditionalExpression: ternary produces HOOK with test/true/false children
  @Test
  public void testProcessConditionalExpression_createsHookWithThreeChildren() throws Throwable {
    Node script = parse("a ? b : c;");
    Node hook = script.getFirstChild().getFirstChild();
    assertEquals(Token.HOOK, hook.getType());
    Node first = hook.getFirstChild();
    Node second = first.getNext();
    Node third = second.getNext();
    assertEquals(Token.NAME, third.getType());
  }

  // processDoLoop: do-while produces DO node with block body then condition
  @Test
  public void testProcessDoLoop_createsDoNode() throws Throwable {
    Node script = parse("do { x(); } while (a);");
    Node doNode = script.getFirstChild();
    assertEquals(Token.DO, doNode.getType());
    assertTrue(doNode.getFirstChild().isBlock());
    assertEquals(Token.NAME, doNode.getFirstChild().getNext().getType());
  }

  // processElementGet: bracket access produces GETELEM with target and element
  @Test
  public void testProcessElementGet_createsGetElemNode() throws Throwable {
    Node script = parse("a[b];");
    Node getElem = script.getFirstChild().getFirstChild();
    assertEquals(Token.GETELEM, getElem.getType());
    assertEquals(Token.NAME, getElem.getFirstChild().getType());
    assertEquals(Token.NAME, getElem.getFirstChild().getNext().getType());
  }

  // processEmptyExpression: for(;;) has EMPTY initializer as first child
  @Test
  public void testProcessEmptyExpression_forLoopNoClauses_firstChildIsEmpty() throws Throwable {
    Node script = parse("for (;;) {}");
    Node forNode = script.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // processExpressionStatement: bare name expression wraps into EXPR_RESULT
  @Test
  public void testProcessExpressionStatement_createsExprResultWithNameChild() throws Throwable {
    Node script = parse("a;");
    Node exprResult = script.getFirstChild();
    assertEquals(Token.EXPR_RESULT, exprResult.getType());
    assertEquals(Token.NAME, exprResult.getFirstChild().getType());
  }

  // processForInLoop: for-in produces FOR with iterator, object, body
  @Test
  public void testProcessForInLoop_createsForNode() throws Throwable {
    Node script = parse("for (a in b) {}");
    Node forNode = script.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    Node iterator = forNode.getFirstChild();
    Node iterated = iterator.getNext();
    assertEquals(Token.NAME, iterated.getType());
    assertTrue(iterated.getNext().isBlock());
  }

  // processForLoop: classic for loop initializer transforms to VAR
  @Test
  public void testProcessForLoop_initializerIsVar() throws Throwable {
    Node script = parse("for (var i = 0; i < 10; i++) {}");
    Node forNode = script.getFirstChild();
    assertEquals(Token.VAR, forNode.getFirstChild().getType());
  }

  // processFunctionCall: call node holds target then each argument in order
  @Test
  public void testProcessFunctionCall_createsCallNodeWithArguments() throws Throwable {
    Node script = parse("f(1, 2);");
    Node call = script.getFirstChild().getFirstChild();
    assertEquals(Token.CALL, call.getType());
    Node target = call.getFirstChild();
    assertEquals(Token.NAME, target.getType());
    assertEquals(1.0, target.getNext().getDouble(), 1e-9);
    assertEquals(2.0, target.getNext().getNext().getDouble(), 1e-9);
  }

  // processFunctionNode: named function statement's first child is its NAME
  @Test
  public void testProcessFunctionNode_named_firstChildIsNameNode() throws Throwable {
    Node script = parse("function foo(a, b) { return a; }");
    Node fn = script.getFirstChild();
    assertEquals(Token.FUNCTION, fn.getType());
    Node name = fn.getFirstChild();
    assertEquals(Token.NAME, name.getType());
    assertEquals("foo", name.getString());
  }

  // processFunctionNode: unnamed function statement is illegal and reports error
  @Test
  public void testProcessFunctionNode_unnamedStatement_reportsError() throws Throwable {
    parse("function() {}");
    assertTrue(compiler.getErrorCount() > 0);
  }

  // processFunctionNode: unnamed function expression gets an empty-string name, no error
  @Test
  public void testProcessFunctionNode_unnamedExpression_nameIsEmptyString() throws Throwable {
    Node script = parse("var f = function() {};");
    Node fnName = script.getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.FUNCTION, fnName.getParent() == null ? -1 : fnName.getParent().getType() == Token.FUNCTION ? Token.FUNCTION : -1);
    assertEquals(Token.NAME, fnName.getFirstChild().getType());
    assertEquals("", fnName.getFirstChild().getString());
  }

  // processIfStatement: if without else has exactly two children
  @Test
  public void testProcessIfStatement_withoutElse_hasTwoChildren() throws Throwable {
    Node script = parse("if (a) b;");
    Node ifNode = script.getFirstChild();
    assertEquals(Token.IF, ifNode.getType());
    Node cond = ifNode.getFirstChild();
    Node then = cond.getNext();
    assertTrue(then.isBlock());
    assertNull(then.getNext());
  }

  // processIfStatement: if with else has a third child block
  @Test
  public void testProcessIfStatement_withElse_hasThirdChildBlock() throws Throwable {
    Node script = parse("if (a) b; else c;");
    Node ifNode = script.getFirstChild();
    Node elseBlock = ifNode.getFirstChild().getNext().getNext();
    assertNotNull(elseBlock);
    assertTrue(elseBlock.isBlock());
  }

  // processInfixExpression: '+' operator produces ADD node with two NAME children
  @Test
  public void testProcessInfixExpression_addCreatesAddNode() throws Throwable {
    Node script = parse("a + b;");
    Node add = script.getFirstChild().getFirstChild();
    assertEquals(Token.ADD, add.getType());
    assertEquals(Token.NAME, add.getFirstChild().getType());
    assertEquals(Token.NAME, add.getFirstChild().getNext().getType());
  }

  // processKeywordLiteral: 'true' keyword becomes a childless TRUE node
  @Test
  public void testProcessKeywordLiteral_true_createsTrueNode() throws Throwable {
    Node script = parse("true;");
    Node trueNode = script.getFirstChild().getFirstChild();
    assertEquals(Token.TRUE, trueNode.getType());
    assertFalse(trueNode.hasChildren());
  }

  // processLabeledStatement: label produces LABEL node with LABEL_NAME then statement
  @Test
  public void testProcessLabeledStatement_firstChildIsLabelName() throws Throwable {
    Node script = parse("a: x;");
    Node label = script.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    Node labelName = label.getFirstChild();
    assertEquals(Token.LABEL_NAME, labelName.getType());
    assertEquals("a", labelName.getString());
    assertEquals(Token.EXPR_RESULT, labelName.getNext().getType());
  }

  // processNewExpression: 'new Foo()' produces NEW node with NAME target
  @Test
  public void testProcessNewExpression_createsNewNode() throws Throwable {
    Node script = parse("new Foo();");
    Node newNode = script.getFirstChild().getFirstChild();
    assertEquals(Token.NEW, newNode.getType());
    assertEquals(Token.NAME, newNode.getFirstChild().getType());
  }

  // processNumberLiteral: numeric literal keeps its double value
  @Test
  public void testProcessNumberLiteral_createsNumberNodeWithValue() throws Throwable {
    Node script = parse("5;");
    Node num = script.getFirstChild().getFirstChild();
    assertEquals(Token.NUMBER, num.getType());
    assertEquals(5.0, num.getDouble(), 1e-9);
  }

  // processObjectLiteral: both quoted and unquoted keys become STRING-typed keys
  @Test
  public void testProcessObjectLiteral_quotedAndUnquotedKeys_bothBecomeStringType() throws Throwable {
    Node script = parse("var o = {a: 1, 'b': 2};");
    Node objLit = script.getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.OBJECTLIT, objLit.getType());
    Node keyA = objLit.getFirstChild();
    assertEquals(Token.STRING, keyA.getType());
    assertEquals("a", keyA.getString());
    assertEquals(1.0, keyA.getFirstChild().getDouble(), 1e-9);
    Node keyB = keyA.getNext();
    assertEquals("b", keyB.getString());
  }

  // processPropertyGet: dotted property access produces GETPROP with STRING property
  @Test
  public void testProcessPropertyGet_createsGetPropWithStringChild() throws Throwable {
    Node script = parse("a.b;");
    Node getProp = script.getFirstChild().getFirstChild();
    assertEquals(Token.GETPROP, getProp.getType());
    Node prop = getProp.getFirstChild().getNext();
    assertEquals(Token.STRING, prop.getType());
    assertEquals("b", prop.getString());
  }

  // processRegExpLiteral: regex with flags produces REGEXP with pattern and flags strings
  @Test
  public void testProcessRegExpLiteral_withFlags_createsRegexpNode() throws Throwable {
    Node script = parse("/abc/g;");
    Node regexp = script.getFirstChild().getFirstChild();
    assertEquals(Token.REGEXP, regexp.getType());
    Node pattern = regexp.getFirstChild();
    assertEquals("abc", pattern.getString());
    assertEquals("g", pattern.getNext().getString());
  }

  // processReturnStatement: return with a value carries the expression as a child
  @Test
  public void testProcessReturnStatement_withValue_hasNumberChild() throws Throwable {
    Node script = parse("function f() { return 1; }");
    Node body = script.getFirstChild().getFirstChild().getNext().getNext();
    Node ret = body.getFirstChild();
    assertEquals(Token.RETURN, ret.getType());
    assertEquals(1.0, ret.getFirstChild().getDouble(), 1e-9);
  }

  // processReturnStatement: bare return has no children
  @Test
  public void testProcessReturnStatement_withoutValue_hasNoChildren() throws Throwable {
    Node script = parse("function f() { return; }");
    Node body = script.getFirstChild().getFirstChild().getNext().getNext();
    Node ret = body.getFirstChild();
    assertEquals(Token.RETURN, ret.getType());
    assertFalse(ret.hasChildren());
  }

  // processStringLiteral: string literal value is preserved
  @Test
  public void testProcessStringLiteral_createsStringNodeWithValue() throws Throwable {
    Node script = parse("\"hello\";");
    Node str = script.getFirstChild().getFirstChild();
    assertEquals(Token.STRING, str.getType());
    assertEquals("hello", str.getString());
  }

  // processSwitchStatement/processSwitchCase: switch builds expr, case and default_case
  @Test
  public void testProcessSwitchStatement_createsSwitchWithCaseAndDefault() throws Throwable {
    Node script = parse("switch (a) { case 1: b(); break; default: c(); }");
    Node sw = script.getFirstChild();
    assertEquals(Token.SWITCH, sw.getType());
    Node caseNode = sw.getFirstChild().getNext();
    assertEquals(Token.CASE, caseNode.getType());
    assertEquals(Token.DEFAULT_CASE, caseNode.getNext().getType());
  }

  // processThrowStatement: throw produces THROW node wrapping the expression
  @Test
  public void testProcessThrowStatement_createsThrowNode() throws Throwable {
    Node script = parse("throw a;");
    Node throwNode = script.getFirstChild();
    assertEquals(Token.THROW, throwNode.getType());
    assertEquals(Token.NAME, throwNode.getFirstChild().getType());
  }

  // processTryStatement: try/catch/finally produces three BLOCK-typed children
  @Test
  public void testProcessTryStatement_withCatchAndFinally_hasThreeBlockChildren() throws Throwable {
    Node script = parse("try { a(); } catch (e) { b(); } finally { c(); }");
    Node tryNode = script.getFirstChild();
    assertEquals(Token.TRY, tryNode.getType());
    Node tryBlock = tryNode.getFirstChild();
    Node catchWrapper = tryBlock.getNext();
    Node finallyBlock = catchWrapper.getNext();
    assertTrue(tryBlock.isBlock());
    assertEquals(Token.CATCH, catchWrapper.getFirstChild().getType());
    assertTrue(finallyBlock.isBlock());
  }

  // processUnaryExpression: unary minus on a number literal folds into a negative NUMBER
  @Test
  public void testProcessUnaryExpression_negNumber_foldsToNegativeNumber() throws Throwable {
    Node script = parse("-5;");
    Node num = script.getFirstChild().getFirstChild();
    assertEquals(Token.NUMBER, num.getType());
    assertEquals(-5.0, num.getDouble(), 1e-9);
  }

  // processUnaryExpression: delete on a non property/elem/name operand is invalid
  @Test
  public void testProcessUnaryExpression_deleteInvalidOperand_reportsError() throws Throwable {
    parse("delete 5;");
    assertTrue(compiler.getErrorCount() > 0);
  }

  // processUnaryExpression: delete on a property access is valid, no error
  @Test
  public void testProcessUnaryExpression_deleteValidOperand_noError() throws Throwable {
    parse("delete a.b;");
    assertEquals(0, compiler.getErrorCount());
  }

  // processUnaryExpression: postfix increment on an invalid target reports error
  @Test
  public void testProcessUnaryExpression_incInvalidTarget_reportsError() throws Throwable {
    parse("5++;");
    assertTrue(compiler.getErrorCount() > 0);
  }

  // processVariableDeclaration: multiple initializers become siblings of VAR node
  @Test
  public void testProcessVariableDeclaration_multipleDeclarations_hasTwoChildren() throws Throwable {
    Node script = parse("var x = 1, y = 2;");
    Node varNode = script.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    Node first = varNode.getFirstChild();
    Node second = first.getNext();
    assertEquals("x", first.getString());
    assertEquals("y", second.getString());
  }

  // processVariableDeclaration: when const is accepted, the CONST token type must be preserved
  @Test
  public void testProcessVariableDeclaration_constAccepted_preservesConstTokenType() throws Throwable {
    Node script = parseAcceptingConst("const x = 1;");
    assertEquals(0, compiler.getErrorCount());
    Node declNode = script.getFirstChild();
    assertEquals(Token.CONST, declNode.getType());
  }

  // processWhileLoop: while produces WHILE node with condition and block body
  @Test
  public void testProcessWhileLoop_createsWhileNode() throws Throwable {
    Node script = parse("while (a) { b(); }");
    Node whileNode = script.getFirstChild();
    assertEquals(Token.WHILE, whileNode.getType());
    assertTrue(whileNode.getFirstChild().getNext().isBlock());
  }

  // processWithStatement: with statement produces WITH node
  @Test
  public void testProcessWithStatement_createsWithNode() throws Throwable {
    Node script = parse("with (a) { b(); }");
    Node withNode = script.getFirstChild();
    assertEquals(Token.WITH, withNode.getType());
  }

  // getStringValue: whole-number object literal key renders without a decimal point
  @Test
  public void testGetStringValue_integerKey_rendersWithoutDecimalPoint() throws Throwable {
    Node script = parse("var o = {1: 'x'};");
    Node objLit = script.getFirstChild().getFirstChild().getFirstChild();
    Node key = objLit.getFirstChild();
    assertEquals("1", key.getString());
  }

  // setSourceInfo: statements on later source lines report the correct line number
  @Test
  public void testSetSourceInfo_multilineSource_reportsCorrectLineNumber() throws Throwable {
    Node script = parse("var a = 1;\nvar b = 2;");
    Node second = script.getFirstChild().getNext();
    assertEquals(2, second.getLineno());
  }
}
