package com.google.javascript.jscomp;

import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CommandLineRunnerTest {

  @Test
  public void testBooleanOptionHandlerTrueValues() throws Throwable {
    String[] args = new String[] { "--print_tree=true" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerOnValues() throws Throwable {
    String[] args = new String[] { "--print_tree=on" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerYesValues() throws Throwable {
    String[] args = new String[] { "--print_tree=yes" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerOneValues() throws Throwable {
    String[] args = new String[] { "--print_tree=1" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerFalseValues() throws Throwable {
    String[] args = new String[] { "--print_tree=false" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerOffValues() throws Throwable {
    String[] args = new String[] { "--print_tree=off" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerNoValues() throws Throwable {
    String[] args = new String[] { "--print_tree=no" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerZeroValues() throws Throwable {
    String[] args = new String[] { "--print_tree=0" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerDefaultWithoutValue() throws Throwable {
    String[] args = new String[] { "--print_tree" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testBooleanOptionHandlerInvalidValue() throws Throwable {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(errContent);
    String[] args = new String[] { "--print_tree=invalid_bool" };
    
    try {
      new CommandLineRunner(args, System.out, errStream);
      fail("Expected CmdLineException");
    } catch (CmdLineException e) {
      assertTrue(e.getMessage().contains("Illegal boolean value"));
    }
  }

  @Test
  public void testFormattingOptionsPrettyPrint() throws Throwable {
    String[] args = new String[] { "--formatting=PRETTY_PRINT" };
    CommandLineRunner runner = new CommandLineRunner(args);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
  }

  @Test
  public void testFormattingOptionsPrintInputDelimiter() throws Throwable {
    String[] args = new String[] { "--formatting=PRINT_INPUT_DELIMITER" };
    CommandLineRunner runner = new CommandLineRunner(args);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.printInputDelimiter);
  }

  @Test
  public void testArgPatternMatchingWithQuotes() throws Throwable {
    String[] args = new String[] { "--js_output_file=\"output.js\"" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testArgPatternMatchingWithoutQuotes() throws Throwable {
    String[] args = new String[] { "--js_output_file=output.js" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertNotNull(runner);
  }

  @Test
  public void testDebugAndCompilationLevels() throws Throwable {
    String[] args = new String[] {
      "--compilation_level=ADVANCED_OPTIMIZATIONS",
      "--debug=true",
      "--warning_level=VERBOSE",
      "--process_closure_primitives=true",
      "--use_only_custom_externs=true"
    };
    CommandLineRunner runner = new CommandLineRunner(args);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  @Test
  public void testCreateCompiler() throws Throwable {
    String[] args = new String[] {};
    CommandLineRunner runner = new CommandLineRunner(args);
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }

  @Test
  public void testCreateExternsCustomOnly() throws Throwable {
    String[] args = new String[] { "--use_only_custom_externs=true" };
    CommandLineRunner runner = new CommandLineRunner(args);
    List<JSSourceFile> externs = runner.createExterns();
    assertNotNull(externs);
  }

  @Test
  public void testMainMethodHelpExecution() throws Throwable {
    String[] args = new String[] { "--help" };
    try {
      CommandLineRunner.main(args);
    } catch (SecurityException e) {
      // Expected if System.exit is called or handled
    }
  }
}