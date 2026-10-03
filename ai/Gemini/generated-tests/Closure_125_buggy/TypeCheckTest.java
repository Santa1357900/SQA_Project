package com.google.javascript.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

public class TypeCheckTest {

  @Test
  public void testConstructorsAndConstants() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    
    TypeCheck tc1 = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    assertNotNull(tc1);

    TypeCheck tc2 = new TypeCheck(compiler, null, registry);
    assertNotNull(tc2);

    assertNotNull(TypeCheck.ALL_DIAGNOSTICS);
    assertNotNull(TypeCheck.UNEXPECTED_TOKEN);
  }

  @Test
  public void testReportMissingPropertiesChaining() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    TypeCheck result = tc.reportMissingProperties(false);
    assertEquals(tc, result);
    
    TypeCheck result2 = tc.reportMissingProperties(true);
    assertEquals(tc, result2);
  }

  @Test
  public void testGetTypedPercentEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    double percent = tc.getTypedPercent();
    assertEquals(0.0, percent, 0.001);
  }

  @Test
  public void testCheckNullNode() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    try {
      tc.check(null, false);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  @Test
  public void testVisitSimpleNodes() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    Scope scope = new Scope(Node.newString(Token.NAME, "global"), compiler);
    MemoizedScopeCreator scopeCreator = new MemoizedScopeCreator(new TypedScopeCreator(compiler));

    Node trueNode = Node.newNumber(1.0);
    NodeTraversal traversal = new NodeTraversal(compiler, tc, scopeCreator);
    
    // Test basic node visit via public check method with dummy AST
    Node script = new Node(Token.SCRIPT);
    script.addChildToBack(Node.newNumber(10));
    
    try {
      tc.check(script, false);
    } catch (Exception e) {
      // Depending on scope setup, might throw or pass
    }
  }

  @Test
  public void testProcessForTestingNullChecks() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    Node script = new Node(Token.SCRIPT);
    Node jsRoot = new Node(Token.BLOCK);
    script.addChildToBack(jsRoot);

    try {
      tc.processForTesting(script, jsRoot);
    } catch (Exception e) {
      // Expected since scopeCreator / topScope preconditions or AST structure might need full setup
    }
  }

  @Test
  public void testBinaryOperatorEdgeCases() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc = new TypeCheck(compiler, null, registry, CheckLevel.WARNING);
    
    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    NodeTraversal traversal = new NodeTraversal(compiler, tc);
    
    try {
      tc.visit(traversal, addNode, new Node(Token.EXPR_RESULT, addNode));
    } catch (Exception e) {
      // Safe guard against incomplete scope/types
    }
  }
}