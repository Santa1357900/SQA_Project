package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.rhino.Node;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbstractCommandLineRunnerTest {

  private static class DummyCompiler extends Compiler {
    @Override
    public Result compile(List<JSSourceFile> externs, List<JSSourceFile> inputs, CompilerOptions options) {
      return new Result();
    }

    @Override
    public Result compileModules(List<JSSourceFile> externs, List<JSModule> modules, CompilerOptions options) {
      return new Result();
    }
  }

  private static class ConcreteCommandLineRunner extends AbstractCommandLineRunner<DummyCompiler, CompilerOptions> {
    ConcreteCommandLineRunner(String[] args) {
      super();
    }

    ConcreteCommandLineRunner(PrintStream out, PrintStream err) {
      super(out, err);
    }

    @Override
    protected DummyCompiler createCompiler() {
      return new DummyCompiler();
    }

    @Override
    protected CompilerOptions createOptions() {
      return new CompilerOptions();
    }
  }

  @Test
  public void testTestModeAndExitCode() throws Throwable {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(
        new PrintStream(outContent), new PrintStream(errContent));

    final List<Integer> receivedCodes = new ArrayList<Integer>();
    Supplier<List<JSSourceFile>> externsSup = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() {
        return new ArrayList<JSSourceFile>();
      }
    };
    Supplier<List<JSSourceFile>> inputsSup = new Supplier<List<JSSourceFile>>() {
      public List<JSSourceFile> get() {
        return new ArrayList<JSSourceFile>();
      }
    };
    Function<Integer, Boolean> exitReceiver = new Function<Integer, Boolean>() {
      public Boolean apply(Integer input) {
        receivedCodes.add(input);
        return true;
      }
    };

    runner.enableTestMode(externsSup, inputsSup, null, exitReceiver);
    assertTrue(runner.isInTestMode());

    runner.run();
    assertEquals(1, receivedCodes.size());
    assertEquals(Integer.valueOf(0), receivedCodes.get(0));
  }

  @Test
  public void testParseModuleWrappers() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:(%s)");
    
    List<JSModule> modules = new ArrayList<JSModule>();
    JSModule mod1 = new JSModule("mod1");
    modules.add(mod1);

    Map<String, String> wrappers = AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
    assertEquals("(%s)", wrappers.get("mod1"));
  }

  @Test
  public void testParseModuleWrappersInvalidSpec() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1-no-colon");

    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Should have thrown FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("format"));
    }
  }

  @Test
  public void testParseModuleWrappersUnknownModule() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("unknown:(%s)");

    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Should have thrown FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Unknown module"));
    }
  }

  @Test
  public void testParseModuleWrappersMissingPlaceholder() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:no-placeholder");

    List<JSModule> modules = new ArrayList<JSModule>();
    modules.add(new JSModule("mod1"));

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Should have thrown FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("placeholder"));
    }
  }

  @Test
  public void testCreateDefineOrTweakReplacementsBoolean() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> defs = new ArrayList<String>();
    defs.add("MY_DEF=true");
    defs.add("MY_FALSE=false");
    defs.add("JUST_NAME");

    AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, options, false);
  }

  @Test
  public void testCreateDefineOrTweakReplacementsString() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> defs = new ArrayList<String>();
    defs.add("STR_DEF='hello'");
    defs.add("STR_DEF2=\"world\"");

    AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, options, false);
  }

  @Test
  public void testCreateDefineOrTweakReplacementsDouble() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> defs = new ArrayList<String>();
    defs.add("NUM_DEF=123.45");

    AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, options, false);
  }

  @Test
  public void testCreateDefineOrTweakReplacementsInvalid() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> defs = new ArrayList<String>();
    defs.add("INVALID_DEF=notanumberandnotbool");

    try {
      AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, options, false);
      fail("Should throw RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("syntax invalid"));
    }
  }

  @Test
  public void testCreateDefineOrTweakReplacementsTweakInvalid() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> defs = new ArrayList<String>();
    defs.add("INVALID_TWEAK=badval");

    try {
      AbstractCommandLineRunner.createDefineOrTweakReplacements(defs, options, true);
      fail("Should throw RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("--tweak flag syntax invalid"));
    }
  }

  @Test
  public void testWriteOutputWithPlaceholder() throws Throwable {
    StringBuilder sb = new StringBuilder();
    DummyCompiler compiler = new DummyCompiler();
    AbstractCommandLineRunner.writeOutput(sb, compiler, "codeContent", "wrapper-%output%-end", "%output%");
    assertTrue(sb.toString().contains("wrapper-codeContent-end"));
  }

  @Test
  public void testWriteOutputWithoutPlaceholder() throws Throwable {
    StringBuilder sb = new StringBuilder();
    DummyCompiler compiler = new DummyCompiler();
    AbstractCommandLineRunner.writeOutput(sb, compiler, "codeContent", "no-placeholder", "%output%");
    assertTrue(sb.toString().contains("codeContent"));
  }

  @Test
  public void testPrintModuleGraphManifestTo() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    JSModuleGraph graph = new JSModuleGraph(ImmutableList.<JSModule>of());
    StringBuilder sb = new StringBuilder();
    runner.printModuleGraphManifestTo(graph, sb);
    assertNotNull(sb.toString());
  }

  @Test
  public void testExpandSourceMapPathEmpty() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "";
    String path = runner.expandSourceMapPath(options, null);
    assertNull(path);
  }

  @Test
  public void testExpandManifestEmpty() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    String path = runner.expandManifest(null);
    assertNull(path);
  }

  @Test
  public void testFilenameToOutputStreamNull() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    assertNull(runner.filenameToOutputStream(null));
  }

  @Test
  public void testSetRunOptionsLanguageModes() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    CompilerOptions options = new CompilerOptions();
    
    runner.getCommandLineConfig().setLanguageIn("ES5");
    runner.getCommandLineConfig().setCharset("UTF-8");
    runner.getCommandLineConfig().setJsOutputFile("");
    runner.getCommandLineConfig().setCreateSourceMap("");
    runner.getCommandLineConfig().setVariableMapInputFile("");
    runner.getCommandLineConfig().setPropertyMapInputFile("");
    
    runner.setRunOptions(options);
  }

  @Test
  public void testSetRunOptionsInvalidLanguage() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    CompilerOptions options = new CompilerOptions();
    runner.getCommandLineConfig().setLanguageIn("INVALID_LANG");

    try {
      runner.setRunOptions(options);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Unknown language"));
    }
  }

  @Test
  public void testCreateJsModulesInvalidSpecCount() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> specs = new ArrayList<String>();
    specs.add("mod1"); // only 1 part
    List<String> jsFiles = new ArrayList<String>();
    jsFiles.add("file1.js");

    try {
      runner.createJsModules(specs, jsFiles);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("colon-delimited"));
    }
  }

  @Test
  public void testCreateJsModulesInvalidFileCount() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:notanint");
    List<String> jsFiles = new ArrayList<String>();
    jsFiles.add("file1.js");

    try {
      runner.createJsModules(specs, jsFiles);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Invalid js file count"));
    }
  }

  @Test
  public void testCreateJsModulesNotEnoughFiles() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:5");
    List<String> jsFiles = new ArrayList<String>();
    jsFiles.add("file1.js");

    try {
      runner.createJsModules(specs, jsFiles);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Not enough js files specified"));
    }
  }

  @Test
  public void testCreateJsModulesDuplicateName() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> specs = new ArrayList<String>();
    specs.add("mod1:1");
    specs.add("mod1:1");
    List<String> jsFiles = new ArrayList<String>();
    jsFiles.add("file1.js");
    jsFiles.add("file2.js");

    try {
      runner.createJsModules(specs, jsFiles);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Duplicate module name"));
    }
  }

  @Test
  public void testCreateInputsStdin() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> files = new ArrayList<String>();
    files.add("-");
    List<JSSourceFile> inputs = runner.createInputs(files, true);
    assertEquals(1, inputs.size());
  }

  @Test
  public void testCreateInputsStdinNotAllowed() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> files = new ArrayList<String>();
    files.add("-");
    try {
      runner.createInputs(files, false);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Can't specify stdin"));
    }
  }

  @Test
  public void testCreateInputsStdinTwice() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    List<String> files = new ArrayList<String>();
    files.add("-");
    files.add("-");
    try {
      runner.createInputs(files, true);
      fail("Should throw FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Can't specify stdin twice"));
    }
  }

  @Test
  public void testDiagnosticGroups() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    assertNotNull(runner.getDiagnosticGroups());
  }

  @Test
  public void testErrorPrintStream() throws Throwable {
    ConcreteCommandLineRunner runner = new ConcreteCommandLineRunner(new String[0]);
    assertNotNull(runner.getErrorPrintStream());
  }
}