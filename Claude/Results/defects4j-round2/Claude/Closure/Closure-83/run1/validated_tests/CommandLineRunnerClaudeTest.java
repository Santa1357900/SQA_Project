package com.google.javascript.jscomp;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class CommandLineRunnerClaudeTest {

  private ByteArrayOutputStream outBytes;
  private ByteArrayOutputStream errBytes;
  private PrintStream out;
  private PrintStream err;

  @Before
  public void setUp() throws Throwable {
    outBytes = new ByteArrayOutputStream();
    errBytes = new ByteArrayOutputStream();
    out = new PrintStream(outBytes);
    err = new PrintStream(errBytes);
  }

  // Covers: successful args4j parsing -> isConfigValid stays true
  @Test
  public void testConstructor_validArgs_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--js", "test.js"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: CmdLineException branch -> isConfigValid set false
  @Test
  public void testConstructor_unknownFlag_shouldRunCompilerFalse() throws Throwable {
    String[] args = {"--this_flag_does_not_exist"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // Covers: display_help branch forces isConfigValid false
  @Test
  public void testConstructor_helpFlag_shouldRunCompilerFalse() throws Throwable {
    String[] args = {"--help"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // Covers: empty args array -> default flags are valid
  @Test
  public void testConstructor_emptyArgs_shouldRunCompilerTrue() throws Throwable {
    String[] args = {};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: single-arg constructor overload (uses System.err internally)
  @Test
  public void testConstructor_singleArgOverload_validArgs_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--js", "a.js"};
    CommandLineRunner runner = new CommandLineRunner(args);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: equals-syntax argument regex branch (argPattern matches)
  @Test
  public void testConstructor_equalsSyntaxArg_parsedSuccessfully() throws Throwable {
    String[] args = {"--js_output_file=out.js"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: quoted value branch in equals-syntax parsing (quotesPattern matches)
  @Test
  public void testConstructor_quotedEqualsSyntaxArg_parsedSuccessfully() throws Throwable {
    String[] args = {"--js_output_file='out.js'"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: BooleanOptionHandler explicit "=true" value branch
  @Test
  public void testConstructor_booleanFlagEqualsTrue_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--debug=true"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: BooleanOptionHandler explicit "=false" value branch
  @Test
  public void testConstructor_booleanFlagEqualsFalse_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--debug=false"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: repeated --js flag loop with multiple elements
  @Test
  public void testConstructor_multipleJsFlags_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--js", "a.js", "--js", "b.js"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: closure_entry_point list option parsing path
  @Test
  public void testConstructor_closureEntryPointFlag_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--closure_entry_point", "goog.foo"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: default (third_party=false) createOptions uses ClosureCodingConvention
  @Test
  public void testCreateOptions_defaultThirdParty_usesClosureCodingConvention() throws Throwable {
    String[] args = {};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertEquals(ClosureCodingConvention.class, options.getCodingConvention().getClass());
  }



  // Covers: explicit --third_party=false keeps ClosureCodingConvention
  @Test
  public void testCreateOptions_thirdPartyExplicitFalse_usesClosureCodingConvention() throws Throwable {
    String[] args = {"--third_party=false"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertEquals(ClosureCodingConvention.class, options.getCodingConvention().getClass());
  }

  // Covers: empty formatting list -> prettyPrint stays false
  @Test
  public void testCreateOptions_defaultFormatting_prettyPrintFalse() throws Throwable {
    String[] args = {};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertFalse(options.prettyPrint);
  }

  // Covers: --formatting PRETTY_PRINT sets prettyPrint true
  @Test
  public void testCreateOptions_formattingPrettyPrint_setsPrettyPrintTrue() throws Throwable {
    String[] args = {"--formatting", "PRETTY_PRINT"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
  }

  // Covers: --formatting PRINT_INPUT_DELIMITER sets printInputDelimiter true
  @Test
  public void testCreateOptions_formattingPrintInputDelimiter_setsFlagTrue() throws Throwable {
    String[] args = {"--formatting", "PRINT_INPUT_DELIMITER"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.printInputDelimiter);
  }

  // Covers: loop over multiple formatting options, both branches applied
  @Test
  public void testCreateOptions_multipleFormattingOptions_bothApplied() throws Throwable {
    String[] args = {"--formatting", "PRETTY_PRINT", "--formatting", "PRINT_INPUT_DELIMITER"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
    assertTrue(options.printInputDelimiter);
  }

  // Covers: default process_closure_primitives=true -> closurePass true
  @Test
  public void testCreateOptions_defaultProcessClosurePrimitives_closurePassTrue() throws Throwable {
    String[] args = {};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.closurePass);
  }

  // Covers: --process_closure_primitives=false -> closurePass false
  @Test
  public void testCreateOptions_processClosurePrimitivesFalse_closurePassFalse() throws Throwable {
    String[] args = {"--process_closure_primitives=false"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertFalse(options.closurePass);
  }

  // Covers: debug flag true triggers setDebugOptionsForCompilationLevel branch
  @Test
  public void testCreateOptions_debugFlagTrue_returnsNonNullOptions() throws Throwable {
    String[] args = {"--debug"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  // Covers: compilation_level WHITESPACE_ONLY enum branch
  @Test
  public void testCreateOptions_compilationLevelWhitespaceOnly_returnsNonNullOptions() throws Throwable {
    String[] args = {"--compilation_level", "WHITESPACE_ONLY"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  // Covers: compilation_level ADVANCED_OPTIMIZATIONS enum branch
  @Test
  public void testCreateOptions_compilationLevelAdvanced_returnsNonNullOptions() throws Throwable {
    String[] args = {"--compilation_level", "ADVANCED_OPTIMIZATIONS"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  // Covers: warning_level VERBOSE enum branch
  @Test
  public void testCreateOptions_warningLevelVerbose_returnsNonNullOptions() throws Throwable {
    String[] args = {"--warning_level", "VERBOSE"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  // Covers: warning_level QUIET enum branch
  @Test
  public void testCreateOptions_warningLevelQuiet_returnsNonNullOptions() throws Throwable {
    String[] args = {"--warning_level", "QUIET"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    CompilerOptions options = runner.createOptions();
    assertNotNull(options);
  }

  // Covers: createCompiler returns a usable Compiler instance
  @Test
  public void testCreateCompiler_returnsCompilerInstance() throws Throwable {
    String[] args = {};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }





  // Covers: combination of a valid flag followed by an invalid flag -> parse fails
  @Test
  public void testConstructor_mixedValidAndInvalidFlags_shouldRunCompilerFalse() throws Throwable {
    String[] args = {"--js", "a.js", "--not_a_real_flag"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // Covers: no --js flag given, other flags present, still valid config
  @Test
  public void testConstructor_noJsFlag_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--compilation_level", "SIMPLE_OPTIMIZATIONS"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: --define list option with name=value token parses successfully
  @Test
  public void testConstructor_defineFlag_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--define", "FOO=true"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // Covers: --module_wrapper list option parses successfully
  @Test
  public void testConstructor_moduleWrapperFlag_shouldRunCompilerTrue() throws Throwable {
    String[] args = {"--module_wrapper", "m1:%s"};
    CommandLineRunner runner = new CommandLineRunner(args, out, err);
    assertTrue(runner.shouldRunCompiler());
  }
}
