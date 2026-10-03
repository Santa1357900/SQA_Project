package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class CommandLineRunnerTest {

  @Test
  public void testDefaultExterns() throws Throwable {
    List<JSSourceFile> externs = CommandLineRunner.getDefaultExterns();
    assertNotNull(externs);
    assertFalse(externs.isEmpty());
  }

  @Test
  public void testValidArguments() throws Throwable {
    String[] args = new String[] {
      "--js", "testcode.js"
    };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testHelpArgument() throws Throwable {
    String[] args = new String[] {
      "--help"
    };
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(errContent);
    
    CommandLineRunner runner = new CommandLineRunner(args, System.out, errStream);
    assertFalse(runner.shouldRunCompiler());
  }

  @Test
  public void testInvalidArguments() throws Throwable {
    String[] args = new String[] {
      "--jscomp_dev_mode", "INVALID_DEV_MODE"
    };
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(errContent);

    CommandLineRunner runner = new CommandLineRunner(args, System.out, errStream);
    assertFalse(runner.shouldRunCompiler());
  }

  @Test
  public void testArgPatternWithQuotes() throws Throwable {
    String[] args = new String[] {
      "--js_output_file=\"output.js\""
    };
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  @Test
  public void testBooleanOptionHandlerValues() throws Throwable {
    String[] argsTrue = new String[] {
      "--print_tree=on",
      "--print_ast=yes",
      "--compute_phase_ordering=1",
      "--third_party=true"
    };
    CommandLineRunner runnerTrue = new CommandLineRunner(argsTrue);
    assertTrue(runnerTrue.shouldRunCompiler());

    String[] argsFalse = new String[] {
      "--print_tree=off",
      "--print_ast=no",
      "--compute_phase_ordering=0",
      "--third_party=false"
    };
    CommandLineRunner runnerFalse = new CommandLineRunner(argsFalse);
    assertTrue(runnerFalse.shouldRunCompiler());
  }

  @Test
  public void testCreateOptionsAndCompiler() throws Throwable {
    String[] args = new String[] {
      "--js", "testcode.js",
      "--debug",
      "--formatting", "PRETTY_PRINT",
      "--formatting", "PRINT_INPUT_DELIMITER"
    };
    CommandLineRunner runner = new CommandLineRunner(args);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
    
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }
}