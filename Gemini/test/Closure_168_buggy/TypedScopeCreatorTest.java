package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

public class TypedScopeCreatorTest {

  @Test
  public void testDelegateProxySuffix() throws Throwable {
    assertNotNull(TypedScopeCreator.DELEGATE_PROXY_SUFFIX);
    assertTrue(TypedScopeCreator.DELEGATE_PROXY_SUFFIX.contains("Proxy"));
  }

  @Test
  public void testDiagnosticTypes() throws Throwable {
    assertNotNull(TypedScopeCreator.MALFORMED_TYPEDEF);
    assertNotNull(TypedScopeCreator.ENUM_INITIALIZER);
    assertNotNull(TypedScopeCreator.CTOR_INITIALIZER);
    assertNotNull(TypedScopeCreator.IFACE_INITIALIZER);
    assertNotNull(TypedScopeCreator.CONSTRUCTOR_EXPECTED);
    assertNotNull(TypedScopeCreator.UNKNOWN_LENDS);
    assertNotNull(TypedScopeCreator.LENDS_ON_NON_OBJECT);
  }

  @Test
  public void testCreateInitialScopeAndPatchScopeNullCases() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node scriptNode = new Node(Token.SCRIPT);
    Scope initialScope = creator.createInitialScope(scriptNode);
    assertNotNull(initialScope);
    assertTrue(initialScope.isGlobal());

    try {
      creator.patchGlobalScope(null, scriptNode);
      fail("Expected NullPointerException or IllegalStateException");
    } catch (Throwable e) {
      // Expected due to preconditions check
    }
  }

  @Test
  public void testCreateScopeWithNullParent() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.SCRIPT);
    Node child = new Node(Token.BLOCK);
    root.addChildToBack(child);

    Scope scope = creator.createScope(root, null);
    assertNotNull(scope);
    assertTrue(scope.isGlobal());
  }
}