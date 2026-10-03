package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class CommandLineRunnerClaudeTest {

  // Covers: constructor parses a single --js flag without error (0->1 iteration list branch)
  @Test
  public void testConstructor_minimalJsFlag_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--js", "test.js"});
    assertNotNull(runner);
  }

  // Covers: constructor with zero arguments, all flags use defaults (0-iteration loop over args)
  @Test
  public void testConstructor_emptyArgs_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    assertNotNull(runner);
  }

  // Covers: multiple --js occurrences building a list with more than one entry
  @Test
  public void testConstructor_multipleJsFlags_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--js", "a.js", "--js", "b.js"});
    assertNotNull(runner);
  }

  // Covers: multiple --externs occurrences building list with more than one entry
  @Test
  public void testConstructor_multipleExternsFlags_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--externs", "e1.js", "--externs", "e2.js"});
    assertNotNull(runner);
  }

  // Covers: argPattern match plus quotesPattern matching branch (quotes stripped)
  @Test
  public void testConstructor_quotedValueEqualsFormat_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--js_output_file=\"out.js\""});
    assertNotNull(runner);
  }

  // Covers: argPattern match, quotesPattern not matching branch (value kept as-is)
  @Test
  public void testConstructor_unquotedValueEqualsFormat_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--js_output_file=out.js"});
    assertNotNull(runner);
  }

  // Covers: --D long alias recognized for --define list option
  @Test
  public void testConstructor_defineLongAlias_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--D", "FOO=true"});
    assertNotNull(runner);
  }

  // Covers: -D short single-dash alias, not matched by argPattern, passed through as-is
  @Test
  public void testConstructor_defineShortDashAlias_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"-D", "BAR=1"});
    assertNotNull(runner);
  }

  // Covers: --module list option with a single entry
  @Test
  public void testConstructor_moduleFlagSingleEntry_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--module", "mod1:1"});
    assertNotNull(runner);
  }

  // Covers: --jscomp_error, --jscomp_warning, --jscomp_off list options
  @Test
  public void testConstructor_jscompErrorWarningOffFlags_doesNotThrow() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {
        "--jscomp_error", "checkTypes",
        "--jscomp_warning", "checkVars",
        "--jscomp_off", "globalThis"});
    assertNotNull(runner);
  }

  // Covers: BooleanOptionHandler throws CmdLineException for unrecognized boolean token
  @Test
  public void testConstructor_invalidBooleanForDebug_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--debug", "maybe"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertTrue(expected.getMessage().contains("Illegal boolean value"));
    }
  }

  // Covers: args4j enum parsing failure branch for --compilation_level
  @Test
  public void testConstructor_invalidCompilationLevel_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--compilation_level", "NOT_A_LEVEL"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  // Covers: args4j enum parsing failure branch for --warning_level
  @Test
  public void testConstructor_invalidWarningLevel_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--warning_level", "NOT_A_LEVEL"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  // Covers: args4j enum parsing failure branch for --jscomp_dev_mode
  @Test
  public void testConstructor_invalidDevMode_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--jscomp_dev_mode", "BOGUS"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  // Covers: args4j int parsing failure branch for --summary_detail_level
  @Test
  public void testConstructor_nonNumericSummaryDetailLevel_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--summary_detail_level", "abc"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  // Covers: args4j enum parsing failure branch for --formatting list option
  @Test
  public void testConstructor_invalidFormattingOption_throwsCmdLineException() throws Throwable {
    try {
      new CommandLineRunner(new String[] {"--formatting", "BOGUS"});
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  // Covers: three-arg constructor delegating to super(out, err) and successful parse
  @Test
  public void testConstructorWithStreams_validArgs_doesNotThrow() throws Throwable {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--js", "a.js"},
        new PrintStream(outBuf), new PrintStream(errBuf));
    assertNotNull(runner);
  }

  // Covers: catch block in initConfigFromFlags writes message and usage to provided err stream
  @Test
  public void testConstructorWithStreams_invalidBooleanPrintTree_throwsAndWritesMessageToErrStream()
      throws Throwable {
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuf, true);
    try {
      new CommandLineRunner(new String[] {"--print_tree", "notabool"}, System.out, err);
      fail("expected CmdLineException");
    } catch (CmdLineException expected) {
      assertTrue(errBuf.toString().contains("Illegal boolean value"));
    }
  }

  // Covers: default process_closure_primitives=true branch -> options.closurePass true
  @Test
  public void testCreateOptions_defaultProcessClosurePrimitives_closurePassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    CompilerOptions options = runner.createOptions();
    assertTrue(options.closurePass);
  }

  // Covers: explicit --process_closure_primitives true token via BooleanOptionHandler
  @Test
  public void testCreateOptions_processClosurePrimitivesExplicitTrue_closurePassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--process_closure_primitives", "true"});
    CompilerOptions options = runner.createOptions();
    assertTrue(options.closurePass);
  }

  // Covers: --process_closure_primitives false must disable goog.require/provide processing
  @Test
  public void testCreateOptions_processClosurePrimitivesFalse_closurePassFalse() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--process_closure_primitives", "false"});
    CompilerOptions options = runner.createOptions();
    assertFalse(options.closurePass);
  }

  // Covers: FormattingOption.PRETTY_PRINT case branch in applyToOptions switch
  @Test
  public void testCreateOptions_formattingPrettyPrint_setsPrettyPrintTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--formatting", "PRETTY_PRINT"});
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
  }

  // Covers: FormattingOption.PRINT_INPUT_DELIMITER case branch in applyToOptions switch
  @Test
  public void testCreateOptions_formattingPrintInputDelimiter_setsPrintInputDelimiterTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--formatting", "PRINT_INPUT_DELIMITER"});
    CompilerOptions options = runner.createOptions();
    assertTrue(options.printInputDelimiter);
  }

  // Covers: for-loop over flags.formatting iterating more than once
  @Test
  public void testCreateOptions_multipleFormattingOptions_setsBothTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {
        "--formatting", "PRETTY_PRINT", "--formatting", "PRINT_INPUT_DELIMITER"});
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
    assertTrue(options.printInputDelimiter);
  }

  // Covers: for-loop over empty flags.formatting list (zero iterations)
  @Test
  public void testCreateOptions_noFormattingOption_prettyPrintDefaultFalse() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    CompilerOptions options = runner.createOptions();
    assertFalse(options.prettyPrint);
  }

  // Covers: createOptions always constructs a new CompilerOptions instance per call
  @Test
  public void testCreateOptions_calledTwice_returnsDistinctInstances() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    CompilerOptions options1 = runner.createOptions();
    CompilerOptions options2 = runner.createOptions();
    assertNotSame(options1, options2);
  }

  // Covers: createCompiler constructs a new Compiler using the error print stream
  @Test
  public void testCreateCompiler_returnsNonNullInstance() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }

  // Covers: each call to createCompiler returns a fresh Compiler instance
  @Test
  public void testCreateCompiler_calledTwice_returnsDistinctInstances() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    Compiler compiler1 = runner.createCompiler();
    Compiler compiler2 = runner.createCompiler();
    assertNotSame(compiler1, compiler2);
  }
}
