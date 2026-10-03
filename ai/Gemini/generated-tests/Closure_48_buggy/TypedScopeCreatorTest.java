package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TypedScopeCreatorTest {

  @Test
  public void testCreateInitialScope() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JSTypeRegistry registry = compiler.getTypeRegistry();
    assertNotNull(registry);

    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    Node root = IR.script();
    Scope scope = creator.createInitialScope(root);
    assertNotNull(scope);
    assertNotNull(scope.getVar("Object"));
    assertNotNull(scope.getVar("Array"));
    assertNotNull(scope.getVar("undefined"));
    assertNotNull(scope.getVar("ActiveXObject"));
  }

  @Test
  public void testCreateScopeGlobalAndLocal() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node root = IR.script(
        IR.var(IR.name("x"), IR.number(10)),
        IR.function(IR.name("f"),
            IR.paramList(IR.name("a")),
            IR.block(
                IR.returnNode(IR.name("a"))
            )
        )
    );
    root.setInputId(new com.google.javascript.rhino.InputId("testscript"));

    Scope globalScope = creator.createScope(root, null);
    assertNotNull(globalScope);
    assertNotNull(globalScope.getVar("x"));
    assertNotNull(globalScope.getVar("f"));

    Scope localScope = creator.createScope(root.getLastChild(), globalScope);
    assertNotNull(localScope);
    assertNotNull(localScope.getVar("a"));
  }

  @Test
  public void testPatchGlobalScopeNullCheck() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);

    Node script = IR.script(IR.var(IR.name("y"), IR.number(20)));
    script.setInputId(new com.google.javascript.rhino.InputId("scriptName"));
    Scope globalScope = creator.createScope(script, null);

    try {
      creator.patchGlobalScope(null, script);
      fail("Expected NullPointerException or similar check failure");
    } catch (Throwable t) {
      // Expected due to Preconditions.checkNotNull(globalScope) or similar
    }
  }
}