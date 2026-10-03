package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class CommandLineRunnerTest {

  @Test
  public void testDefaultExterns() throws Throwable {
    List<SourceFile> externs = CommandLineRunner.getDefaultExterns();
    assertNotNull(externs);
    assertTrue(externs.size() > 0);
  }

  @Test
  public void testCommandLineRunnerInitializationValid() throws Throwable {
    String[] args = new String[] {"--js", "testcode.js"};
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(err);
    
    CommandLineRunner runner = new CommandLineRunner(args, System.out, errStream);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testCommandLineRunnerInitializationHelp() throws Throwable {
    String[] args = new String[] {"--help"};
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(err);
    
    CommandLineRunner runner = new CommandLineRunner(args, System.out, errStream);
    assertFalse(runner.shouldRunCompiler());
  }

  @Test
  public void testCommandLineRunnerInitializationVersion() throws Throwable {
    String[] args = new String[] {"--version"};
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(err);
    
    CommandLineRunner runner = new CommandLineRunner(args, System.out, errStream);
    assertFalse(runner.shouldRunCompiler());
    String errOutput = err.toString();
    assertTrue(errOutput.contains("Closure Compiler"));
  }

  @Test
  public void testCreateOptions() throws Throwable {
    String[] args = new String[] {"--js", "testcode.js"};
    CommandLineRunner runner = new CommandLineRunner(args);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  @Test
  public void testCreateCompiler() throws Throwable {
    String[] args = new String[] {"--js", "testcode.js"};
    CommandLineRunner runner = new CommandLineRunner(args);
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }
}