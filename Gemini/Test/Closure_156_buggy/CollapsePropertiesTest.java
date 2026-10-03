package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollapsePropertiesTest {

  @Test
  public void testConstructorAndProcessDefaults() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    CollapseProperties collapse = new CollapseProperties(compiler, false, false);
    collapse.process(externs, root);

    assertNotNull(compiler);
  }

  @Test
  public void testConstructorWithExternsAndInline() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    CollapseProperties collapse = new CollapseProperties(compiler, true, true);
    collapse.process(externs, root);

    assertNotNull(compiler);
  }

  @Test
  public void testProcessWithSimpleGlobalAssignment() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    
    // a = {}; a.b = 1;
    Node script = new Node(Token.SCRIPT);
    Node assignObj = new Node(Token.ASSIGN, 
        new Node(Token.NAME, "a"), 
        new Node(Token.OBJECTLIT));
    script.addChildToBack(new Node(Token.EXPR_RESULT, assignObj));

    Node assignProp = new Node(Token.ASSIGN,
        new Node(Token.GETPROP,
            new Node(Token.NAME, "a"),
            new Node(Token.STRING, "b")),
        new Node(Token.NUMBER, 1.0));
    script.addChildToBack(new Node(Token.EXPR_RESULT, assignProp));

    CollapseProperties collapse = new CollapseProperties(compiler, false, false);
    collapse.process(externs, script);

    assertTrue(script.hasChildren());
  }

  @Test
  public void testProcessWithInlineAliasesDisabledAndEnabled() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = new Node(Token.BLOCK);
    Node script = new Node(Token.SCRIPT);

    CollapseProperties collapseDisabled = new CollapseProperties(compiler, false, false);
    collapseDisabled.process(externs, script);

    CollapseProperties collapseEnabled = new CollapseProperties(compiler, false, true);
    collapseEnabled.process(externs, script);

    assertNotNull(compiler);
  }
}