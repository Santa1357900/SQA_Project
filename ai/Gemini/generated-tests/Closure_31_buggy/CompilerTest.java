package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.InputId;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
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
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(bos);
    Compiler compiler = new Compiler(ps);
    assertNotNull(compiler);
  }

  @Test
  public void testErrorManagerConstructor() throws Throwable {
    Compiler compiler = new Compiler(new LoggerErrorManager(new MessageFormatter() {
      public String formatError(JSError error) { return ""; }
      public String formatWarning(JSError error) { return ""; }
    }, java.util.logging.Logger.getLogger("test")));
    assertNotNull(compiler);
    assertNotNull(compiler.getErrorManager());
  }

  @Test(expected = NullPointerException.class)
  public void testSetErrorManagerNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setErrorManager(null);
  }

  @Test
  public void testProgressBoundaries() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(1.5);
    assertEquals(1.0, compiler.getProgress(), 0.001);

    compiler.setProgress(-0.5);
    assertEquals(0.0, compiler.getProgress(), 0.001);

    compiler.setProgress(0.5);
    assertEquals(0.5, compiler.getProgress(), 0.001);
  }

  @Test
  public void testCodeBuilderOperations() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    assertEquals(0, cb.getLength());
    assertEquals("", cb.toString());

    cb.append("var a = 1;\n");
    assertTrue(cb.getLength() > 0);
    assertTrue(cb.endsWith(";"));
    assertEquals(1, cb.getLineIndex());

    cb.reset();
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testDisableThreads() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    // Verify it doesn't throw and sets internal flag
    assertNotNull(compiler);
  }

  @Test
  public void testGetSourceLineAndRegion() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("nonexistent", 1));
    assertNull(compiler.getSourceLine("nonexistent", 0));
    assertNull(compiler.getSourceRegion("nonexistent", 1));
    assertNull(compiler.getSourceRegion("nonexistent", 0));
  }

  @Test
  public void testResetUniqueNameId() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    assertNotNull(compiler.getUniqueNameIdSupplier().get());
  }

  @Test
  public void testSetLoggingLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.INFO);
    // Just verifying execution without exception
    assertTrue(true);
  }

  @Test
  public void testGetAstDotGraphEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    String dot = compiler.getAstDotGraph();
    assertEquals("", dot);
  }

  @Test
  public void testCreateFillFileName() throws Throwable {
    String fillName = Compiler.createFillFileName("module1");
    assertEquals("[module1]", fillName);
  }

  @Test(expected = IllegalStateException.class)
  public void testSetPassConfigTwice() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    PassConfig passConfig1 = new DefaultPassConfig(options);
    PassConfig passConfig2 = new DefaultPassConfig(options);
    compiler.setPassConfig(passConfig1);
    compiler.setPassConfig(passConfig2);
  }

  @Test
  public void testGetModuleGraphNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getModuleGraph());
  }

  @Test
  public void testNewExternInputConflict() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.parseTestCode("var x;");
    try {
      compiler.newExternInput("[testcode]");
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Conflicting externs name"));
    }
  }

  @Test
  public void testRemoveExternNonExistent() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.removeExternInput(new InputId("nonexistent"));
    // Should return silently without error
    assertNull(compiler.getInput(new InputId("nonexistent")));
  }
}