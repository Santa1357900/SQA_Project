package com.google.javascript.jscomp;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MaybeReachingVariableUseTest extends TestCase {

  public void testReachingUsesCopyAndEquals() throws Throwable {
    MaybeReachingVariableUse.ReachingUses uses1 = new MaybeReachingVariableUse.ReachingUses();
    MaybeReachingVariableUse.ReachingUses uses2 = new MaybeReachingVariableUse.ReachingUses(uses1);

    assertTrue(uses1.equals(uses2));
    assertEquals(uses1.hashCode(), uses2.hashCode());

    assertFalse(uses1.equals(null));
    assertFalse(uses1.equals(new Object()));
  }

  public void testReachingUsesJoinOp() throws Throwable {
    MaybeReachingVariableUse.ReachingUses uses1 = new MaybeReachingVariableUse.ReachingUses();
    MaybeReachingVariableUse.ReachingUses uses2 = new MaybeReachingVariableUse.ReachingUses();

    List<MaybeReachingVariableUse.ReachingUses> list = new ArrayList<MaybeReachingVariableUse.ReachingUses>();
    list.add(uses1);
    list.add(uses2);

    MaybeReachingVariableUse.ReachingUsesJoinOp joinOp = new MaybeReachingVariableUse.ReachingUsesJoinOp();
    MaybeReachingVariableUse.ReachingUses result = joinOp.apply(list);
    assertNotNull(result);
  }

  public void testIsForward() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);
    assertFalse(analysis.isForward());
  }

  public void testCreateEntryAndInitialEstimateLattice() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);
    
    MaybeReachingVariableUse.ReachingUses entry = analysis.createEntryLattice();
    assertNotNull(entry);
    
    MaybeReachingVariableUse.ReachingUses initial = analysis.createInitialEstimateLattice();
    assertNotNull(initial);
  }

  public void testFlowThroughBasicNodes() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);

    Node blockNode = new Node(Token.BLOCK);
    MaybeReachingVariableUse.ReachingUses input = new MaybeReachingVariableUse.ReachingUses();
    MaybeReachingVariableUse.ReachingUses output = analysis.flowThrough(blockNode, input);
    assertNotNull(output);

    Node nameNode = Node.newString(Token.NAME, "x");
    MaybeReachingVariableUse.ReachingUses outputName = analysis.flowThrough(nameNode, input);
    assertNotNull(outputName);
  }

  public void testFlowThroughControlStructures() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);
    MaybeReachingVariableUse.ReachingUses input = new MaybeReachingVariableUse.ReachingUses();

    Node cond = Node.newString(Token.NAME, "x");
    Node ifNode = new Node(Token.IF, cond, new Node(Token.BLOCK));
    MaybeReachingVariableUse.ReachingUses outputIf = analysis.flowThrough(ifNode, input);
    assertNotNull(outputIf);

    Node whileNode = new Node(Token.WHILE, cond, new Node(Token.BLOCK));
    MaybeReachingVariableUse.ReachingUses outputWhile = analysis.flowThrough(whileNode, input);
    assertNotNull(outputWhile);

    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), cond);
    MaybeReachingVariableUse.ReachingUses outputDo = analysis.flowThrough(doNode, input);
    assertNotNull(outputDo);
  }

  public void testFlowThroughLogicalAndHook() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);
    MaybeReachingVariableUse.ReachingUses input = new MaybeReachingVariableUse.ReachingUses();

    Node left = Node.newString(Token.NAME, "a");
    Node right = Node.newString(Token.NAME, "b");
    Node andNode = new Node(Token.AND, left, right);
    assertNotNull(analysis.flowThrough(andNode, input));

    Node orNode = new Node(Token.OR, left, right);
    assertNotNull(analysis.flowThrough(orNode, input));

    Node cond = Node.newString(Token.NAME, "c");
    Node hookNode = new Node(Token.HOOK, cond, left, right);
    assertNotNull(analysis.flowThrough(hookNode, input));
  }

  public void testFlowThroughVarAndAssignments() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    Scope scope = new Scope(root, compiler);
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(root, true, true);

    MaybeReachingVariableUse analysis = new MaybeReachingVariableUse(cfg, scope, compiler);
    MaybeReachingVariableUse.ReachingUses input = new MaybeReachingVariableUse.ReachingUses();

    Node varName = Node.newString(Token.NAME, "x");
    varName.addChildToFront(Node.newNumber(1.0));
    Node varNode = new Node(Token.VAR, varName);
    assertNotNull(analysis.flowThrough(varNode, input));

    Node assignTarget = Node.newString(Token.NAME, "x");
    Node assignValue = Node.newNumber(2.0);
    Node assignNode = new Node(Token.ASSIGN, assignTarget, assignValue);
    assertNotNull(analysis.flowThrough(assignNode, input));

    Node assignAddTarget = Node.newString(Token.NAME, "x");
    Node assignAddValue = Node.newNumber(2.0);
    Node assignAddNode = new Node(Token.ASSIGN_ADD, assignAddTarget, assignAddValue);
    assertNotNull(analysis.flowThrough(assignAddNode, input));
  }
}