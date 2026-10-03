package com.google.javascript.jscomp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandLineRunnerClaudeTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream outPS;
  private PrintStream errPS;

  @Before
  public void setUp() throws Throwable {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    outPS = new PrintStream(outContent);
    errPS = new PrintStream(errContent);
  }

  // Tests single-arg protected constructor with empty args: default flags are valid
  @Test
  public void testConstructorSingleArg_emptyArgs_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{});
    assertTrue(runner.shouldRunCompiler());
  }

  // Tests two-arg constructor with empty args: default flags are valid
  @Test
  public void testConstructorTwoArg_emptyArgs_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }

  // Branch: displayHelp true -> isConfigValid set false, usage printed
  @Test
  public void testConstructor_helpFlag_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{"--help"}, outPS, errPS);
    assertFalse(runner.shouldRunCompiler());
    assertTrue(errContent.size() > 0);
  }

  // Branch: unknown flag triggers CmdLineException caught -> config invalid, message printed
  @Test
  public void testConstructor_unknownFlag_cmdLineExceptionCaught_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--totally_unknown_flag_xyz"}, outPS, errPS);
    assertFalse(runner.shouldRunCompiler());
    assertTrue(errContent.size() > 0);
  }

  // Branch: --flagfile pointing to a nonexistent file triggers IOException caught -> config invalid
  @Test
  public void testConstructor_flagFileMissing_ioExceptionCaught_configInvalid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--flagfile", "/nonexistent/path/flags.txt"}, outPS, errPS);
    assertFalse(runner.shouldRunCompiler());
  }

  // Branch: --version flag prints version info to err, config remains valid
  @Test
  public void testConstructor_versionFlag_printsVersionInfo_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{"--version"}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
    assertTrue(errContent.toString().contains("Version"));
  }

  // Bug check: processCommonJsModules without entry module must not crash, must invalidate config
  // and print the documented error message, per the contract in initConfigFromFlags.
  @Test
  public void testConstructor_commonJsModulesWithoutEntryModule_configInvalidNoCrash() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--process_common_js_modules"}, outPS, errPS);
    assertFalse(runner.shouldRunCompiler());
    assertTrue(errContent.toString().contains("common_js_entry_module"));
  }

  // Branch: processCommonJsModules with entry module specified -> no crash, config valid
  @Test
  public void testConstructor_commonJsModulesWithEntryModule_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--process_common_js_modules", "--common_js_entry_module", "main"},
        outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }

  // Branch: --jscomp_error with a group name is accepted at parse time, config remains valid
  @Test
  public void testConstructor_jscompErrorFlag_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--jscomp_error", "checkTypes"}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }

  // BooleanOptionHandler: explicit "false" value parsed correctly (param matches FALSES set)
  @Test
  public void testConstructor_booleanFlagExplicitFalse_parsedCorrectly() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--use_only_custom_externs=false"}, outPS, errPS);
    List<SourceFile> externs = runner.createExterns();
    assertFalse(externs.isEmpty());
  }

  // BooleanOptionHandler: explicit "yes" value parsed as true (param matches TRUES set)
  @Test
  public void testConstructor_booleanFlagExplicitYes_parsedAsTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--use_only_custom_externs=yes"}, outPS, errPS);
    List<SourceFile> externs = runner.createExterns();
    assertTrue(externs.isEmpty());
  }

  // BooleanOptionHandler: unrecognized value falls back to true, config still valid
  @Test
  public void testConstructor_booleanFlagUnrecognizedValue_defaultsTrueConfigValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--use_only_custom_externs=banana"}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }

  // Branch: default flags -> closurePass true (default processClosurePrimitives=true)
  @Test
  public void testCreateOptions_defaultFlags_setsClosurePassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.closurePass);
    assertFalse(options.jqueryPass);
    assertFalse(options.angularPass);
  }

  // Branch: --formatting PRETTY_PRINT -> options.prettyPrint true (1 loop iteration)
  @Test
  public void testCreateOptions_prettyPrintFormatting_setsPrettyPrintTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--formatting", "PRETTY_PRINT"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
  }

  // Branch: --formatting PRINT_INPUT_DELIMITER -> options.printInputDelimiter true
  @Test
  public void testCreateOptions_printInputDelimiterFormatting_setsFlagTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--formatting", "PRINT_INPUT_DELIMITER"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.printInputDelimiter);
  }

  // Branch: multiple --formatting flags -> loop runs more than once, both options applied
  @Test
  public void testCreateOptions_multipleFormattingFlags_appliesBoth() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--formatting", "PRETTY_PRINT", "--formatting", "PRINT_INPUT_DELIMITER"},
        outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.prettyPrint);
    assertTrue(options.printInputDelimiter);
  }

  // Branch: ADVANCED_OPTIMIZATIONS with no translations file -> messageBundle is EmptyMessageBundle
  @Test
  public void testCreateOptions_advancedOptimizationsNoTranslations_setsEmptyMessageBundle() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--compilation_level", "ADVANCED_OPTIMIZATIONS"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.messageBundle instanceof EmptyMessageBundle);
  }

  // Branch: jquery primitives + ADVANCED_OPTIMIZATIONS -> jqueryPass true
  @Test
  public void testCreateOptions_jqueryPrimitivesWithAdvanced_setsJqueryPassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--compilation_level", "ADVANCED_OPTIMIZATIONS",
            "--process_jquery_primitives"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.jqueryPass);
  }

  // Branch: jquery primitives without ADVANCED level -> jqueryPass remains false
  @Test
  public void testCreateOptions_jqueryPrimitivesWithoutAdvanced_jqueryPassFalse() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--process_jquery_primitives"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertFalse(options.jqueryPass);
  }

  // Branch: --angular_pass flag -> options.angularPass true
  @Test
  public void testCreateOptions_angularPassFlag_setsAngularPassTrue() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--angular_pass"}, outPS, errPS);
    CompilerOptions options = runner.createOptions();
    assertTrue(options.angularPass);
  }

  // Branch: invalid translations file path -> IOException wrapped and rethrown as RuntimeException
  @Test
  public void testCreateOptions_invalidTranslationsFile_throwsRuntimeException() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--translations_file", "/nonexistent/path/file.xtb"}, outPS, errPS);
    try {
      runner.createOptions();
      fail("expected RuntimeException due to missing translations file");
    } catch (RuntimeException expected) {
      // expected
    }
  }

  // Tests createCompiler() returns a usable, non-null Compiler instance
  @Test
  public void testCreateCompiler_returnsNonNullCompilerInstance() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{}, outPS, errPS);
    Compiler compiler = runner.createCompiler();
    assertNotNull(compiler);
  }

  // Branch: useOnlyCustomExterns true -> createExterns() returns only user externs (empty here)
  @Test
  public void testCreateExterns_useOnlyCustomExterns_returnsEmptyList() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--use_only_custom_externs"}, outPS, errPS);
    List<SourceFile> externs = runner.createExterns();
    assertNotNull(externs);
    assertTrue(externs.isEmpty());
  }

  // Branch: default flags -> createExterns() includes default externs (non-empty)
  @Test
  public void testCreateExterns_defaultExterns_includesDefaultExternsNonEmpty() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{}, outPS, errPS);
    List<SourceFile> externs = runner.createExterns();
    assertNotNull(externs);
    assertFalse(externs.isEmpty());
  }

  // Tests static getDefaultExterns() returns the hard-coded non-empty default externs list
  @Test
  public void testGetDefaultExterns_returnsNonEmptyList() throws Throwable {
    List<SourceFile> externs = CommandLineRunner.getDefaultExterns();
    assertNotNull(externs);
    assertFalse(externs.isEmpty());
  }

  // Branch: --third_party flag selects CodingConventions.getDefault(), config stays valid
  @Test
  public void testConstructor_thirdPartyFlag_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(new String[]{"--third_party"}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }

  // Branch: --process_jquery_primitives alone selects JqueryCodingConvention, config stays valid
  @Test
  public void testConstructor_jqueryPrimitivesFlag_configValid() throws Throwable {
    CommandLineRunner runner = new CommandLineRunner(
        new String[]{"--process_jquery_primitives"}, outPS, errPS);
    assertTrue(runner.shouldRunCompiler());
  }
}
