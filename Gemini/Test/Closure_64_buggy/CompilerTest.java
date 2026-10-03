package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

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

  @Test(expected = NullPointerException.class)
  public void testSetErrorManagerNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setErrorManager(null);
  }

  @Test
  public void testDisableThreads() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    // Verify no exception is thrown
  }

  @Test
  public void testResetUniqueNameId() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    // Verify no exception
  }

  @Test
  public void testGetSourceLineEdgeCases() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("nonexistent", 0));
    assertNull(compiler.getSourceLine("nonexistent", -1));
    assertNull(compiler.getSourceLine("nonexistent", 5));
  }

  @Test
  public void testGetSourceRegionEdgeCases() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceRegion("nonexistent", 0));
    assertNull(compiler.getSourceRegion("nonexistent", -1));
    assertNull(compiler.getSourceRegion("nonexistent", 5));
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
    
    cb.append("hello\nworld");
    assertTrue(cb.getLength() > 0);
    assertTrue(cb.endsWith("world"));
    assertEquals(1, cb.getLineIndex());
    
    cb.reset();
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testGetAstDotGraphEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    String dot = compiler.getAstDotGraph();
    assertEquals("", dot);
  }

  @Test
  public void testGetInputsInOrderEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    List<CompilerInput> inputs = compiler.getInputsInOrder();
    assertNotNull(inputs);
    assertTrue(inputs.isEmpty());
  }

  @Test
  public void testNewExternInputConflict() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    
    // Setup roots so externInput can be added to externsRoot
    compiler.parseTestCode("var x;");
    
    try {
      compiler.newExternInput("testName");
      compiler.newExternInput("testName");
      fail("Should have thrown IllegalArgumentException for duplicate extern name");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Conflicting externs name"));
    }
  }

  @Test
  public void testInitOptionsWithDiagnosticGroups() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.checkTypes = true;
    compiler.initOptions(options);
    assertNotNull(compiler.getOptions());
  }

  @Test
  public void testGetDefaultErrorReporter() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getDefaultErrorReporter());
  }

  @Test
  public void testAcceptEcmaScript5() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5);
    compiler.initOptions(options);
    assertTrue(compiler.acceptEcmaScript5());
  }

}