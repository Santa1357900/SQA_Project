package com.google.javascript.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class NormalizeClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  private Node parseAndNormalize(String code) {
    Node js = compiler.parseTestCode(code);
    NodeTraversal.traverse(
        compiler, js, new Normalize.NormalizeStatements(compiler, false));
    return js;
  }



  // covers parseAndNormalizeTestCode: var splitting applied via static helper
  @Test
  public void testParseAndNormalizeTestCode_multipleDeclarators_splitApplied() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var a, b;", "test$");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertTrue(first.hasOneChild());
    assertNotNull(first.getNext());
  }



  // covers visit(): Token.WHILE case, CONVERT_WHILE_TO_FOR true path
  @Test
  public void testVisit_whileLoop_convertedToForWithEmptyInitAndIncrement() throws Throwable {
    Node js = parseAndNormalize("while(a);");
    Node forNode = js.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    Node init = forNode.getFirstChild();
    Node cond = init.getNext();
    Node incr = cond.getNext();
    assertEquals(Token.EMPTY, init.getType());
    assertEquals(Token.NAME, cond.getType());
    assertEquals("a", cond.getString());
    assertEquals(Token.EMPTY, incr.getType());
  }



  // covers isHoistedFunctionDeclaration true path: top level function not rewritten
  @Test
  public void testVisit_hoistedTopLevelFunction_remainsFunctionDeclaration() throws Throwable {
    Node js = parseAndNormalize("function f(){}");
    Node stmt = js.getFirstChild();
    assertEquals(Token.FUNCTION, stmt.getType());
    assertEquals("f", stmt.getFirstChild().getString());
  }

  // covers unhoisted function declaration: rewritten to var f = function(){}
  @Test
  public void testVisit_unhoistedFunctionInBlock_rewrittenToVarWithFunctionExpr() throws Throwable {
    Node js = parseAndNormalize("if (a) { function f(){} }");
    Node ifNode = js.getFirstChild();
    Node thenBlock = ifNode.getLastChild();
    Node varNode = thenBlock.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    Node nameNode = varNode.getFirstChild();
    assertEquals("f", nameNode.getString());
    Node func = nameNode.getFirstChild();
    assertEquals(Token.FUNCTION, func.getType());
    assertEquals("", func.getFirstChild().getString());
  }

  // covers isFunctionExpression true path: function expression not rewritten
  @Test
  public void testVisit_namedFunctionExpression_notRewrittenKeepsInnerName() throws Throwable {
    Node js = parseAndNormalize("var f = function g(){};");
    Node nameF = js.getFirstChild().getFirstChild();
    Node func = nameF.getFirstChild();
    assertEquals(Token.FUNCTION, func.getType());
    assertEquals("g", func.getFirstChild().getString());
  }



  // covers NAME not constant-by-convention branch: not annotated
  @Test
  public void testVisit_nameNotFollowingConstantConvention_notAnnotated() throws Throwable {
    Node js = parseAndNormalize("var foo = 1;");
    Node nameNode = js.getFirstChild().getFirstChild();
    assertFalse(nameNode.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // covers normalizeLabels default branch: non block/loop body wrapped in BLOCK
  @Test
  public void testShouldTraverse_labelWithNonBlockBody_wrapsBodyInBlock() throws Throwable {
    Node js = parseAndNormalize("foo: a;");
    Node label = js.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    Node body = label.getLastChild();
    assertEquals(Token.BLOCK, body.getType());
  }

  // covers normalizeLabels BLOCK case: body already a block, left unchanged (no double wrap)
  @Test
  public void testShouldTraverse_labelWithBlockBody_remainsUnchanged() throws Throwable {
    Node js = parseAndNormalize("foo: { a; }");
    Node label = js.getFirstChild();
    Node body = label.getLastChild();
    assertEquals(Token.BLOCK, body.getType());
    assertTrue(body.getFirstChild().getType() != Token.BLOCK);
  }

  // covers extractForInitializer: FOR with VAR initializer hoisted before loop
  @Test
  public void testDoStatementNormalizations_forWithVarInit_initMovedBeforeLoop() throws Throwable {
    Node js = parseAndNormalize("for (var i = 0; i < 10; i++) {}");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    Node forNode = first.getNext();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // covers extractForInitializer: FOR with expression initializer hoisted as expr statement
  @Test
  public void testDoStatementNormalizations_forWithExprInit_initMovedAsExpressionStatement() throws Throwable {
    Node js = parseAndNormalize("for (i = 0; i < 10; i++) {}");
    Node first = js.getFirstChild();
    Node forNode = first.getNext();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
    assertEquals(Token.ASSIGN, first.getFirstChild().getType());
  }

  // covers extractForInitializer: FOR with already-EMPTY initializer, no extraction
  @Test
  public void testDoStatementNormalizations_forWithEmptyInit_noExtractionPerformed() throws Throwable {
    Node js = parseAndNormalize("for (; i < 10; i++) {}");
    Node first = js.getFirstChild();
    assertEquals(Token.FOR, first.getType());
    assertNull(first.getNext());
  }

  // covers FOR-IN with VAR: declaration hoisted, FOR left with bare NAME
  @Test
  public void testDoStatementNormalizations_forInWithVarDeclaration_declarationHoistedBeforeLoop() throws Throwable {
    Node js = parseAndNormalize("for (var k in obj) {}");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertEquals("k", first.getFirstChild().getString());
    Node forNode = first.getNext();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.NAME, forNode.getFirstChild().getType());
    assertEquals("k", forNode.getFirstChild().getString());
  }

  // covers FOR-IN without VAR: no extraction performed
  @Test
  public void testDoStatementNormalizations_forInWithoutVarDeclaration_noExtraction() throws Throwable {
    Node js = parseAndNormalize("for (k in obj) {}");
    Node first = js.getFirstChild();
    assertEquals(Token.FOR, first.getType());
    assertNull(first.getNext());
  }

  // covers LABEL recursion branch of extractForInitializer: init hoisted before the LABEL
  @Test
  public void testDoStatementNormalizations_forInsideLabel_initExtractedBeforeLabel() throws Throwable {
    Node js = parseAndNormalize("foo: for (var i = 0; i < 1; i++) {}");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    Node label = first.getNext();
    assertEquals(Token.LABEL, label.getType());
    Node forNode = label.getLastChild();
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // covers splitVarDeclarations while-loop multi-iteration: three declarators split
  @Test
  public void testSplitVarDeclarations_multipleDeclarators_splitIntoSeparateStatements() throws Throwable {
    Node js = parseAndNormalize("var a, b, c;");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertEquals("a", first.getFirstChild().getString());
    assertTrue(first.hasOneChild());
    Node second = first.getNext();
    assertEquals("b", second.getFirstChild().getString());
    Node third = second.getNext();
    assertEquals("c", third.getFirstChild().getString());
    assertNull(third.getNext());
  }



  // covers splitVarDeclarations while-loop zero-iteration: single declarator not split
  @Test
  public void testSplitVarDeclarations_singleDeclarator_remainsSingleStatement() throws Throwable {
    Node js = parseAndNormalize("var a = 1;");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertTrue(first.hasOneChild());
    assertNull(first.getNext());
  }





  // covers moveNamedFunctions: trailing function declaration moved to front of body
  @Test
  public void testMoveNamedFunctions_declarationNotAtTop_movedToFrontOfFunctionBody() throws Throwable {
    Node js = parseAndNormalize("function f() { a(); function g(){} }");
    Node funcF = js.getFirstChild();
    Node body = funcF.getLastChild();
    Node firstInBody = body.getFirstChild();
    assertEquals(Token.FUNCTION, firstInBody.getType());
    assertEquals("g", firstInBody.getFirstChild().getString());
  }

  // covers moveNamedFunctions skip-loop branch: declaration already at top stays in place
  @Test
  public void testMoveNamedFunctions_declarationAlreadyAtTop_remainsAtFront() throws Throwable {
    Node js = parseAndNormalize("function f() { function g(){} a(); }");
    Node funcF = js.getFirstChild();
    Node body = funcF.getLastChild();
    Node firstInBody = body.getFirstChild();
    assertEquals(Token.FUNCTION, firstInBody.getType());
    assertEquals("g", firstInBody.getFirstChild().getString());
  }
}
