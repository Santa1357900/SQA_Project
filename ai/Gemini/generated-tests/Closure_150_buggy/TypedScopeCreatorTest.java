package com.google.javascript.jscomp;

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import org.junit.Test;
import static org.junit.Assert.*;

public class TypedScopeCreatorTest {

  @Test
  public void testConstantsAndDelegateProxy() throws Throwable {
    assertEquals("(Proxy)", TypedScopeCreator.DELEGATE_PROXY_SUFFIX);
    assertNotNull(TypedScopeCreator.MALFORMED_TYPEDEF);
    assertNotNull(TypedScopeCreator.ENUM_INITIALIZER);
    assertNotNull(TypedScopeCreator.CONSTRUCTOR_EXPECTED);
  }

  @Test
  public void testDeferredSetTypeAndNullChecks() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    assertNotNull(creator);

    JSTypeRegistry registry = compiler.getTypeRegistry();
    assertNotNull(registry);

    Node scriptNode = new Node(Token.SCRIPT);
    Scope initialScope = creator.createInitialScope(scriptNode);
    assertNotNull(initialScope);

    Scope scope = creator.createScope(scriptNode, null);
    assertNotNull(scope);
  }

  @Test
  public void testCreateScopeWithParent() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.SCRIPT);
    Scope parentScope = new Scope(root, compiler);
    Node childRoot = new Node(Token.BLOCK);
    
    Scope localScope = creator.createScope(childRoot, parentScope);
    assertNotNull(localScope);
    assertTrue(localScope.isLocal());
  }

  @Test
  public void testDiscoverEnumsAndLiteralTypes() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.SCRIPT);
    Node nameNode = Node.newString(Token.NAME, "myEnum");
    root.addChildToBack(nameNode);

    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
  }

  @Test
  public void testNullSafetyOnCreateScope() throws Throwable {
    Compiler compiler = new Compiler();
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node root = new Node(Token.SCRIPT);
    boolean exceptionThrown = false;
    try {
      creator.createScope(null, null);
    } catch (Throwable t) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }
}