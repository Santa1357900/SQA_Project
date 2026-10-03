package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class CoalesceVariableNamesTest {

  @Test
  public void testConstructorAndProcess() throws Throwable {
    Compiler compiler = new Compiler();
    CoalesceVariableNames pass = new CoalesceVariableNames(compiler, false);
    Node root = new Node(Token.BLOCK);
    pass.process(root, root);
    assertTrue(true);
  }

  @Test
  public void testPseudoNamesConstructor() throws Throwable {
    Compiler compiler = new Compiler();
    CoalesceVariableNames pass = new CoalesceVariableNames(compiler, true);
    Node root = new Node(Token.BLOCK);
    pass.process(root, root);
    assertTrue(true);
  }
}