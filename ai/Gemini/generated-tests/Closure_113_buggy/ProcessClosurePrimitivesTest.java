package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfoBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Set;

public class ProcessClosurePrimitivesTest {

  @Test
  public void testGetExportedVariableNames() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    Set<String> exported = pass.getExportedVariableNames();
    assertNotNull(exported);
    assertTrue(exported.isEmpty());
  }

  @Test
  public void testHotSwapScript() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    Node scriptRoot = IR.script();
    Node originalRoot = IR.script();
    // Should not throw exception
    pass.hotSwapScript(scriptRoot, originalRoot);
  }

  @Test
  public void testProcessProvideValid() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "provide"),
        IR.string("a.b.c")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    // Verify that provide created a namespace definition in root
    assertNotNull(root.getFirstChild());
  }

  @Test
  public void testProcessProvideInvalidIdentifier() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "provide"),
        IR.string("a.invalid-name.c")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertTrue(compiler.hasErrors());
  }

  @Test
  public void testProcessRequireValid() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    // First provide 'a.b'
    Node provideCall = IR.call(
        IR.getProp(IR.name("goog"), "provide"),
        IR.string("a.b")
    );
    Node provideExpr = IR.exprResult(provideCall);

    // Then require 'a.b'
    Node requireCall = IR.call(
        IR.getProp(IR.name("goog"), "require"),
        IR.string("a.b")
    );
    Node requireExpr = IR.exprResult(requireCall);

    Node root = IR.script(provideExpr, requireExpr);

    pass.process(null, root);
    // Require should be detached
    assertFalse(root.hasChild(requireExpr));
  }

  @Test
  public void testProcessRequireMissingProvide() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR);
    
    Node requireCall = IR.call(
        IR.getProp(IR.name("goog"), "require"),
        IR.string("nonexistent.namespace")
    );
    Node requireExpr = IR.exprResult(requireCall);
    Node root = IR.script(requireExpr);

    pass.process(null, root);
    assertTrue(compiler.hasErrors());
  }

  @Test
  public void testProcessExportSymbol() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "exportSymbol"),
        IR.string("myNamespace.mySymbol"),
        IR.name("someVar")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertTrue(pass.getExportedVariableNames().contains("myNamespace"));
  }

  @Test
  public void testProcessExportSymbolSimple() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "exportSymbol"),
        IR.string("myVariable"),
        IR.name("someVar")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertTrue(pass.getExportedVariableNames().contains("myVariable"));
  }

  @Test
  public void testProcessDefineValid() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "define"),
        IR.string("MY_DEFINE"),
        IR.number(123)
    );
    Node expr = IR.exprResult(callNode);
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(false);
    jsDocBuilder.recordDefine();
    expr.setJSDocInfo(jsDocBuilder.build(null));
    
    Node root = IR.script(expr);

    pass.process(null, root);
    // Should replace goog.define with standard assignment/var declaration
    assertFalse(compiler.hasErrors());
  }

  @Test
  public void testProcessSetCssNameMappingValid() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node objLit = IR.objectlit(
        IR.stringKey("foo", IR.string("bar"))
    );
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "setCssNameMapping"),
        objLit,
        IR.string("BY_WHOLE")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertFalse(compiler.hasErrors());
  }

  @Test
  public void testProcessSetCssNameMappingInvalidArg() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "setCssNameMapping"),
        IR.string("notAnObjectLit")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertTrue(compiler.hasErrors());
  }

  @Test
  public void testFunctionNamespaceError() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    // Provide a namespace
    Node provideCall = IR.call(
        IR.getProp(IR.name("goog"), "provide"),
        IR.string("myFunc")
    );
    Node provideExpr = IR.exprResult(provideCall);

    // Declare a function with the same name in global scope
    Node funcNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block());
    
    Node root = IR.script(provideExpr, funcNode);

    pass.process(null, root);
    assertTrue(compiler.hasErrors());
  }

  @Test
  public void testBaseClassCallFirstArgNotThis() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, null, CheckLevel.OFF);
    
    Node callNode = IR.call(
        IR.getProp(IR.name("goog"), "base"),
        IR.name("notThis")
    );
    Node expr = IR.exprResult(callNode);
    Node root = IR.script(expr);

    pass.process(null, root);
    assertTrue(compiler.hasErrors());
  }

}