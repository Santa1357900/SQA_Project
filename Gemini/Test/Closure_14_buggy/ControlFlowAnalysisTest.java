package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class ControlFlowAnalysisTest {

  @Test
  public void testMayThrowExceptionBasic() throws Throwable {
    Node callNode = new Node(Token.CALL);
    assertTrue(ControlFlowAnalysis.mayThrowException(callNode));

    Node numberNode = new Node(Token.NUMBER, 1.0);
    assertFalse(ControlFlowAnalysis.mayThrowException(numberNode));

    Node funcNode = new Node(Token.FUNCTION);
    assertFalse(ControlFlowAnalysis.mayThrowException(funcNode));
  }

  @Test
  public void testIsBreakStructure() throws Throwable {
    assertTrue(ControlFlowAnalysis.isBreakStructure(new Node(Token.FOR), false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(new Node(Token.WHILE), false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(new Node(Token.DO), false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(new Node(Token.SWITCH), false));

    assertFalse(ControlFlowAnalysis.isBreakStructure(new Node(Token.IF), false));
    assertTrue(ControlFlowAnalysis.isBreakStructure(new Node(Token.IF), true));

    assertFalse(ControlFlowAnalysis.isBreakStructure(new Node(Token.EXPR_RESULT), false));
  }

  @Test
  public void testIsContinueStructure() throws Throwable {
    assertTrue(ControlFlowAnalysis.isContinueStructure(new Node(Token.FOR)));
    assertTrue(ControlFlowAnalysis.isContinueStructure(new Node(Token.WHILE)));
    assertTrue(ControlFlowAnalysis.isContinueStructure(new Node(Token.DO)));

    assertFalse(ControlFlowAnalysis.isContinueStructure(new Node(Token.SWITCH)));
    assertFalse(ControlFlowAnalysis.isContinueStructure(new Node(Token.IF)));
  }

  @Test
  public void testIsBreakTarget() throws Throwable {
    Node loopNode = new Node(Token.FOR);
    assertFalse(ControlFlowAnalysis.isBreakTarget(loopNode, "nonexistent"));
  }

  @Test
  public void testComputeFollowNodeNullParent() throws Throwable {
    Node node = new Node(Token.EXPR_RESULT);
    Node follow = ControlFlowAnalysis.computeFollowNode(node);
    assertNull(follow);
  }
}