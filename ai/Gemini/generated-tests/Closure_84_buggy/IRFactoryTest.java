package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.mozilla.rhino.CompilerEnvirons;
import com.google.javascript.jscomp.mozilla.rhino.Parser;
import com.google.javascript.jscomp.mozilla.rhino.ast.AstRoot;
import com.google.javascript.jscomp.mozilla.rhino.ErrorReporter;
import com.google.javascript.jscomp.mozilla.rhino.ToolErrorReporter;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.IRFactory;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

public class IRFactoryTest {

  @Test
  public void testTransformTreeBasic() throws Throwable {
    String sourceString = "var x = 10;";
    String sourceName = "testcode.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
    assertEquals(Token.SCRIPT, result.getType());
  }

  @Test
  public void testTransformTreeWithDirectives() throws Throwable {
    String sourceString = "\"use strict\"; var y = 20;";
    String sourceName = "strict.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
    assertTrue(result.getDirectives() != null);
  }

  @Test
  public void testTransformTreeWithFunctionAndCall() throws Throwable {
    String sourceString = "function foo(a, b) { return a + b; } foo(1, 2);";
    String sourceName = "func.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testTransformTreeWithControlFlow() throws Throwable {
    String sourceString = "if (true) { while(false) { break; } } else { continue; }";
    String sourceName = "control.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testTransformTreeTryCatchFinally() throws Throwable {
    String sourceString = "try { throw new Error('test'); } catch (e) { } finally { }";
    String sourceName = "trycatch.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testTransformTreeObjectAndArrayLiterals() throws Throwable {
    String sourceString = "var obj = {a: 1, 'b': 2}; var arr = [1, , 3];";
    String sourceName = "literals.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testTransformTreeSwitchStatement() throws Throwable {
    String sourceString = "switch(x) { case 1: foo(); break; default: bar(); }";
    String sourceName = "switch.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testTransformTreeRegExpAndUnary() throws Throwable {
    String sourceString = "var r = /ab+c/i; var n = -5; var p = +10;";
    String sourceName = "regexp.js";
    CompilerEnvirons env = new CompilerEnvirons();
    ErrorReporter errorReporter = new ToolErrorReporter(true);
    Parser parser = new Parser(env, errorReporter);
    AstRoot astRoot = parser.parse(sourceString, sourceName, 1);

    Set<String> extraAnnotations = new HashSet<String>();
    Config config = new Config(extraAnnotations, extraAnnotations, true, null, false);

    Node result = IRFactory.transformTree(astRoot, sourceString, config, errorReporter);
    assertNotNull(result);
  }
}