package com.google.javascript.jscomp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandLineRunnerClaudeTest {

  private PrintStream out;
  private PrintStream err;

  @Before
  public void setUp() throws Throwable {
    out = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
    err = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
  }

  // constructor: empty args -> no parse error -> config valid
  @Test
  public void testConstructor_emptyArgs_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // single-arg constructor (uses System.err internally) with empty args -> config valid
  @Test
  public void testConstructor_singleArgCtorEmptyArgs_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {});
    assertTrue(runner.shouldRunCompiler());
  }

  // --help sets display_help -> isConfigValid forced false
  @Test
  public void testConstructor_helpFlag_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--help"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // unknown flag -> CmdLineException caught -> isConfigValid false
  @Test
  public void testConstructor_unknownFlag_configInvalid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--no_such_flag_xyz"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // --js with space syntax -> valid
  @Test
  public void testConstructor_jsFlagSpaceSyntax_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--js", "a.js"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --js=value equals syntax, argPattern regex branch (matches) -> valid
  @Test
  public void testConstructor_jsFlagEqualsSyntax_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--js=a.js"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // equals syntax with quoted value -> quotesPattern matches -> quotes stripped -> valid
  @Test
  public void testConstructor_quotedValueEqualsSyntax_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--js_output_file='out.js'"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // equals syntax with unquoted value -> quotesPattern does not match -> value used as-is
  @Test
  public void testConstructor_unquotedValueEqualsSyntax_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--variable_map_output_file=map.out"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --js specified multiple times -> list accumulates, loop with >1 rounds -> valid
  @Test
  public void testConstructor_multipleJsFlags_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--js", "a.js", "--js", "b.js"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --externs specified multiple times -> valid
  @Test
  public void testConstructor_multipleExternsFlags_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--externs", "e1.js", "--externs", "e2.js"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --define with name=value format -> valid (no parse-time validation of content)
  @Test
  public void testConstructor_defineFlagWithValue_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--define", "FOO=true"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --module specified multiple times -> list accumulates -> valid
  @Test
  public void testConstructor_multipleModuleFlags_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--module", "m1:1:", "--module", "m2:1:m1"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --jscomp_error accepts arbitrary group name string -> valid
  @Test
  public void testConstructor_jscompErrorFlag_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--jscomp_error", "checkTypes"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --jscomp_warning accepts arbitrary group name string -> valid
  @Test
  public void testConstructor_jscompWarningFlag_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--jscomp_warning", "checkTypes"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --jscomp_off accepts arbitrary group name string -> valid
  @Test
  public void testConstructor_jscompOffFlag_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--jscomp_off", "checkTypes"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --summary_detail_level with valid int -> valid
  @Test
  public void testConstructor_summaryDetailLevelValidInt_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--summary_detail_level", "3"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --summary_detail_level with non-numeric value -> CmdLineException -> invalid
  @Test
  public void testConstructor_summaryDetailLevelInvalidInt_configInvalid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--summary_detail_level", "abc"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // --compilation_level with a valid enum constant -> valid
  @Test
  public void testConstructor_compilationLevelValidEnum_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--compilation_level", "ADVANCED_OPTIMIZATIONS"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --compilation_level with unknown enum constant -> CmdLineException -> invalid
  @Test
  public void testConstructor_compilationLevelInvalidEnum_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--compilation_level", "NOT_A_LEVEL"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // --warning_level with a valid enum constant -> valid
  @Test
  public void testConstructor_warningLevelValidEnum_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--warning_level", "VERBOSE"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --warning_level with unknown enum constant -> invalid
  @Test
  public void testConstructor_warningLevelInvalidEnum_configInvalid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--warning_level", "NOT_A_WLEVEL"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // --formatting with valid enum constant -> valid
  @Test
  public void testConstructor_formattingValidEnum_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--formatting", "PRETTY_PRINT"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // --formatting with unknown enum constant -> invalid
  @Test
  public void testConstructor_formattingInvalidEnum_configInvalid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--formatting", "NOT_A_FORMAT"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }



  // BooleanOptionHandler: explicit "true" keyword -> valid
  @Test
  public void testBooleanOptionHandler_trueKeyword_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--debug", "true"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // BooleanOptionHandler: "off" keyword maps to false -> valid
  @Test
  public void testBooleanOptionHandler_offKeyword_configValid() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--third_party", "off"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // BooleanOptionHandler: value is case-insensitive via toLowerCase() -> valid
  @Test
  public void testBooleanOptionHandler_caseInsensitiveKeyword_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--debug", "TRUE"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // BooleanOptionHandler: illegal boolean value -> CmdLineException -> invalid
  @Test
  public void testBooleanOptionHandler_illegalValue_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {"--debug", "maybe"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }

  // createOptions(): default process_closure_primitives is true per field default -> closurePass true
  @Test
  public void testCreateOptions_defaultProcessClosurePrimitives_closurePassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {}, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.closurePass);
  }

  // createOptions(): --process_closure_primitives=false -> closurePass false
  @Test
  public void testCreateOptions_processClosurePrimitivesFalse_closurePassFalse() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--process_closure_primitives", "false"}, out, err);
    CompilerOptions options = runner.createOptions();
    assertFalse(options.closurePass);
  }

  // createOptions(): no --formatting flags -> prettyPrint and printInputDelimiter remain false
  @Test
  public void testCreateOptions_noFormattingFlags_formattingDefaultsFalse() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {}, out, err);
    CompilerOptions options = runner.createOptions();
    assertFalse(options.prettyPrint);
    assertFalse(options.printInputDelimiter);
  }

  // createOptions(): --formatting=PRETTY_PRINT applies prettyPrint=true
  @Test
  public void testCreateOptions_formattingPrettyPrint_prettyPrintTrue() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--formatting", "PRETTY_PRINT"}, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
  }

  // createOptions(): --formatting=PRINT_INPUT_DELIMITER applies printInputDelimiter=true
  @Test
  public void testCreateOptions_formattingPrintInputDelimiter_delimiterTrue() throws Throwable {
    CommandLineRunner runner =
        new CommandLineRunner(new String[] {"--formatting", "PRINT_INPUT_DELIMITER"}, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.printInputDelimiter);
  }

  // createOptions(): two --formatting flags accumulate into list, loop runs 2 iterations, both applied
  @Test
  public void testCreateOptions_formattingBothOptions_bothTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {
        "--formatting", "PRETTY_PRINT", "--formatting", "PRINT_INPUT_DELIMITER"}, out, err);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
    assertTrue(options.printInputDelimiter);
  }

  // createCompiler(): returns a non-null Compiler instance
  @Test
  public void testCreateCompiler_returnsNonNullCompiler() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {}, out, err);
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }

  // createCompiler(): returned object is actually of declared return type Compiler
  @Test
  public void testCreateCompiler_returnsCompilerType() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[] {}, out, err);
    Object compiler = runner.createCompiler();
    assertTrue(compiler instanceof Compiler);
  }

  // shouldRunCompiler(): combination of two valid flags together remains valid
  @Test
  public void testShouldRunCompiler_multipleValidFlagsCombined_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--js", "a.js", "--warning_level", "QUIET", "--debug", "on"}, out, err);
    assertTrue(runner.shouldRunCompiler());
  }

  // shouldRunCompiler(): an invalid flag among otherwise valid flags still invalidates config
  @Test
  public void testShouldRunCompiler_oneInvalidFlagAmongValid_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[] {"--js", "a.js", "--warning_level", "BOGUS"}, out, err);
    assertFalse(runner.shouldRunCompiler());
  }
}
