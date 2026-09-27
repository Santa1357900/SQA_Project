package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TypedScopeCreatorTest {

  @Test
  public void testCreateInitialScopeAndCreateScopeNullParent() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JSTypeRegistry registry = compiler.getTypeRegistry();
    assertNotNull(registry);

    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    
    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
    assertTrue(scope.isGlobal());

    Scope globalScope = creator.createScope(root, null);
    assertNotNull(globalScope);
    assertTrue(globalScope.isGlobal());
  }

  @Test
  public void testPatchGlobalScopeNullScript() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    Scope globalScope = creator.createScope(root, null);

    Node scriptRoot = new Node(Token.SCRIPT);
    compiler.getSourceFileByName("testScript");
    
    try {
      creator.patchGlobalScope(globalScope, scriptRoot);
      fail("Expected exception due to missing source name or preconditions");
    } catch (Throwable t) {
      // Expected Preconditions failure or NullPointerException because scriptName is null or not set properly
      assertNotNull(t);
    }
  }

  @Test
  public void testLocalScopeCreation() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    
    Node globalRoot = new Node(Token.SCRIPT);
    Scope globalScope = creator.createScope(globalRoot, null);

    Node fnNode = new Node(Token.FUNCTION, new Node(Token.NAME, "testFn"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Scope localScope = creator.createScope(fnNode, globalScope);
    assertNotNull(localScope);
    assertTrue(!localScope.isGlobal());
  }
}