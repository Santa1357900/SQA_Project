package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class CompilerTest {

  @Test
  public void testDefaultConstructor() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler);
    assertNull(compiler.getErrorManager());
  }

  @Test
  public void testStreamConstructor() throws Throwable {
    Compiler compiler = new Compiler((PrintStream) null);
    assertNotNull(compiler);
  }

  @Test
  public void testErrorManagerConstructor() throws Throwable {
    LoggerErrorManager errorManager = new LoggerErrorManager(
        new MessageFormatter() {
          public String formatError(JSError error) { return ""; }
          public String formatWarning(JSError error) { return ""; }
        },
        java.util.logging.Logger.getLogger("test")
    );
    Compiler compiler = new Compiler(errorManager);
    assertNotNull(compiler);
    assertEquals(errorManager, compiler.getErrorManager());
  }

  @Test
  public void testSetErrorManagerNull() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setErrorManager(null);
      fail("Should have thrown NullPointerException");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("the error manager cannot be null"));
    }
  }

  @Test
  public void testDisableThreads() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    // No direct getter for useThreads, but we can verify it doesn't crash
  }

  @Test
  public void testResetUniqueNameId() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    // Test passes if no exception
  }

  @Test
  public void testSetLoggingLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.OFF);
    Compiler.setLoggingLevel(Level.INFO);
  }

  @Test
  public void testCodeBuilderOperations() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    assertEquals(0, cb.getLength());
    assertEquals("", cb.toString());

    cb.append("line1\nline2");
    assertEquals(11, cb.getLength());
    assertTrue(cb.endsWith("line2"));
    assertEquals(1, cb.getLineIndex());
    assertEquals(5, cb.getColumnIndex());

    cb.reset();
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testGetSourceLineAndRegion() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());

    assertNull(compiler.getSourceLine("nonexistent", 1));
    assertNull(compiler.getSourceLine("nonexistent", 0));
    assertNull(compiler.getSourceRegion("nonexistent", 1));
    assertNull(compiler.getSourceRegion("nonexistent", 0));
  }

  @Test
  public void testParseSyntheticCode() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());

    Node root = compiler.parseSyntheticCode("var x = 1;");
    assertNotNull(root);
  }

  @Test
  public void testParseSyntheticCodeWithName() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());

    Node root = compiler.parseSyntheticCode("testFile.js", "var y = 2;");
    assertNotNull(root);
  }

  @Test
  public void testParseTestCode() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());

    Node root = compiler.parseTestCode("function foo() {}");
    assertNotNull(root);
  }

  @Test
  public void testCompileSimpleCode() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);

    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");

    Result result = compiler.compile(extern, input, options);
    assertNotNull(result);
    assertTrue(result.success);
  }

  @Test
  public void testCompileWithErrors() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.checkTypes = true;

    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "syntax error {{{");

    Result result = compiler.compile(extern, input, options);
    assertNotNull(result);
    assertFalse(result.success);
    assertTrue(compiler.hasErrors());
    assertTrue(compiler.getErrorCount() > 0);
  }

  @Test
  public void testGetAstDotGraphEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    String dot = compiler.getAstDotGraph();
    assertEquals("", dot);
  }

  @Test
  public void testGettersAndSetters() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);

    assertEquals(options, compiler.getOptions());
    assertNotNull(compiler.getTypeRegistry());
    assertNotNull(compiler.getTypeValidator());
    assertNotNull(compiler.getReverseAbstractInterpreter());
    assertNotNull(compiler.getCodingConvention());
    assertNotNull(compiler.getDefaultErrorReporter());
    assertNotNull(compiler.getDiagnosticGroups());
    assertFalse(compiler.isIdeMode());
    assertTrue(compiler.acceptEcmaScript5());
    assertFalse(compiler.acceptConstKeyword());
  }

  @Test
  public void testModuleCompilationValidation() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    externs.add(JSSourceFile.fromCode("ext.js", ""));

    List<JSModule> modules = new ArrayList<JSModule>();
    // Empty modules list should trigger error
    compiler.compileModules(externs, modules, options);
    assertTrue(compiler.hasErrors());
  }

  @Test
  public void testRemoveExternInputNonExistent() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    // Should return quietly without error
    compiler.removeExternInput("nonexistent");
  }

  @Test
  public void testNewExternInputConflict() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    JSSourceFile extern = JSSourceFile.fromCode("conflict.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "");
    compiler.compile(extern, input, options);

    try {
      compiler.newExternInput("conflict.js");
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Conflicting externs name"));
    }
  }

  @Test
  public void testStateSaveAndRestore() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    JSSourceFile extern = JSSourceFile.fromCode("ext.js", "");
    JSSourceFile input = JSSourceFile.fromCode("inp.js", "var a = 1;");
    compiler.compile(extern, input, options);

    Compiler.IntermediateState state = compiler.getState();
    assertNotNull(state);

    Compiler compiler2 = new Compiler();
    compiler2.initOptions(options);
    compiler2.setState(state);
    assertNotNull(compiler2.getRoot());
  }

  @Test
  public void testGetRootBeforeInit() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getRoot());
  }

  @Test
  public void testToSourceArrayWithModules() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    JSSourceFile extern = JSSourceFile.fromCode("ext.js", "");
    JSModule module = new JSModule("mod1");
    module.add(JSSourceFile.fromCode("mod1.js", "var z = 10;"));

    JSModule[] modules = new JSModule[] { module };
    compiler.compile(extern, modules, options);

    String[] sources = compiler.toSourceArray(module);
    assertNotNull(sources);
    assertEquals(1, sources.length);
    assertTrue(sources[0].contains("z = 10"));
  }
}