package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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
    MessageFormatter formatter = new LightMessageFormatter();
    LoggerErrorManager errorManager = new LoggerErrorManager(formatter, java.util.logging.Logger.getAnonymousLogger());
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
  public void testInitOptions() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    assertNotNull(compiler.getOptions());
    assertNotNull(compiler.getErrorManager());
  }

  @Test
  public void testDisableThreads() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    // No direct getter for useThreads, but we can exercise it via parsing/compiling
    assertNotNull(compiler);
  }

  @Test
  public void testResetUniqueNameId() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    assertNotNull(compiler.getUniqueNameIdSupplier().get());
  }

  @Test
  public void testGetRootBeforeInit() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getRoot());
  }

  @Test
  public void testGetModuleGraphBeforeInit() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getModuleGraph());
  }

  @Test
  public void testGetTypeRegistry() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getTypeRegistry());
  }

  @Test
  public void testGetTypeValidator() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getTypeValidator());
  }

  @Test
  public void testGetReverseAbstractInterpreter() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getReverseAbstractInterpreter());
  }

  @Test
  public void testGetDefaultErrorReporter() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getDefaultErrorReporter());
  }

  @Test
  public void testGetSourceLineInvalidLine() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("nonexistent", 0));
    assertNull(compiler.getSourceLine("nonexistent", -1));
  }

  @Test
  public void testGetSourceRegionInvalidLine() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceRegion("nonexistent", 0));
    assertNull(compiler.getSourceRegion("nonexistent", -5));
  }

  @Test
  public void testSetLoggingLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.OFF);
    // Should not throw any exception
  }

  @Test
  public void testCodeBuilderOperations() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    assertEquals(0, cb.getLength());
    assertEquals("", cb.toString());

    cb.append("hello\nworld");
    assertEquals(11, cb.getLength());
    assertEquals(1, cb.getLineIndex());
    assertTrue(cb.endsWith("world"));
    assertFalse(cb.endsWith("hello"));

    cb.reset();
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testGetAstDotGraphNullRoot() throws Throwable {
    Compiler compiler = new Compiler();
    assertEquals("", compiler.getAstDotGraph());
  }

  @Test
  public void testGetInputsInOrderEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getInputsInOrder());
    assertTrue(compiler.getInputsInOrder().isEmpty());
  }

  @Test
  public void testAcceptEcmaScript5() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT3);
    compiler.initOptions(options);
    assertFalse(compiler.acceptEcmaScript5());

    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5);
    compiler.initOptions(options);
    assertTrue(compiler.acceptEcmaScript5());
  }

  @Test
  public void testLanguageMode() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT);
    compiler.initOptions(options);
    assertEquals(CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT, compiler.languageMode());
  }

  @Test
  public void testAcceptConstKeyword() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.acceptConstKeyword = true;
    compiler.initOptions(options);
    assertTrue(compiler.acceptConstKeyword());
  }

  @Test
  public void testGetErrorCountAndWarningCount() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    assertEquals(0, compiler.getErrorCount());
    assertEquals(0, compiler.getWarningCount());
    assertFalse(compiler.hasErrors());
  }

  @Test
  public void testAddToDebugLog() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.addToDebugLog("test message");
    // Verifies no exception is thrown
  }

  @Test
  public void testGetCssRenamingMap() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getCssRenamingMap());
    CssRenamingMap map = new SimpleReferenceRenamingMap();
    compiler.setCssRenamingMap(map);
    assertEquals(map, compiler.getCssRenamingMap());
  }

  @Test
  public void testHasRegExpGlobalReferences() throws Throwable {
    Compiler compiler = new Compiler();
    assertTrue(compiler.hasRegExpGlobalReferences());
    compiler.setHasRegExpGlobalReferences(false);
    assertFalse(compiler.hasRegExpGlobalReferences());
  }

  @Test
  public void testGetPassConfig() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    assertNotNull(compiler.getPassConfig());
  }

  @Test
  public void testSetPassConfigValidAndDuplicate() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    PassConfig passes = new DefaultPassConfig(options);
    compiler.setPassConfig(passes);

    try {
      compiler.setPassConfig(passes);
      fail("Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("this.passes has already been assigned"));
    }
  }

  @Test
  public void testSetPassConfigNull() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setPassConfig(null);
      fail("Should have thrown NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  @Test
  public void testParseTestCode() throws Throwable {
    Compiler compiler = new Compiler();
    Node node = compiler.parseTestCode("var x = 1;");
    assertNotNull(node);
  }

  @Test
  public void testParseSyntheticCode() throws Throwable {
    Compiler compiler = new Compiler();
    Node node = compiler.parseSyntheticCode("var y = 2;");
    assertNotNull(node);
  }

  @Test
  public void testParseSyntheticCodeWithName() throws Throwable {
    Compiler compiler = new Compiler();
    Node node = compiler.parseSyntheticCode("testfile.js", "var z = 3;");
    assertNotNull(node);
  }

  @Test
  public void testToSourceSimple() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    String src = compiler.toSource();
    assertEquals("", src);
  }

  @Test
  public void testRemoveInputNonExistent() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    // Should not throw error
    compiler.removeInput("nonexistent.js");
  }

  @Test
  public void testNewExternInputConflict() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    
    // Initialize externsRoot so newExternInput doesn't throw NPE on child add
    compiler.externsRoot = new Node(Token.BLOCK);

    compiler.newExternInput("extern.js");
    try {
      compiler.newExternInput("extern.js");
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Conflicting externs name: extern.js"));
    }
  }

  @Test
  public void testStateSaveAndRestore() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    
    Compiler.IntermediateState state = compiler.getState();
    assertNotNull(state);

    compiler.setState(state);
    assertNotNull(compiler.getOptions());
  }

}