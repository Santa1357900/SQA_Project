package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class TypedScopeCreatorTest {

  @Test
  public void testDelegateProxySuffix() throws Throwable {
    assertNotNull(TypedScopeCreator.DELEGATE_PROXY_SUFFIX);
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
  public void testCreateInitialScopeWithCompiler() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = new Node(Token.SCRIPT);
    
    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
    assertTrue(scope.isGlobal());
  }

  @Test(expected = NullPointerException.class)
  public void testCreateScopeNullRoot() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    creator.createScope(null, null);
  }

  @Test
  public void testPatchGlobalScopeInvalidState() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node scriptRoot = new Node(Token.SCRIPT);
    Scope scope = new Scope(scriptRoot, compiler);
    
    try {
      creator.patchGlobalScope(scope, scriptRoot);
      fail("Expected IllegalStateException due to non-global scope");
    } catch (IllegalStateException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCreateScopeWithCodingConvention() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    CodingConvention convention = new DefaultCodingConvention();
    TypedScopeCreator creator = new TypedScopeCreator(compiler, convention);
    Node root = new Node(Token.SCRIPT);
    
    Scope scope = creator.createScope(root, null);
    assertNotNull(scope);
  }
}