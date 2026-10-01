package com.google.javascript.rhino;

import org.junit.Test;
import static org.junit.Assert.*;

public class IRClaudeTest {

  // Tests IR.empty() returns a Node with Token.EMPTY type
  @Test
  public void testEmpty_returnsEmptyNode() throws Throwable {
    Node result = IR.empty();
    assertEquals(Token.EMPTY, result.getType());
  }

  // Tests IR.function() success path: valid name/params/body
  @Test
  public void testFunction_validArgs_createsFunctionNode() throws Throwable {
    Node name = IR.name("foo");
    Node params = IR.paramList();
    Node body = IR.block();
    Node result = IR.function(name, params, body);
    assertEquals(Token.FUNCTION, result.getType());
  }

  // Tests IR.function() throws when name is not a NAME node
  @Test
  public void testFunction_invalidName_throwsException() throws Throwable {
    Node name = IR.string("foo");
    Node params = IR.paramList();
    Node body = IR.block();
    try {
      IR.function(name, params, body);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.function() throws when body is not a BLOCK node
  @Test
  public void testFunction_invalidBody_throwsException() throws Throwable {
    Node name = IR.name("foo");
    Node params = IR.paramList();
    Node body = IR.name("notABlock");
    try {
      IR.function(name, params, body);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.paramList() with no args returns empty PARAM_LIST node
  @Test
  public void testParamList_noArgs_returnsEmptyParamList() throws Throwable {
    Node result = IR.paramList();
    assertEquals(Token.PARAM_LIST, result.getType());
    assertFalse(result.hasChildren());
  }

  // Tests IR.paramList(Node) adds the single valid param as child
  @Test
  public void testParamList_singleValidParam_addsChild() throws Throwable {
    Node param = IR.name("a");
    Node result = IR.paramList(param);
    assertEquals(Token.PARAM_LIST, result.getType());
    assertTrue(result.hasOneChild());
  }

  // Tests IR.paramList(Node) throws when param is not a NAME node
  @Test
  public void testParamList_invalidParam_throwsException() throws Throwable {
    Node param = IR.string("a");
    try {
      IR.paramList(param);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.paramList(Node...) loop with multiple valid params
  @Test
  public void testParamList_varargsMultipleParams_addsAllChildren() throws Throwable {
    Node a = IR.name("a");
    Node b = IR.name("b");
    Node result = IR.paramList(a, b);
    assertEquals(Token.PARAM_LIST, result.getType());
    assertFalse(result.hasOneChild());
  }

  // Tests IR.block() with no args returns empty BLOCK node
  @Test
  public void testBlock_noArgs_returnsBlockNode() throws Throwable {
    Node result = IR.block();
    assertEquals(Token.BLOCK, result.getType());
    assertFalse(result.hasChildren());
  }

  // Tests IR.block(Node) with a valid statement adds it as child
  @Test
  public void testBlock_validStmt_addsChild() throws Throwable {
    Node stmt = IR.exprResult(IR.name("x"));
    Node result = IR.block(stmt);
    assertEquals(Token.BLOCK, result.getType());
    assertTrue(result.hasOneChild());
  }

  // Tests IR.block(Node) throws when stmt does not satisfy mayBeStatement
  @Test
  public void testBlock_invalidStmt_throwsException() throws Throwable {
    Node stmt = IR.name("x");
    try {
      IR.block(stmt);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.script(Node...) adds multiple valid statements to SCRIPT node
  @Test
  public void testScript_validStmts_createsScriptNode() throws Throwable {
    Node s1 = IR.exprResult(IR.name("x"));
    Node s2 = IR.exprResult(IR.number(1));
    Node result = IR.script(s1, s2);
    assertEquals(Token.SCRIPT, result.getType());
    assertFalse(result.hasOneChild());
  }

  // Tests IR.var(name, value) adds value as child of name node
  @Test
  public void testVar_nameAndValue_addsValueAsChild() throws Throwable {
    Node name = IR.name("x");
    Node value = IR.number(5);
    Node result = IR.var(name, value);
    assertEquals(Token.VAR, result.getType());
    assertTrue(name.hasChildren());
  }

  // Tests IR.var(name, value) throws when name already has a child
  @Test
  public void testVar_nameAlreadyHasChild_throwsException() throws Throwable {
    Node name = IR.name("x");
    IR.var(name, IR.number(1));
    try {
      IR.var(name, IR.number(2));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.var(name) single-arg overload creates VAR node
  @Test
  public void testVar_nameOnly_createsVarNode() throws Throwable {
    Node name = IR.name("x");
    Node result = IR.var(name);
    assertEquals(Token.VAR, result.getType());
  }

  // Tests IR.returnNode(expr) throws when expr is not an expression (BLOCK)
  @Test
  public void testReturnNode_invalidExpr_throwsException() throws Throwable {
    Node expr = IR.block();
    try {
      IR.returnNode(expr);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.throwNode(expr) creates THROW node with valid expression
  @Test
  public void testThrowNode_validExpr_createsThrowNode() throws Throwable {
    Node result = IR.throwNode(IR.number(1));
    assertEquals(Token.THROW, result.getType());
  }

  // Tests IR.exprResult(expr) creates EXPR_RESULT node
  @Test
  public void testExprResult_validExpr_createsExprResultNode() throws Throwable {
    Node result = IR.exprResult(IR.name("x"));
    assertEquals(Token.EXPR_RESULT, result.getType());
  }

  // Tests IR.ifNode(cond, then) throws when then is not a BLOCK
  @Test
  public void testIfNode_invalidThen_throwsException() throws Throwable {
    Node cond = IR.trueNode();
    Node then = IR.name("x");
    try {
      IR.ifNode(cond, then);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.ifNode(cond, then, elseNode) 3-arg overload creates IF node
  @Test
  public void testIfNode_threeArgs_createsIfElseNode() throws Throwable {
    Node cond = IR.trueNode();
    Node then = IR.block();
    Node elseNode = IR.block();
    Node result = IR.ifNode(cond, then, elseNode);
    assertEquals(Token.IF, result.getType());
  }

  // Tests IR.doNode(body, cond) creates DO node with valid args
  @Test
  public void testDoNode_validArgs_createsDoNode() throws Throwable {
    Node body = IR.block();
    Node cond = IR.trueNode();
    Node result = IR.doNode(body, cond);
    assertEquals(Token.DO, result.getType());
  }

  // Tests IR.forIn(target, cond, body) with expression target creates FOR node
  @Test
  public void testForIn_validArgs_createsForNode() throws Throwable {
    Node target = IR.name("x");
    Node cond = IR.name("y");
    Node body = IR.block();
    Node result = IR.forIn(target, cond, body);
    assertEquals(Token.FOR, result.getType());
  }

  // Tests IR.forNode with empty init/cond/incr (mayBeExpressionOrEmpty branch)
  @Test
  public void testForNode_emptyInitCondIncr_createsForNode() throws Throwable {
    Node init = IR.empty();
    Node cond = IR.empty();
    Node incr = IR.empty();
    Node body = IR.block();
    Node result = IR.forNode(init, cond, incr, body);
    assertEquals(Token.FOR, result.getType());
  }

  // Tests IR.switchNode throws when a case node is neither CASE nor DEFAULT_CASE
  @Test
  public void testSwitchNode_invalidCase_throwsException() throws Throwable {
    Node cond = IR.name("x");
    Node invalidCase = IR.block();
    try {
      IR.switchNode(cond, invalidCase);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.caseNode throws when body is not a BLOCK
  @Test
  public void testCaseNode_invalidBody_throwsException() throws Throwable {
    Node expr = IR.number(1);
    Node body = IR.name("x");
    try {
      IR.caseNode(expr, body);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.defaultCase creates DEFAULT_CASE node with valid block body
  @Test
  public void testDefaultCase_validBody_createsDefaultCaseNode() throws Throwable {
    Node body = IR.block();
    Node result = IR.defaultCase(body);
    assertEquals(Token.DEFAULT_CASE, result.getType());
  }

  // Tests IR.label throws when name is not a LABEL_NAME node
  @Test
  public void testLabel_invalidName_throwsException() throws Throwable {
    Node name = IR.name("x");
    Node stmt = IR.block();
    try {
      IR.label(name, stmt);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.labelName("") throws since name must not be empty
  @Test
  public void testLabelName_emptyString_throwsException() throws Throwable {
    try {
      IR.labelName("");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.labelName with non-empty string creates LABEL_NAME node
  @Test
  public void testLabelName_nonEmptyString_createsLabelNameNode() throws Throwable {
    Node result = IR.labelName("foo");
    assertEquals(Token.LABEL_NAME, result.getType());
  }

  // BUG TEST: tryFinally's contract (consistent with tryCatch/tryCatchFinally)
  // requires block bodies, not label-name nodes; fails on buggy version.
  @Test
  public void testTryFinally_blockBodies_createsTryNode() throws Throwable {
    Node tryBody = IR.block();
    Node finallyBody = IR.block();
    Node result = IR.tryFinally(tryBody, finallyBody);
    assertEquals(Token.TRY, result.getType());
  }

  // Tests IR.tryFinally throws when tryBody is neither a block nor label name
  @Test
  public void testTryFinally_nonBlockTryBody_throwsException() throws Throwable {
    Node tryBody = IR.name("x");
    Node finallyBody = IR.block();
    try {
      IR.tryFinally(tryBody, finallyBody);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.tryCatch creates TRY node with valid block and catch node
  @Test
  public void testTryCatch_validArgs_createsTryNode() throws Throwable {
    Node tryBody = IR.block();
    Node catchNode = IR.catchNode(IR.name("e"), IR.block());
    Node result = IR.tryCatch(tryBody, catchNode);
    assertEquals(Token.TRY, result.getType());
  }

  // Tests IR.tryCatchFinally creates TRY node with 3 children total
  @Test
  public void testTryCatchFinally_validArgs_createsTryNodeWithThreeChildren() throws Throwable {
    Node tryBody = IR.block();
    Node catchNode = IR.catchNode(IR.name("e"), IR.block());
    Node finallyBody = IR.block();
    Node result = IR.tryCatchFinally(tryBody, catchNode, finallyBody);
    assertEquals(Token.TRY, result.getType());
    assertFalse(result.hasOneChild());
  }

  // Tests IR.catchNode throws when expr is not a NAME node
  @Test
  public void testCatchNode_invalidExpr_throwsException() throws Throwable {
    Node expr = IR.number(1);
    Node body = IR.block();
    try {
      IR.catchNode(expr, body);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.breakNode(name) creates BREAK node with label child
  @Test
  public void testBreakNode_withLabel_createsBreakNodeWithChild() throws Throwable {
    Node name = IR.labelName("L");
    Node result = IR.breakNode(name);
    assertEquals(Token.BREAK, result.getType());
    assertTrue(result.hasOneChild());
  }

  // Tests IR.call(target, args) creates CALL node with target and args as children
  @Test
  public void testCall_withArgs_createsCallNode() throws Throwable {
    Node target = IR.name("f");
    Node arg = IR.number(1);
    Node result = IR.call(target, arg);
    assertEquals(Token.CALL, result.getType());
    assertTrue(result.hasChildren());
  }

  // Tests IR.getprop throws when prop is not a STRING node
  @Test
  public void testGetprop_invalidProp_throwsException() throws Throwable {
    Node target = IR.name("x");
    Node prop = IR.name("y");
    try {
      IR.getprop(target, prop);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.assign throws when target is not name/getprop/getelem
  @Test
  public void testAssign_invalidTarget_throwsException() throws Throwable {
    Node target = IR.number(1);
    Node expr = IR.number(2);
    try {
      IR.assign(target, expr);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.hook creates HOOK node with valid cond/trueval/falseval
  @Test
  public void testHook_validArgs_createsHookNode() throws Throwable {
    Node cond = IR.trueNode();
    Node trueval = IR.number(1);
    Node falseval = IR.number(2);
    Node result = IR.hook(cond, trueval, falseval);
    assertEquals(Token.HOOK, result.getType());
  }

  // Tests IR.objectlit throws when propdef is not stringKey/getterDef/setterDef
  @Test
  public void testObjectlit_invalidPropdef_throwsException() throws Throwable {
    Node propdef = IR.name("x");
    try {
      IR.objectlit(propdef);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Tests IR.arraylit allows EMPTY elements via mayBeExpressionOrEmpty
  @Test
  public void testArraylit_withEmptySlot_allowsEmptyElement() throws Throwable {
    Node empty = IR.empty();
    Node num = IR.number(1);
    Node result = IR.arraylit(empty, num);
    assertEquals(Token.ARRAYLIT, result.getType());
    assertFalse(result.hasOneChild());
  }

  // Tests IR.regexp(expr, flags) creates REGEXP node with two children
  @Test
  public void testRegexp_withFlags_createsRegexpNodeWithTwoChildren() throws Throwable {
    Node expr = IR.string("abc");
    Node flags = IR.string("g");
    Node result = IR.regexp(expr, flags);
    assertEquals(Token.REGEXP, result.getType());
    assertFalse(result.hasOneChild());
  }
}
