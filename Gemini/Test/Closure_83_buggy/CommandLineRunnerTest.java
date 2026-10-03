package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class CommandLineRunnerTest {

  @Test
  public void testHelpOption() throws Throwable {
    String[] args = new String[] { "--help" };
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes);
    CommandLineRunner runner = new CommandLineRunner(args, System.out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  @Test
  public void testVersionOption() throws Throwable {
    String[] args = new String[] { "--version" };
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes);
    CommandLineRunner runner = new CommandLineRunner(args, System.out, err);
    assertFalse(runner.shouldRunCompiler());
    String output = errBytes.toString();
    assertTrue(output.contains("Closure Compiler"));
  }

  @Test
  public void testValidArguments() throws Throwable {
    String[] args = new String[] { "--js", "test.js" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
    
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);

    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }

  @Test
  public void testArgumentsWithEqualsAndQuotes() throws Throwable {
    String[] args = new String[] { "--js_output_file='output.js'", "--warning_level=VERBOSE" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testBooleanOptionHandlerTrueValues() throws Throwable {
    String[] args = new String[] { "--print_tree=true", "--print_ast=on", "--print_pass_graph=yes", "--compute_phase_ordering=1" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testBooleanOptionHandlerFalseValues() throws Throwable {
    String[] args = new String[] { "--print_tree=false", "--print_ast=off", "--print_pass_graph=no", "--compute_phase_ordering=0" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testBooleanOptionHandlerFallback() throws Throwable {
    String[] args = new String[] { "--print_tree=something_else" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testFormattingOptions() throws Throwable {
    String[] args = new String[] { "--formatting=PRETTY_PRINT", "--formatting=PRINT_INPUT_DELIMITER" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
    
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
    assertTrue(options.printInputDelimiter);
  }

  @Test
  public void testThirdPartyCodingConvention() throws Throwable {
    String[] args = new String[] { "--third_party=true" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testDebugAndCompilationLevels() throws Throwable {
    String[] args = new String[] { "--compilation_level=ADVANCED_OPTIMIZATIONS", "--debug=true", "--warning_level=QUIET" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
    
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  @Test
  public void testDefaultExterns() throws Throwable {
    List<JSSourceFile> externs = CommandLineRunner.getDefaultExterns();
    assertNotNull(externs);
    assertFalse(externs.isEmpty());
  }

  @Test
  public void testCreateExternsWithCustomOnly() throws Throwable {
    String[] args = new String[] { "--use_only_custom_externs=true" };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
    List<JSSourceFile> externs = runner.createExterns();
    assertNotNull(externs);
  }
}