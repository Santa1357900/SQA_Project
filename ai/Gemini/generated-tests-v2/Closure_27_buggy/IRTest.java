package com.google.javascript.rhino;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class IRTest {

  @Test
  public void testEmpty() throws Throwable {
    Node node = IR.empty();
    assertNotNull(node);
    assertEquals(Token.EMPTY, node.getType());
  }

  @Test
  public void testFunction() throws Throwable {
    Node name = IR.name("testFunc");
    Node params = IR.paramList();
    Node body = IR.block();
    Node func = IR.function(name, params, body);
    assertNotNull(func);
    assertEquals(Token.FUNCTION, func.getType());
  }

  @Test
  public void testParamListOverloads() throws Throwable {
    Node p1 = IR.name("a");
    Node p2 = IR.name("b");

    Node list1 = IR.paramList();
    assertNotNull(list1);
    assertEquals(Token.PARAM_LIST, list1.getType());

    Node list2 = IR.paramList(p1);
    assertNotNull(list2);
    assertEquals(Token.PARAM_LIST, list2.getType());

    Node list3 = IR.paramList(p1, p2);
    assertNotNull(list3);
    assertEquals(Token.PARAM_LIST, list3.getType());

    List<Node> paramListCollection = new ArrayList<Node>();
    paramListCollection.add(p1);
    paramListCollection.add(p2);
    Node list4 = IR.paramList(paramListCollection);
    assertNotNull(list4);
    assertEquals(Token.PARAM_LIST, list4.getType());
  }

  @Test
  public void testBlockOverloads() throws Throwable {
    Node stmt = IR.empty();

    Node b1 = IR.block();
    assertNotNull(b1);
    assertEquals(Token.BLOCK, b1.getType());

    Node b2 = IR.block(stmt);
    assertNotNull(b2);
    assertEquals(Token.BLOCK, b2.getType());

    Node b3 = IR.block(stmt, stmt);
    assertNotNull(b3);
    assertEquals(Token.BLOCK, b3.getType());
  }

  @Test
  public void testScript() throws Throwable {
    Node stmt = IR.empty();
    Node script = IR.script(stmt);
    assertNotNull(script);
    assertEquals(Token.SCRIPT, script.getType());
  }

  @Test
  public void testVarOverloads() throws Throwable {
    Node name1 = IR.name("x");
    Node val = IR.number(5.0);
    Node var1 = IR.var(name1, val);
    assertNotNull(var1);
    assertEquals(Token.VAR, var1.getType());

    Node name2 = IR.name("y");
    Node var2 = IR.var(name2);
    assertNotNull(var2);
    assertEquals(Token.VAR, var2.getType());
  }

  @Test
  public void testReturnNodeOverloads() throws Throwable {
    Node r1 = IR.returnNode();
    assertNotNull(r1);
    assertEquals(Token.RETURN, r1.getType());

    Node expr = IR.trueNode();
    Node r2 = IR.returnNode(expr);
    assertNotNull(r2);
    assertEquals(Token.RETURN, r2.getType());
  }

  @Test
  public void testThrowNode() throws Throwable {
    Node expr = IR.string("error");
    Node t = IR.throwNode(expr);
    assertNotNull(t);
    assertEquals(Token.THROW, t.getType());
  }

  @Test
  public void testExprResult() throws Throwable {
    Node expr = IR.trueNode();
    Node res = IR.exprResult(expr);
    assertNotNull(res);
    assertEquals(Token.EXPR_RESULT, res.getType());
  }

  @Test
  public void testIfNodeOverloads() throws Throwable {
    Node cond = IR.trueNode();
    Node thenBlock = IR.block();
    Node elseBlock = IR.block();

    Node if1 = IR.ifNode(cond, thenBlock);
    assertNotNull(if1);
    assertEquals(Token.IF, if1.getType());

    Node if2 = IR.ifNode(cond, thenBlock, elseBlock);
    assertNotNull(if2);
    assertEquals(Token.IF, if2.getType());
  }

  @Test
  public void testDoNode() throws Throwable {
    Node body = IR.block();
    Node cond = IR.trueNode();
    Node d = IR.doNode(body, cond);
    assertNotNull(d);
    assertEquals(Token.DO, d.getType());
  }

  @Test
  public void testForLoops() throws Throwable {
    Node target = IR.name("i");
    Node cond = IR.trueNode();
    Node body = IR.block();
    Node incr = IR.empty();
    Node init = IR.empty();

    Node forIn = IR.forIn(target, cond, body);
    assertNotNull(forIn);
    assertEquals(Token.FOR, forIn.getType());

    Node forNode = IR.forNode(init, cond, incr, body);
    assertNotNull(forNode);
    assertEquals(Token.FOR, forNode.getType());
  }

  @Test
  public void testSwitchAndCases() throws Throwable {
    Node cond = IR.number(1.0);
    Node block = IR.block();
    Node caseNode = IR.caseNode(cond, block);
    Node defaultCase = IR.defaultCase(block);

    Node switchNode = IR.switchNode(cond, caseNode, defaultCase);
    assertNotNull(switchNode);
    assertEquals(Token.SWITCH, switchNode.getType());
    assertNotNull(caseNode);
    assertEquals(Token.CASE, caseNode.getType());
    assertNotNull(defaultCase);
    assertEquals(Token.DEFAULT_CASE, defaultCase.getType());
  }

  @Test
  public void testLabelsAndLabelName() throws Throwable {
    Node name = IR.labelName("lbl");
    Node stmt = IR.empty();
    Node label = IR.label(name, stmt);
    assertNotNull(name);
    assertEquals(Token.LABEL_NAME, name.getType());
    assertNotNull(label);
    assertEquals(Token.LABEL, label.getType());
  }

  @Test
  public void testTryCatchFinallyConstructs() throws Throwable {
    Node tryBodyName = IR.labelName("tryLbl");
    Node finBodyName = IR.labelName("finLbl");
    Node tryBodyBlock = IR.block();
    Node finBodyBlock = IR.block();
    Node catchNode = IR.catchNode(IR.name("e"), IR.block());

    Node tryFin = IR.tryFinally(tryBodyName, finBodyName);
    assertNotNull(tryFin);
    assertEquals(Token.TRY, tryFin.getType());

    Node tryCatch = IR.tryCatch(tryBodyBlock, catchNode);
    assertNotNull(tryCatch);
    assertEquals(Token.TRY, tryCatch.getType());

    Node tryCatchFin = IR.tryCatchFinally(tryBodyBlock, catchNode, finBodyBlock);
    assertNotNull(tryCatchFin);
    assertEquals(Token.TRY, tryCatchFin.getType());
  }

  @Test
  public void testBreakAndContinueOverloads() throws Throwable {
    Node b1 = IR.breakNode();
    assertNotNull(b1);
    assertEquals(Token.BREAK, b1.getType());

    Node name = IR.labelName("loopLbl");
    Node b2 = IR.breakNode(name);
    assertNotNull(b2);
    assertEquals(Token.BREAK, b2.getType());

    Node c1 = IR.continueNode();
    assertNotNull(c1);
    assertEquals(Token.CONTINUE, c1.getType());

    Node c2 = IR.continueNode(name);
    assertNotNull(c2);
    assertEquals(Token.CONTINUE, c2.getType());
  }

  @Test
  public void testCallAndNewNode() throws Throwable {
    Node target = IR.name("func");
    Node arg = IR.trueNode();

    Node call = IR.call(target, arg);
    assertNotNull(call);
    assertEquals(Token.CALL, call.getType());

    Node newNode = IR.newNode(target, arg);
    assertNotNull(newNode);
    assertEquals(Token.NEW, newNode.getType());
  }

  @Test
  public void testNameAndGettersSetters() throws Throwable {
    Node name = IR.name("foo");
    assertNotNull(name);
    assertEquals(Token.NAME, name.getType());

    Node target = IR.name("obj");
    Node prop = IR.string("prop");
    Node getprop = IR.getprop(target, prop);
    assertNotNull(getprop);
    assertEquals(Token.GETPROP, getprop.getType());

    Node elem = IR.string("elem");
    Node getelem = IR.getelem(target, elem);
    assertNotNull(getelem);
    assertEquals(Token.GETELEM, getelem.getType());
  }

  @Test
  public void testAssignAndHook() throws Throwable {
    Node target = IR.name("x");
    Node expr = IR.number(1.0);
    Node assign = IR.assign(target, expr);
    assertNotNull(assign);
    assertEquals(Token.ASSIGN, assign.getType());

    Node cond = IR.trueNode();
    Node trueval = IR.number(1.0);
    Node falseval = IR.number(2.0);
    Node hook = IR.hook(cond, trueval, falseval);
    assertNotNull(hook);
    assertEquals(Token.HOOK, hook.getType());
  }

  @Test
  public void testBinaryAndUnaryOperations() throws Throwable {
    Node e1 = IR.trueNode();
    Node e2 = IR.falseNode();

    assertNotNull(IR.comma(e1, e2));
    assertNotNull(IR.and(e1, e2));
    assertNotNull(IR.or(e1, e2));
    assertNotNull(IR.not(e1));
    assertNotNull(IR.eq(e1, e2));
    assertNotNull(IR.sheq(e1, e2));
    assertNotNull(IR.voidNode(e1));
    assertNotNull(IR.neg(e1));
    assertNotNull(IR.pos(e1));
    assertNotNull(IR.add(e1, e2));
    assertNotNull(IR.sub(e1, e2));
  }

  @Test
  public void testLiteralsAndPrimitives() throws Throwable {
    Node stringKey = IR.stringKey("key");
    Node val = IR.trueNode();
    Node propdef = IR.propdef(stringKey, val);
    assertNotNull(propdef);

    Node objLit = IR.objectlit(propdef);
    assertNotNull(objLit);
    assertEquals(Token.OBJECTLIT, objLit.getType());

    Node arrLit = IR.arraylit(IR.number(1.0));
    assertNotNull(arrLit);
    assertEquals(Token.ARRAYLIT, arrLit.getType());

    Node rx1 = IR.regexp(IR.string("abc"));
    assertNotNull(rx1);
    assertEquals(Token.REGEXP, rx1.getType());

    Node rx2 = IR.regexp(IR.string("abc"), IR.string("g"));
    assertNotNull(rx2);
    assertEquals(Token.REGEXP, rx2.getType());

    assertNotNull(IR.string("test"));
    assertNotNull(IR.stringKey("testKey"));
    assertNotNull(IR.number(42.0));
    assertNotNull(IR.thisNode());
    assertNotNull(IR.trueNode());
    assertNotNull(IR.falseNode());
    assertNotNull(IR.nullNode());
  }
}