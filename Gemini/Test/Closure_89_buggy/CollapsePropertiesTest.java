package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class CollapsePropertiesTest {

  @Test
  public void testConstructorAndBasicProcess() throws Throwable {
    Compiler compiler = new Compiler();
    CollapseProperties collapse = new CollapseProperties(compiler, false, false);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    collapse.process(externs, root);
    assertNotNull(compiler);
  }

  @Test
  public void testProcessWithExternsAndInlineAliases() throws Throwable {
    Compiler compiler = new Compiler();
    CollapseProperties collapse = new CollapseProperties(compiler, true, true);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    collapse.process(externs, root);
    assertNotNull(compiler);
  }

  @Test
  public void testCollapsePropertiesVariations() throws Throwable {
    Compiler compiler = new Compiler();
    
    CollapseProperties cp1 = new CollapseProperties(compiler, false, true);
    CollapseProperties cp2 = new CollapseProperties(compiler, true, false);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    cp1.process(externs, root);
    cp2.process(externs, root);
    
    assertNotNull(compiler);
  }
}