package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ControlFlowAnalysisClaudeTest {

  private Node lastMainRoot;

  private ControlFlowGraph<Node> createCfg(String js, boolean traverseFunctions)
      throws Throwable {
    Compiler localCompiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    localCompiler.init(externs, inputs, options);
    Node root = localCompiler.parseInputs();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    lastMainRoot = mainRoot;
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(localCompiler, traverseFunctions);
    cfa.process(externsRoot, mainRoot);
    return cfa.getCfg();
  }

  private ControlFlowGraph<Node> createCfg(String js) throws Throwable {
    return createCfg(js, true);
  }

  private Node parseOnly(String js) throws Throwable {
    Compiler localCompiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    localCompiler.init(externs, inputs, options);
    Node root = localCompiler.parseInputs();
    return root.getLastChild();
  }

  private static Node findNode(Node n, int type) {
    if (n.getType() == type) {
      return n;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node result = findNode(c, type);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static Node findCall(Node n, String name) {
    if (n.getType() == Token.CALL) {
      Node callee = n.getFirstChild();
      if (callee != null && callee.getType() == Token.NAME
          && name.equals(callee.getString())) {
        return n;
      }
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node result = findCall(c, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static Node findCallStmt(Node root, String name) {
    Node call = findCall(root, name);
    return call == null ? null : call.getParent();
  }

  private static DiGraphNode<Node, Branch> findCfgNode(
      ControlFlowGraph<Node> cfg, Node value) {
    for (DiGraphNode<Node, Branch> n : cfg.getDirectedGraphNodes()) {
      if (n.getValue() == value) {
        return n;
      }
    }
    return null;
  }

  private static List<Node> successorsOf(ControlFlowGraph<Node> cfg, Node value) {
    DiGraphNode<Node, Branch> dNode = findCfgNode(cfg, value);
    assertNotNull("Node not found in CFG", dNode);
    List<DiGraphNode<Node, Branch>> succ = cfg.getDirectedSuccNodes(dNode);
    List<Node> result = new ArrayList<Node>();
    for (DiGraphNode<Node, Branch> s : succ) {
      result.add(s.getValue());
    }
    return result;
  }

  // getCfg() before process() must be null (field not yet initialized)
  @Test
  public void testGetCfg_beforeProcess_returnsNull() throws Throwable {
    Compiler localCompiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(localCompiler, true);
    assertNull(cfa.getCfg());
  }

  // process(): entry node value equals computeFallThrough(root) == root for a plain BLOCK/SCRIPT root
  @Test
  public void testProcess_entryValueMatchesPassedRoot() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("a();");
    assertSame(lastMainRoot, cfg.getEntry().getValue());
  }

  // shouldTraverse FUNCTION case: shouldTraverseFunctions==true -> function node is included
  @Test
  public void testProcess_functionsTraversed_whenFlagTrue() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ a(); } b();", true);
    Node functionNode = findNode(lastMainRoot, Token.FUNCTION);
    assertNotNull(findCfgNode(cfg, functionNode));
  }

  // shouldTraverse FUNCTION case: shouldTraverseFunctions==false and not entry -> function excluded
  @Test
  public void testProcess_functionsNotTraversed_whenFlagFalse() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ a(); } b();", false);
    Node functionNode = findNode(lastMainRoot, Token.FUNCTION);
    assertNull(findCfgNode(cfg, functionNode));
  }

  // handleIf: both ON_TRUE (thenBlock) and ON_FALSE (elseBlock) edges present
  @Test
  public void testHandleIf_withElse_bothBranchesPresent() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("if (x) { a(); } else { b(); }");
    Node ifNode = findNode(lastMainRoot, Token.IF);
    Node thenBlock = ifNode.getFirstChild().getNext();
    Node elseBlock = thenBlock.getNext();
    List<Node> succ = successorsOf(cfg, ifNode);
    assertTrue(succ.contains(thenBlock));
    assertTrue(succ.contains(elseBlock));
  }

  // handleIf: elseBlock == null -> ON_FALSE goes to follow() of the IF statement
  @Test
  public void testHandleIf_noElse_falseBranchGoesToFollow() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("if (x) { a(); } b();");
    Node ifNode = findNode(lastMainRoot, Token.IF);
    Node thenBlock = ifNode.getFirstChild().getNext();
    Node bStmt = findCallStmt(lastMainRoot, "b");
    List<Node> succ = successorsOf(cfg, ifNode);
    assertTrue(succ.contains(thenBlock));
    assertTrue(succ.contains(bStmt));
  }

  // handleWhile: ON_TRUE -> body, ON_FALSE -> follow of the while loop
  @Test
  public void testHandleWhile_trueAndFalseBranches() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("while (x) { a(); } b();");
    Node whileNode = findNode(lastMainRoot, Token.WHILE);
    Node body = whileNode.getFirstChild().getNext();
    Node bStmt = findCallStmt(lastMainRoot, "b");
    List<Node> succ = successorsOf(cfg, whileNode);
    assertTrue(succ.contains(body));
    assertTrue(succ.contains(bStmt));
  }

  // handleDo: ON_TRUE -> fallthrough(body), ON_FALSE -> follow of do-while loop
  @Test
  public void testHandleDo_trueAndFalseBranches() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("do { a(); } while (x); b();");
    Node doNode = findNode(lastMainRoot, Token.DO);
    Node body = doNode.getFirstChild();
    Node bStmt = findCallStmt(lastMainRoot, "b");
    List<Node> succ = successorsOf(cfg, doNode);
    assertTrue(succ.contains(body));
    assertTrue(succ.contains(bStmt));
  }

  // handleFor (C-style, 4 children): init->for, for ON_TRUE->body ON_FALSE->follow, iter->for
  @Test
  public void testHandleFor_cStyle_initCondIterBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("for (var i = 0; i < 10; i++) { a(); } b();");
    Node forNode = findNode(lastMainRoot, Token.FOR);
    Node init = forNode.getFirstChild();
    Node cond = init.getNext();
    Node iter = cond.getNext();
    Node body = iter.getNext();
    Node bStmt = findCallStmt(lastMainRoot, "b");
    assertTrue(successorsOf(cfg, init).contains(forNode));
    List<Node> forSucc = successorsOf(cfg, forNode);
    assertTrue(forSucc.contains(body));
    assertTrue(forSucc.contains(bStmt));
    assertTrue(successorsOf(cfg, iter).contains(forNode));
  }

  // handleFor (for-in, 3 children): ON_TRUE->body, ON_FALSE->follow
  @Test
  public void testHandleForIn_bodyAndFollow() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("for (var k in obj) { a(); } b();");
    Node forNode = findNode(lastMainRoot, Token.FOR);
    Node item = forNode.getFirstChild();
    Node collection = item.getNext();
    Node body = collection.getNext();
    Node bStmt = findCallStmt(lastMainRoot, "b");
    List<Node> succ = successorsOf(cfg, forNode);
    assertTrue(succ.contains(body));
    assertTrue(succ.contains(bStmt));
  }

  // handleSwitch: transfers to the first CASE found after the switch expression
  @Test
  public void testHandleSwitch_firstCaseTarget() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg(
        "switch (x) { case 1: a(); break; case 2: b(); break; }");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node firstCase = switchNode.getFirstChild().getNext();
    assertEquals(Token.CASE, firstCase.getType());
    assertTrue(successorsOf(cfg, switchNode).contains(firstCase));
  }

  // handleSwitch: no CASE present, but a DEFAULT exists -> transfer to DEFAULT
  @Test
  public void testHandleSwitch_onlyDefault_targetsDefault() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("switch (x) { default: a(); }");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node deflt = switchNode.getFirstChild().getNext();
    assertEquals(Token.DEFAULT, deflt.getType());
    assertTrue(successorsOf(cfg, switchNode).contains(deflt));
  }

  // handleSwitch: no CASE, no DEFAULT -> transfer to follow of the switch
  @Test
  public void testHandleSwitch_empty_targetsFollow() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("switch (x) {} b();");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node bStmt = findCallStmt(lastMainRoot, "b");
    assertTrue(successorsOf(cfg, switchNode).contains(bStmt));
  }

  // handleCase: ON_TRUE -> own body, ON_FALSE -> next CASE sibling
  @Test
  public void testHandleCase_fallsThroughToNextCase() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("switch (x) { case 1: a(); case 2: b(); }");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node case1 = switchNode.getFirstChild().getNext();
    Node case2 = case1.getNext();
    Node case1Body = case1.getFirstChild().getNext();
    List<Node> succ = successorsOf(cfg, case1);
    assertTrue(succ.contains(case1Body));
    assertTrue(succ.contains(case2));
  }

  // handleCase: no next CASE, DEFAULT located after current case -> ON_FALSE -> DEFAULT
  @Test
  public void testHandleCase_noNextCase_fallsToDefaultAfterIt() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg(
        "switch (x) { case 1: a(); case 2: b(); default: c(); }");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node case1 = switchNode.getFirstChild().getNext();
    Node case2 = case1.getNext();
    Node deflt = case2.getNext();
    assertTrue(successorsOf(cfg, case2).contains(deflt));
  }

  // handleCase bug: DEFAULT positioned BEFORE the current (last) case must NOT be a fallthrough
  // target; lexical fallthrough of the last case must go to the switch's follow node.
  @Test
  public void testHandleCase_defaultBeforeCurrentCase_shouldNotFallBackward() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg(
        "switch (x) { default: d(); case 1: a(); case 2: b(); } e();");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node deflt = switchNode.getFirstChild().getNext();
    Node case1 = deflt.getNext();
    Node case2 = case1.getNext();
    Node eStmt = findCallStmt(lastMainRoot, "e");
    List<Node> succ = successorsOf(cfg, case2);
    assertTrue(succ.contains(eStmt));
    assertFalse(succ.contains(deflt));
  }

  // handleCase: no next CASE, no DEFAULT anywhere -> ON_FALSE -> follow of the switch
  @Test
  public void testHandleCase_noDefaultAnywhere_fallsToSwitchFollow() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("switch (x) { case 1: a(); case 2: b(); } e();");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node case1 = switchNode.getFirstChild().getNext();
    Node case2 = case1.getNext();
    Node eStmt = findCallStmt(lastMainRoot, "e");
    assertTrue(successorsOf(cfg, case2).contains(eStmt));
  }

  // handleDefault: UNCOND edge directly into its own body, not into next sibling
  @Test
  public void testHandleDefault_jumpsToOwnBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("switch (x) { case 1: a(); default: d(); }");
    Node switchNode = findNode(lastMainRoot, Token.SWITCH);
    Node case1 = switchNode.getFirstChild().getNext();
    Node deflt = case1.getNext();
    Node body = deflt.getFirstChild();
    List<Node> succ = successorsOf(cfg, deflt);
    assertEquals(1, succ.size());
    assertTrue(succ.contains(body));
  }

  // handleWith: UNCOND edge directly to the last child (body)
  @Test
  public void testHandleWith_jumpsToBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("with (x) { a(); } b();");
    Node withNode = findNode(lastMainRoot, Token.WITH);
    Node body = withNode.getLastChild();
    assertTrue(successorsOf(cfg, withNode).contains(body));
  }

  // handleStmtList: leading FUNCTION declarations are skipped, control goes to first real stmt
  @Test
  public void testHandleStmtList_skipsLeadingFunctionDeclarations() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){} a();");
    Node scriptNode = findNode(lastMainRoot, Token.SCRIPT);
    Node aStmt = findCallStmt(lastMainRoot, "a");
    assertTrue(successorsOf(cfg, scriptNode).contains(aStmt));
  }

  // handleStmtList: empty BLOCK inside FUNCTION connects to the implicit (null) return
  @Test
  public void testHandleStmtList_emptyBlockInFunction_connectsToImplicitReturn()
      throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){}");
    Node functionNode = findNode(lastMainRoot, Token.FUNCTION);
    Node body = functionNode.getFirstChild().getNext().getNext();
    List<Node> succ = successorsOf(cfg, body);
    assertEquals(1, succ.size());
    assertTrue(succ.contains(null));
  }

  // handleFunction: UNCOND edge from FUNCTION node to the fallthrough of its body
  @Test
  public void testHandleFunction_connectsToBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ a(); }");
    Node functionNode = findNode(lastMainRoot, Token.FUNCTION);
    Node body = functionNode.getFirstChild().getNext().getNext();
    assertTrue(successorsOf(cfg, functionNode).contains(body));
  }

  // handleExpr: no enclosing exception handler -> only the UNCOND follow edge exists
  @Test
  public void testHandleExpr_noHandler_onlyUnconditionalEdge() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("a();");
    Node aStmt = findCallStmt(lastMainRoot, "a");
    List<Node> succ = successorsOf(cfg, aStmt);
    assertEquals(1, succ.size());
    assertTrue(succ.contains(null));
  }

  // handleExpr + connectToPossibleExceptionHandler: CALL may throw -> ON_EX edge to catch block
  @Test
  public void testHandleExpr_withTryCatch_addsExceptionEdge() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("try { a(); } catch (e) { b(); }");
    Node tryNode = findNode(lastMainRoot, Token.TRY);
    Node aStmt = findCallStmt(lastMainRoot, "a");
    Node catchBlock = NodeUtil.getCatchBlock(tryNode);
    assertTrue(successorsOf(cfg, aStmt).contains(catchBlock));
  }

  // handleThrow: connects to the enclosing CATCH block via connectToPossibleExceptionHandler
  @Test
  public void testHandleThrow_connectsToCatchBlock() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("try { throw x; } catch (e) { b(); }");
    Node tryNode = findNode(lastMainRoot, Token.TRY);
    Node throwNode = findNode(lastMainRoot, Token.THROW);
    Node catchBlock = NodeUtil.getCatchBlock(tryNode);
    assertTrue(successorsOf(cfg, throwNode).contains(catchBlock));
  }

  // handleTry: UNCOND edge from TRY node to its first child (try body)
  @Test
  public void testHandleTry_connectsToBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("try { a(); } catch (e) { b(); }");
    Node tryNode = findNode(lastMainRoot, Token.TRY);
    Node tryBody = tryNode.getFirstChild();
    assertTrue(successorsOf(cfg, tryNode).contains(tryBody));
  }

  // handleCatch: UNCOND edge from CATCH node to its last child (catch body)
  @Test
  public void testHandleCatch_connectsToBody() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("try { a(); } catch (e) { b(); }");
    Node catchNode = findNode(lastMainRoot, Token.CATCH);
    Node catchBody = catchNode.getLastChild();
    assertTrue(successorsOf(cfg, catchNode).contains(catchBody));
  }

  // handleBreak: unlabeled break inside WHILE jumps to the follow of the loop
  @Test
  public void testHandleBreak_unlabeledWhile_targetsLoopFollow() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("while (x) { break; } done();");
    Node breakNode = findNode(lastMainRoot, Token.BREAK);
    Node doneStmt = findCallStmt(lastMainRoot, "done");
    assertTrue(successorsOf(cfg, breakNode).contains(doneStmt));
  }

  // handleContinue: unlabeled continue inside WHILE jumps back to the WHILE node itself
  @Test
  public void testHandleContinue_unlabeledWhile_targetsLoopItself() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("while (x) { continue; } done();");
    Node whileNode = findNode(lastMainRoot, Token.WHILE);
    Node continueNode = findNode(lastMainRoot, Token.CONTINUE);
    assertTrue(successorsOf(cfg, continueNode).contains(whileNode));
  }

  // handleContinue: continue inside C-style FOR jumps to the iterator expression
  @Test
  public void testHandleContinue_forLoop_targetsIterExpression() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("for (var i = 0; i < 10; i++) { continue; } done();");
    Node forNode = findNode(lastMainRoot, Token.FOR);
    Node iter = forNode.getFirstChild().getNext().getNext();
    Node continueNode = findNode(lastMainRoot, Token.CONTINUE);
    assertTrue(successorsOf(cfg, continueNode).contains(iter));
  }

  // handleReturn: no value, no enclosing finally -> UNCOND edge to the implicit (null) return
  @Test
  public void testHandleReturn_noValue_noFinally_targetsImplicitReturn() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ return; }");
    Node returnNode = findNode(lastMainRoot, Token.RETURN);
    List<Node> succ = successorsOf(cfg, returnNode);
    assertEquals(1, succ.size());
    assertTrue(succ.contains(null));
  }

  // handleReturn: inside TRY with FINALLY -> UNCOND edge goes into the finally block
  @Test
  public void testHandleReturn_withFinally_targetsFinallyBlock() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ try { return; } finally { fin(); } }");
    Node tryNode = findNode(lastMainRoot, Token.TRY);
    Node finallyBlock = tryNode.getLastChild();
    Node returnNode = findNode(lastMainRoot, Token.RETURN);
    assertTrue(successorsOf(cfg, returnNode).contains(finallyBlock));
  }

  // handleReturn: return value may throw, but nearest handler is the enclosing FUNCTION itself,
  // so no ON_EX edge is added (exception propagates out of the function).
  @Test
  public void testHandleReturn_valueMayThrow_functionOnly_noExceptionEdge() throws Throwable {
    ControlFlowGraph<Node> cfg = createCfg("function f(){ return a(); }");
    Node returnNode = findNode(lastMainRoot, Token.RETURN);
    List<Node> succ = successorsOf(cfg, returnNode);
    assertEquals(1, succ.size());
    assertTrue(succ.contains(null));
  }

  // isBreakStructure: FOR/DO/WHILE/SWITCH are always break targets regardless of labeled flag
  @Test
  public void testIsBreakStructure_loopsAndSwitch_alwaysTrue() throws Throwable {
    Node forNode = findNode(parseOnly("for(;;){}"), Token.FOR);
    Node whileNode = findNode(parseOnly("while(x){}"), Token.WHILE);
    Node switchNode = findNode(parseOnly("switch(x){}"), Token.SWITCH);
    assertTrue(ControlFlowAnalysis.isBreakStructure(forNode, false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(whileNode, true));
    assertTrue(ControlFlowAnalysis.isBreakStructure(switchNode, false));
  }

  // isBreakStructure: BLOCK/IF/TRY are break targets only when the break is labeled
  @Test
  public void testIsBreakStructure_blockIfTry_dependsOnLabeled() throws Throwable {
    Node blockNode = findNode(parseOnly("{ a(); }"), Token.BLOCK);
    Node ifNode = findNode(parseOnly("if(x){a();}"), Token.IF);
    assertFalse(ControlFlowAnalysis.isBreakStructure(blockNode, false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(blockNode, true));
    assertFalse(ControlFlowAnalysis.isBreakStructure(ifNode, false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(ifNode, true));
  }

  // isBreakStructure: default case returns false for ordinary nodes regardless of labeled flag
  @Test
  public void testIsBreakStructure_default_false() throws Throwable {
    Node callNode = findCall(parseOnly("a();"), "a");
    assertFalse(ControlFlowAnalysis.isBreakStructure(callNode, false));
    assertFalse(ControlFlowAnalysis.isBreakStructure(callNode, true));
  }

  // isContinueStructure: FOR/DO/WHILE are continue targets, other nodes are not
  @Test
  public void testIsContinueStructure_loopsTrueOthersFalse() throws Throwable {
    Node forNode = findNode(parseOnly("for(;;){}"), Token.FOR);
    Node blockNode = findNode(parseOnly("{ a(); }"), Token.BLOCK);
    assertTrue(ControlFlowAnalysis.isContinueStructure(forNode));
    assertFalse(ControlFlowAnalysis.isContinueStructure(blockNode));
  }
}
