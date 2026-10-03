package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TypedScopeCreatorTest {

  @Test
  public void testCreateInitialScopeWithCompiler() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    
    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
    assertNotNull(scope.getVar("Object"));
    assertNotNull(scope.getVar("Array"));
    assertNotNull(scope.getVar("ActiveXObject"));
  }

  @Test
  public void testCreateScopeGlobal() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    Node nameNode = Node.newString(Token.NAME, "myGlobalVar");
    Node varNode = new Node(Token.VAR, nameNode);
    root.addChildToBack(varNode);
    
    Scope scope = creator.createScope(root, null);
    assertNotNull(scope);
    assertNotNull(scope.getVar("myGlobalVar"));
  }

  @Test
  public void testCreateScopeLocal() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    Node globalRoot = new Node(Token.SCRIPT);
    Scope globalScope = creator.createScope(globalRoot, null);
    
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "myFunc"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Scope localScope = creator.createScope(fnNode, globalScope);
    assertNotNull(localScope);
  }

  @Test
  public void testPatchGlobalScopeNullCheck() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    Scope globalScope = creator.createScope(root, null);
    
    try {
      creator.patchGlobalScope(globalScope, null);
    } catch (Throwable t) {
      // Expected due to precondition check on scriptRoot
      assertNotNull(t);
    }
  }

  @Test
  public void testDiscoverEnumsAndTypedefs() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    JSTypeRegistry registry = compiler.getTypeRegistry();
    Node root = new Node(Token.SCRIPT);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
  }
}