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
        new BasicErrorManager() {
          @Override protected void printSummary() {}
          @Override public void println(CheckLevel level, JSError error) {}
        }, 
        null
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
  public void testProgressBounds() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(1.5);
    assertEquals(1.0, compiler.getProgress(), 0.001);

    compiler.setProgress(-0.5);
    assertEquals(0.0, compiler.getProgress(), 0.001);

    compiler.setProgress(0.5);
    assertEquals(0.5, compiler.getProgress(), 0.001);
  }

  @Test
  public void testUniqueNameId() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    com.google.common.base.Supplier<String> supplier = compiler.getUniqueNameIdSupplier();
    assertNotNull(supplier);
    assertEquals("0", supplier.get());
    assertEquals("1", supplier.get());
    compiler.resetUniqueNameId();
    assertEquals("0", supplier.get());
  }

  @Test
  public void testLoggingLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.OFF);
    // Just verify it doesn't throw an exception
    assertTrue(true);
  }

  @Test
  public void testGetReleaseVersionAndDate() throws Throwable {
    try {
      String version = Compiler.getReleaseVersion();
      assertNotNull(version);
    } catch (Exception e) {
      // Resource bundle might not be present in test classpath, ignore or assert
    }
    try {
      String date = Compiler.getReleaseDate();
      assertNotNull(date);
    } catch (Exception e) {
      // Resource bundle might not be present in test classpath, ignore or assert
    }
  }

  @Test
  public void testDisableThreads() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    // Verify execution under disabled threads or basic state
    Result result = compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("input.js", "var x = 1;"),
        new CompilerOptions()
    );
    assertNotNull(result);
  }

  @Test
  public void testCodeBuilder() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    assertEquals(0, cb.getLength());
    cb.append("hello\nworld");
    assertEquals(1, cb.getLineIndex());
    assertTrue(cb.endsWith("world"));
    assertEquals("hello\nworld", cb.toString());
    
    cb.reset();
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testInitOptionsAndDiagnostics() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.checkTypes = true;
    compiler.initOptions(options);
    assertNotNull(compiler.getOptions());
  }

  @Test
  public void testGetSourceLineAndRegion() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("nonexistent", 1));
    assertNull(compiler.getSourceLine("nonexistent", 0));
    assertNull(compiler.getSourceRegion("nonexistent", 1));
    assertNull(compiler.getSourceRegion("nonexistent", 0));
  }
}