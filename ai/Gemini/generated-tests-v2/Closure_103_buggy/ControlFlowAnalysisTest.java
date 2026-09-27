package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ControlFlowAnalysisTest {

  @Test
  public void testConstructorAndProcessBasic() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);
    assertNull(cfa.getCfg());

    Node externs = IR.script();
    Node root = IR.script();
    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testProcessWithoutTraversingFunctions() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false);

    Node externs = IR.script();
    Node root = IR.script();
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block());
    root.addChildToBack(fn);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresIfElse() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node cond = IR.trueNode();
    Node thenBlock = IR.block(IR.exprResult(IR.number(1)));
    Node elseBlock = IR.block(IR.exprResult(IR.number(2)));
    Node ifNode = IR.ifNode(cond, thenBlock, elseBlock);
    Node root = IR.script();
    root.addChildToBack(ifNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresIfNoElse() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node cond = IR.trueNode();
    Node thenBlock = IR.block(IR.exprResult(IR.number(1)));
    Node ifNode = IR.ifNode(cond, thenBlock);
    Node root = IR.script();
    root.addChildToBack(ifNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresWhile() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node cond = IR.trueNode();
    Node body = IR.block(IR.exprResult(IR.number(1)));
    Node whileNode = IR.whileNode(cond, body);
    Node root = IR.script();
    root.addChildToBack(whileNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresDo() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node body = IR.block(IR.exprResult(IR.number(1)));
    Node cond = IR.trueNode();
    Node doNode = IR.doNode(body, cond);
    Node root = IR.script();
    root.addChildToBack(doNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresFor() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node init = IR.exprResult(IR.number(0));
    Node cond = IR.trueNode();
    Node iter = IR.exprResult(IR.number(1));
    Node body = IR.block(IR.exprResult(IR.number(2)));
    Node forNode = IR.forNode(init, cond, iter, body);
    Node root = IR.script();
    root.addChildToBack(forNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testControlStructuresSwitchCaseDefault() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node switchVal = IR.number(1);
    Node caseNode = IR.caseNode(IR.number(1), IR.block(IR.breakNode()));
    Node defaultNode = IR.defaultCase(IR.block(IR.breakNode()));
    Node switchBlock = IR.block(caseNode, defaultNode);
    Node switchNode = IR.switchNode(switchVal, switchBlock);
    Node root = IR.script();
    root.addChildToBack(switchNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testTryCatchFinally() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node tryBody = IR.block(IR.throwNode(IR.string("error")));
    Node catchNode = IR.catchNode(IR.name("e"), IR.block(IR.exprResult(IR.number(1))));
    Node finallyBlock = IR.block(IR.exprResult(IR.number(2)));
    Node tryNode = IR.tryNode(tryBody, IR.block(catchNode), finallyBlock);
    Node root = IR.script();
    root.addChildToBack(tryNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testTryFinallyNoCatch() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node tryBody = IR.block(IR.exprResult(IR.number(1)));
    Node finallyBlock = IR.block(IR.exprResult(IR.number(2)));
    Node tryNode = IR.tryNode(tryBody, IR.block(), finallyBlock);
    Node root = IR.script();
    root.addChildToBack(tryNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testBreakAndContinueWithLabel() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node labelName = IR.labelName("mylabal");
    Node breakNode = IR.breakNode(labelName);
    Node continueNode = IR.continueNode(labelName);
    Node body = IR.block(breakNode, continueNode);
    Node whileNode = IR.whileNode(IR.trueNode(), body);
    Node labelNode = IR.label(labelName, whileNode);
    Node root = IR.script();
    root.addChildToBack(labelNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testReturnStatement() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node returnNode = IR.returnNode(IR.number(42));
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(returnNode));
    Node root = IR.script();
    root.addChildToBack(fn);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testWithStatement() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true);

    Node externs = IR.script();
    Node withNode = IR.with(IR.name("obj"), IR.block(IR.exprResult(IR.number(1))));
    Node root = IR.script();
    root.addChildToBack(withNode);

    cfa.process(externs, root);
    assertNotNull(cfa.getCfg());
  }

  @Test
  public void testStaticMethodsDirect() throws Throwable {
    Node node = IR.forIn(IR.name("x"), IR.name("y"), IR.block());
    assertTrue(ControlFlowAnalysis.isBreakStructure(node, false));
    assertTrue(ControlFlowAnalysis.isContinueStructure(node));

    Node blockNode = IR.block();
    assertTrue(ControlFlowAnalysis.isBreakStructure(blockNode, true));
    assertTrue(!ControlFlowAnalysis.isBreakStructure(blockNode, false));
  }
}