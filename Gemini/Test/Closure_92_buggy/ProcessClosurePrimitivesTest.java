package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class ProcessClosurePrimitivesTest {

  private static class DummyCompiler extends Compiler {
    private boolean codeChanged = false;
    private CssRenamingMap cssRenamingMap;
    private JSError lastError;

    @Override
    public void reportCodeChange() {
      codeChanged = true;
    }

    @Override
    public void setCssRenamingMap(CssRenamingMap map) {
      this.cssRenamingMap = map;
    }

    @Override
    public void report(JSError error) {
      this.lastError = error;
    }

    @Override
    public CodingConvention getCodingConvention() {
      return new DefaultCodingConvention();
    }
  }

  @Test
  public void testConstructorAndGetters() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);
    Set<String> exported = pass.getExportedVariableNames();
    assertNotNull(exported);
    assertTrue(exported.isEmpty());
  }

  @Test
  public void testProcessExportSymbolSimple() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);

    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.exportSymbol"),
        Node.newString(Token.STRING, "myVar")
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    Set<String> exported = pass.getExportedVariableNames();
    assertTrue(exported.contains("myVar"));
  }

  @Test
  public void testProcessExportSymbolDotted() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);

    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.exportSymbol"),
        Node.newString(Token.STRING, "namespace.myVar")
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    Set<String> exported = pass.getExportedVariableNames();
    assertTrue(exported.contains("namespace"));
  }

  @Test
  public void testProcessProvideCall() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);

    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.provide"),
        Node.newString(Token.STRING, "a.b.c")
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    assertTrue(compiler.codeChanged);
  }

  @Test
  public void testProcessRequireCallMissing() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.ERROR, false);

    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.require"),
        Node.newString(Token.STRING, "nonexistent")
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    assertNotNull(compiler.lastError);
  }

  @Test
  public void testProcessSetCssNameMappingValid() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);

    Node root = new Node(Token.BLOCK);
    Node objlit = new Node(Token.OBJECTLIT);
    objlit.addChildToBack(Node.newString(Token.STRING, "key1"));
    objlit.addChildToBack(Node.newString(Token.STRING, "val1"));

    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.setCssNameMapping"),
        objlit
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    assertNotNull(compiler.cssRenamingMap);
    assertEquals("val1", compiler.cssRenamingMap.get("key1"));
    assertEquals("unknown", compiler.cssRenamingMap.get("unknown"));
  }

  @Test
  public void testTrySimplifyNewDate() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, true);

    Node root = new Node(Token.BLOCK);
    Node googNowCall = new Node(Token.CALL, Node.newString(Token.GETPROP, "goog.now"));
    Node newDate = new Node(Token.NEW, Node.newString(Token.NAME, "Date"), googNowCall);
    Node expr = new Node(Token.EXPR_RESULT, newDate);
    root.addChildToBack(expr);

    pass.process(null, root);
    assertTrue(compiler.codeChanged);
  }

  @Test
  public void testInvalidProvideName() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler, CheckLevel.OFF, false);

    Node root = new Node(Token.BLOCK);
    Node call = new Node(Token.CALL, 
        Node.newString(Token.GETPROP, "goog.provide"),
        Node.newString(Token.STRING, "invalid-name")
    );
    Node expr = new Node(Token.EXPR_RESULT, call);
    root.addChildToBack(expr);

    pass.process(null, root);
    assertNotNull(compiler.lastError);
  }
}