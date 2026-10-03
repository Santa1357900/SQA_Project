package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TypedScopeCreatorTest {

  @Test
  public void testCreateInitialScopeAndCreateScopeNullParent() throws Throwable {
    Compiler compiler = new Compiler();
    // Initialize basic options or dummy compiler setup if necessary, 
    // but Compiler constructor is safe.
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

    // Build a basic root node with script and block
    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.SCRIPT);
    Node src = new Node(Token.SCRIPT);
    root.addChildToBack(externs);
    root.addChildToBack(src);

    Scope globalScope = scopeCreator.createScope(root, null);
    assertNotNull(globalScope);
    assertTrue(globalScope.isGlobal());
  }

  @Test
  public void testCreateInitialScopeDirectly() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.SCRIPT);
    Node src = new Node(Token.SCRIPT);
    root.addChildToBack(externs);
    root.addChildToBack(src);

    Scope initialScope = scopeCreator.createInitialScope(root);
    assertNotNull(initialScope);
    assertTrue(initialScope.isGlobal());
  }

  @Test
  public void testPatchGlobalScopeInvalidConditions() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.SCRIPT);
    Node src = new Node(Token.SCRIPT);
    root.addChildToBack(externs);
    root.addChildToBack(src);

    Scope globalScope = scopeCreator.createScope(root, null);

    // patchGlobalScope expects a SCRIPT node. Passing BLOCK should trigger IllegalStateException.
    try {
      scopeCreator.patchGlobalScope(globalScope, root);
      fail("Expected IllegalStateException for non-script node");
    } catch (IllegalStateException e) {
      assertTrue(e != null);
    }
  }

  @Test
  public void testLocalScopeCreation() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.SCRIPT);
    Node src = new Node(Token.SCRIPT);
    root.addChildToBack(externs);
    root.addChildToBack(src);

    Scope globalScope = scopeCreator.createScope(root, null);

    // Create a local scope with a function root node
    Node fnNode = new Node(Token.FUNCTION, new Node(Token.NAME, "myFunc"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Scope localScope = scopeCreator.createScope(fnNode, globalScope);
    assertNotNull(localScope);
    assertTrue(!localScope.isGlobal());
  }

  @Test
  public void DELEGATE_PROXY_SUFFIX_constant() throws Throwable {
    String suffix = TypedScopeCreator.DELEGATE_PROXY_SUFFIX;
    assertNotNull(suffix);
    assertTrue(suffix.length() > 0);
  }
}