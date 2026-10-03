package com.google.javascript.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.javascript.jscomp.AbstractCommandLineRunner.FlagUsageException;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbstractCommandLineRunnerClaudeTest {

  private static class TestRunner extends AbstractCommandLineRunner<Compiler, CompilerOptions> {
    TestRunner() {
      super();
    }
    TestRunner(PrintStream out, PrintStream err) {
      super(out, err);
    }
    protected Compiler createCompiler() {
      return new Compiler();
    }
    protected CompilerOptions createOptions() {
      return new CompilerOptions();
    }
  }

  private TestRunner runner;

  @Before
  public void setUp() throws Throwable {
    runner = new TestRunner();
  }

  // isInTestMode(): default state before enableTestMode is false
  @Test
  public void testIsInTestMode_defaultFalse_returnsFalse() throws Throwable {
    assertFalse(runner.isInTestMode());
  }

  // enableTestMode(): valid args (inputsSupplier xor modulesSupplier) sets test mode true
  @Test
  public void testEnableTestMode_validArgs_setsTestModeTrue() throws Throwable {
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { return true; }
    };
    runner.enableTestMode(externs, inputs, null, exitReceiver);
    assertTrue(runner.isInTestMode());
  }

  // enableTestMode(): inputsSupplier and modulesSupplier both null violates xor precondition
  @Test
  public void testEnableTestMode_bothNull_throwsIllegalArgumentException() throws Throwable {
    try {
      runner.enableTestMode(null, null, null, null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // enableTestMode(): inputsSupplier and modulesSupplier both non-null violates xor precondition
  @Test
  public void testEnableTestMode_bothNonNull_throwsIllegalArgumentException() throws Throwable {
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSModule>> modules = new Supplier<List<JSModule>>() {
      public List<JSModule> get() { return new ArrayList<JSModule>(); }
    };
    try {
      runner.enableTestMode(null, inputs, modules, null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // getCommandLineConfig(): always returns a non-null config instance
  @Test
  public void testGetCommandLineConfig_returnsNonNullConfig() throws Throwable {
    assertNotNull(runner.getCommandLineConfig());
  }

  // initOptionsFromFlags(): deprecated no-op, options must remain unchanged
  @Test
  public void testInitOptionsFromFlags_noop_optionsUnchanged() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    String before = options.jsOutputFile;
    runner.initOptionsFromFlags(options);
    assertEquals(before, options.jsOutputFile);
  }

  // setRunOptions(): unknown languageIn value throws FlagUsageException("Unknown language...")
  @Test
  public void testSetRunOptions_invalidLanguageIn_throwsFlagUsageException() throws Throwable {
    runner.getCommandLineConfig().setLanguageIn("INVALID_LANG");
    CompilerOptions options = new CompilerOptions();
    try {
      runner.setRunOptions(options);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Unknown language"));
    }
  }

  // setRunOptions(): default empty charset config results in US-ASCII output charset
  @Test
  public void testSetRunOptions_defaultCharset_setsOutputCharsetUSASCII() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    runner.setRunOptions(options);
    assertEquals("US-ASCII", options.outputCharset);
  }

  // setRunOptions(): explicit charset flag is reused verbatim as the output charset
  @Test
  public void testSetRunOptions_explicitCharsetConfigured_usesSameForOutput() throws Throwable {
    runner.getCommandLineConfig().setCharset("UTF-8");
    CompilerOptions options = new CompilerOptions();
    runner.setRunOptions(options);
    assertEquals("UTF-8", options.outputCharset);
  }

  // setRunOptions(): unsupported charset name throws FlagUsageException
  @Test
  public void testSetRunOptions_unsupportedCharset_throwsFlagUsageException() throws Throwable {
    runner.getCommandLineConfig().setCharset("NOT-A-REAL-CHARSET");
    CompilerOptions options = new CompilerOptions();
    try {
      runner.setRunOptions(options);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("not a valid charset"));
    }
  }

  // setRunOptions(): configured jsOutputFile is copied onto the options object
  @Test
  public void testSetRunOptions_jsOutputFileConfigured_setsOptionsField() throws Throwable {
    runner.getCommandLineConfig().setJsOutputFile("out.js");
    CompilerOptions options = new CompilerOptions();
    runner.setRunOptions(options);
    assertEquals("out.js", options.jsOutputFile);
  }

  // getCompiler(): before any run/doRun call, compiler field is null
  @Test
  public void testGetCompiler_beforeRun_returnsNull() throws Throwable {
    assertNull(runner.getCompiler());
  }

  // getCompiler(): after a successful doRun(), returns the non-null compiler instance used
  @Test
  public void testGetCompiler_afterDoRun_returnsNonNullCompiler() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream outStream = new PrintStream(baos);
    TestRunner r = new TestRunner(outStream, System.err);
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() {
        List<JSSourceFile> list = new ArrayList<JSSourceFile>();
        list.add(JSSourceFile.fromCode("test.js", "var x = 1;"));
        return list;
      }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { return true; }
    };
    r.enableTestMode(externs, inputs, null, exitReceiver);
    r.doRun();
    assertNotNull(r.getCompiler());
  }

  // run(): successful compile of valid JS in test mode yields exit code 0 via receiver
  @Test
  public void testRun_testModeValidJs_exitCodeZero() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream outStream = new PrintStream(baos);
    TestRunner r = new TestRunner(outStream, System.err);
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() {
        List<JSSourceFile> list = new ArrayList<JSSourceFile>();
        list.add(JSSourceFile.fromCode("test.js", "var x = 1;"));
        return list;
      }
    };
    final int[] captured = new int[] { 999 };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { captured[0] = input.intValue(); return true; }
    };
    r.enableTestMode(externs, inputs, null, exitReceiver);
    r.run();
    assertEquals(0, captured[0]);
  }

  // run(): a FlagUsageException from setRunOptions is caught, yielding exit code -1 via receiver
  @Test
  public void testRun_flagUsageException_exitCodeNegativeOne() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream outStream = new PrintStream(baos);
    TestRunner r = new TestRunner(outStream, System.err);
    r.getCommandLineConfig().setLanguageIn("BOGUS");
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    final int[] captured = new int[] { 999 };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { captured[0] = input.intValue(); return true; }
    };
    r.enableTestMode(externs, inputs, null, exitReceiver);
    r.run();
    assertEquals(-1, captured[0]);
  }

  // getErrorPrintStream(): returns the exact err stream passed to the constructor
  @Test
  public void testGetErrorPrintStream_returnsConstructorErr() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(baos);
    TestRunner r = new TestRunner(System.out, err);
    assertSame(err, r.getErrorPrintStream());
  }

  // FlagUsageException: constructor stores the message, retrievable via getMessage()
  @Test
  public void testFlagUsageException_constructor_storesMessage() throws Throwable {
    FlagUsageException e = new FlagUsageException("bad flag");
    assertEquals("bad flag", e.getMessage());
  }

  // createInputs(): empty files list returns an empty list for either allowStdIn value
  @Test
  public void testCreateInputs_emptyList_returnsEmptyList() throws Throwable {
    List<String> files = new ArrayList<String>();
    List<JSSourceFile> result = runner.createInputs(files, true);
    assertTrue(result.isEmpty());
  }

  // createInputs(): "-" with allowStdIn=false throws FlagUsageException mentioning stdin
  @Test
  public void testCreateInputs_stdinNotAllowed_throwsFlagUsageException() throws Throwable {
    List<String> files = new ArrayList<String>();
    files.add("-");
    try {
      runner.createInputs(files, false);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("stdin"));
    }
  }

  // createInputs(): "-" specified twice with allowStdIn=true throws FlagUsageException (twice)
  @Test
  public void testCreateInputs_stdinTwice_throwsFlagUsageException() throws Throwable {
    List<String> files = new ArrayList<String>();
    files.add("-");
    files.add("-");
    try {
      runner.createInputs(files, true);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("twice"));
    }
  }

  // createJsModules(): in test mode, returns exactly the modules supplier's list
  @Test
  public void testCreateJsModules_testMode_returnsSupplierList() throws Throwable {
    final List<JSModule> expected = new ArrayList<JSModule>();
    expected.add(new JSModule("m1"));
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSModule>> modules = new Supplier<List<JSModule>>() {
      public List<JSModule> get() { return expected; }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { return true; }
    };
    runner.enableTestMode(externs, null, modules, exitReceiver);
    assertSame(expected, runner.createJsModules(new ArrayList<String>(), new ArrayList<String>()));
  }

  // createJsModules(): single module spec requiring zero js files is created successfully
  @Test
  public void testCreateJsModules_zeroFileModule_createsModule() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:0");
    List<String> jsFiles = new ArrayList<String>();
    List<JSModule> result = runner.createJsModules(specs, jsFiles);
    assertEquals(1, result.size());
    assertEquals("mod1", result.get(0).getName());
  }

  // createJsModules(): empty specs list violates precondition, throws IllegalStateException
  @Test
  public void testCreateJsModules_emptySpecs_throwsIllegalStateException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // createJsModules(): spec with fewer than 2 colon-delimited parts throws FlagUsageException
  @Test
  public void testCreateJsModules_tooFewParts_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1only");
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("colon-delimited"));
    }
  }

  // createJsModules(): non-numeric file count throws FlagUsageException("Invalid js file count")
  @Test
  public void testCreateJsModules_nonNumericFileCount_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:abc");
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Invalid js file count"));
    }
  }

  // createJsModules(): requesting more files than available throws FlagUsageException
  @Test
  public void testCreateJsModules_notEnoughFiles_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:2");
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Not enough js files"));
    }
  }

  // createJsModules(): duplicate module names throw FlagUsageException
  @Test
  public void testCreateJsModules_duplicateName_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:0");
    specs.add("mod1:0");
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Duplicate module name"));
    }
  }

  // createJsModules(): dependency on unknown module throws FlagUsageException
  @Test
  public void testCreateJsModules_unknownDependency_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:0:unknownDep");
    List<String> jsFiles = new ArrayList<String>();
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("depends on unknown module"));
    }
  }

  // createJsModules(): leftover unconsumed js files throws FlagUsageException("Too many js files")
  @Test
  public void testCreateJsModules_tooManyFiles_throwsFlagUsageException() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:0");
    List<String> jsFiles = new ArrayList<String>();
    jsFiles.add("extra.js");
    try {
      runner.createJsModules(specs, jsFiles);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Too many js files"));
    }
  }

  // checkModuleName(): a syntactically valid JS identifier does not throw
  @Test
  public void testCheckModuleName_validIdentifier_doesNotThrow() throws Throwable {
    runner.checkModuleName("module1");
  }

  // checkModuleName(): an invalid identifier (leading digit) throws FlagUsageException
  @Test
  public void testCheckModuleName_invalidIdentifier_throwsFlagUsageException() throws Throwable {
    try {
      runner.checkModuleName("123bad");
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Invalid module name"));
    }
  }

  // parseModuleWrappers(): valid "name:wrapper" spec with %s placeholder is stored correctly
  @Test
  public void testParseModuleWrappers_validSpec_setsWrapper() throws Throwable {
    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:(%s)");
    Map<String, String> result = AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
    assertEquals("(%s)", result.get("mod1"));
  }

  // parseModuleWrappers(): spec missing a colon throws FlagUsageException
  @Test
  public void testParseModuleWrappers_missingColon_throwsFlagUsageException() throws Throwable {
    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));
    List<String> specs = new ArrayList<String>();
    specs.add("nowrapperhere");
    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("name"));
    }
  }

  // parseModuleWrappers(): wrapper spec referencing an unknown module throws FlagUsageException
  @Test
  public void testParseModuleWrappers_unknownModule_throwsFlagUsageException() throws Throwable {
    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));
    List<String> specs = new ArrayList<String>();
    specs.add("unknown:%s");
    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("expected FlagUsageException");
    } catch (FlagUsageException expected) {
      assertTrue(expected.getMessage().contains("Unknown module"));
    }
  }

  // writeOutput(): wrapper containing %output% marker interpolates code between prefix/suffix
  @Test
  public void testWriteOutput_wrapperWithMarker_interpolatesCode() throws Throwable {
    StringBuilder out = new StringBuilder();
    AbstractCommandLineRunner.writeOutput(out, null, "CODE", "pre%output%post", "%output%");
    assertEquals("preCODEpost\n", out.toString());
  }

  // writeOutput(): wrapper without the marker simply appends code followed by a newline
  @Test
  public void testWriteOutput_wrapperWithoutMarker_appendsCodeDirectly() throws Throwable {
    StringBuilder out = new StringBuilder();
    AbstractCommandLineRunner.writeOutput(out, null, "CODE", "nowrapper", "%output%");
    assertEquals("CODE\n", out.toString());
  }

  // doRun(): compiling input with a syntax error returns a non-zero error code
  @Test
  public void testDoRun_syntaxErrorInput_returnsNonZeroErrorCode() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream outStream = new PrintStream(baos);
    TestRunner r = new TestRunner(outStream, System.err);
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() {
        List<JSSourceFile> list = new ArrayList<JSSourceFile>();
        list.add(JSSourceFile.fromCode("bad.js", "var x = ;"));
        return list;
      }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { return true; }
    };
    r.enableTestMode(externs, inputs, null, exitReceiver);
    int code = r.doRun();
    assertTrue(code > 0);
  }

  // createExterns(): in test mode, returns exactly the externs supplier's list
  @Test
  public void testCreateExterns_testMode_returnsSupplierList() throws Throwable {
    final List<JSSourceFile> expected = new ArrayList<JSSourceFile>();
    expected.add(JSSourceFile.fromCode("externs.js", ""));
    Supplier<List<JSSourceFile>> externs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return expected; }
    };
    Supplier<List<JSSourceFile>> inputs = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() { return new ArrayList<JSSourceFile>(); }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) { return true; }
    };
    runner.enableTestMode(externs, inputs, null, exitReceiver);
    assertSame(expected, runner.createExterns());
  }

  // createExterns(): outside test mode with no --externs configured yields single /dev/null placeholder
  @Test
  public void testCreateExterns_nonTestModeNoExterns_returnsDevNullPlaceholder() throws Throwable {
    List<JSSourceFile> result = runner.createExterns();
    assertEquals(1, result.size());
  }

  // expandSourceMapPath(): an empty sourceMapOutputPath yields null (no map expansion)
  @Test
  public void testExpandSourceMapPath_emptyPath_returnsNull() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "";
    assertNull(runner.expandSourceMapPath(options, null));
  }

  // expandSourceMapPath(): %outname% placeholder is substituted with the configured jsOutputFile
  @Test
  public void testExpandSourceMapPath_withOutnamePlaceholder_substitutesJsOutputFile() throws Throwable {
    runner.getCommandLineConfig().setJsOutputFile("out.js");
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "%outname%.map";
    assertEquals("out.js.map", runner.expandSourceMapPath(options, null));
  }

  // expandManifest(): empty configured outputManifest yields null
  @Test
  public void testExpandManifest_emptyManifest_returnsNull() throws Throwable {
    assertNull(runner.expandManifest(null));
  }

  // filenameToOutputStream(): a null fileName returns null per its Javadoc contract
  @Test
  public void testFilenameToOutputStream_nullFileName_returnsNull() throws Throwable {
    assertNull(runner.filenameToOutputStream(null));
  }

  // createDefineOrTweakReplacements(): empty define name (before '=') throws RuntimeException mentioning "define"
  @Test
  public void testCreateDefineOrTweakReplacements_emptyName_throwsRuntimeException() throws Throwable {
    List<String> defs = new ArrayList<String>();
    defs.add("=true");
    try {
      AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, new CompilerOptions(), false);
      fail("expected RuntimeException");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("define"));
    }
  }

  // createDefineOrTweakReplacements(): non-numeric, non-boolean, non-quoted value under tweaks=true
  // throws RuntimeException mentioning "tweak"
  @Test
  public void testCreateDefineOrTweakReplacements_tweaksInvalidValue_throwsRuntimeException() throws Throwable {
    List<String> defs = new ArrayList<String>();
    defs.add("FOO=notanumber");
    try {
      AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, new CompilerOptions(), true);
      fail("expected RuntimeException");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("tweak"));
    }
  }
}
