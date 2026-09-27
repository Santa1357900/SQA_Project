package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class AbstractCommandLineRunnerTest {

  private static class DummyCommandLineRunner extends AbstractCommandLineRunner<Compiler, CompilerOptions> {
    private final Compiler compilerInstance;
    private final CompilerOptions optionsInstance;

    public DummyCommandLineRunner(Compiler compiler, CompilerOptions options) {
      super();
      this.compilerInstance = compiler;
      this.optionsInstance = options;
    }

    @Override
    protected Compiler createCompiler() {
      return compilerInstance;
    }

    @Override
    protected CompilerOptions createOptions() {
      return optionsInstance;
    }
  }

  @Test
  public void testCreateDefineReplacementsBooleanAndNumber() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("MY_BOOL=true");
    definitions.add("MY_FALSE=false");
    definitions.add("MY_NUM=123.45");
    definitions.add("MY_FLAG");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    // Verified that it executes without throwing exceptions for valid syntax
    assertTrue(true);
  }

  @Test
  public void testCreateDefineReplacementsStringLiteral() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("MY_STR='hello'");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertTrue(true);
  }

  @Test(expected = RuntimeException.class)
  public void testCreateDefineReplacementsInvalidSyntax() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("INVALID_SYNTAX='unterminated");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
  }

  @Test(expected = RuntimeException.class)
  public void testCreateDefineReplacementsMalformed() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("=invalidName");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
  }

  @Test
  public void testCreateJsModulesValid() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:1");
    List<String> jsFiles = new ArrayList<String>();
    File tempFile = File.createTempFile("testJsModule", ".js");
    tempFile.deleteOnExit();
    jsFiles.add(tempFile.getAbsolutePath());

    JSModule[] modules = AbstractCommandLineRunner.createJsModules(specs, jsFiles);
    assertNotNull(modules);
    assertEquals(1, modules.length);
    assertEquals("m1", modules[0].getName());
  }

  @Test
  public void testCreateJsModulesInvalidSpecColon() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Expected 2-4 colon-delimited parts"));
    }
  }

  @Test
  public void testCreateJsModulesInvalidIdentifier() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("invalid-name:1");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Invalid module name"));
    }
  }

  @Test
  public void testCreateJsModulesDuplicateName() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:0");
    specs.add("m1:0");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Duplicate module name"));
    }
  }

  @Test
  public void testCreateJsModulesInvalidFileCount() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:abc");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Invalid js file count"));
    }
  }

  @Test
  public void testCreateJsModulesNotEnoughFiles() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:5");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Not enough js files specified"));
    }
  }

  @Test
  public void testCreateJsModulesTooManyFiles() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:0");
    List<String> jsFiles = new ArrayList<String>();
    File tempFile = File.createTempFile("testJsModule2", ".js");
    tempFile.deleteOnExit();
    jsFiles.add(tempFile.getAbsolutePath());

    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Too many js files specified"));
    }
  }

  @Test
  public void testCreateJsModulesUnknownDependency() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:0::unknownDep");
    List<String> jsFiles = new ArrayList<String>();
    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("depends on unknown module"));
    }
  }

  @Test
  public void testParseModuleWrappersValid() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule[] modules = new JSModule[] { m1 };
    List<String> specs = new ArrayList<String>();
    specs.add("m1:(%s)");

    java.util.Map<String, String> wrappers = AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
    assertNotNull(wrappers);
    assertEquals("(%s)", wrappers.get("m1"));
  }

  @Test
  public void testParseModuleWrappersMissingColon() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule[] modules = new JSModule[] { m1 };
    List<String> specs = new ArrayList<String>();
    specs.add("m1wrapperWithoutColon");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Expected module wrapper to have"));
    }
  }

  @Test
  public void testParseModuleWrappersUnknownModule() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule[] modules = new JSModule[] { m1 };
    List<String> specs = new ArrayList<String>();
    specs.add("unknown: (%s)");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("Unknown module"));
    }
  }

  @Test
  public void testParseModuleWrappersMissingPlaceholder() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule[] modules = new JSModule[] { m1 };
    List<String> specs = new ArrayList<String>();
    specs.add("m1:noPlaceholder");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      assertTrue(e.getMessage().contains("No %s placeholder"));
    }
  }

  @Test
  public void testWriteOutputWithPlaceholder() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    Compiler compiler = null;
    String code = "var x = 1;";
    String wrapper = "PREFIX%output%SUFFIX";
    String marker = "%output%";

    AbstractCommandLineRunner.writeOutput(ps, compiler, code, wrapper, marker);
    ps.flush();
    String output = baos.toString();
    assertTrue(output.contains("PREFIXvar x = 1;SUFFIX"));
  }

  @Test
  public void testWriteOutputWithoutPlaceholder() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    Compiler compiler = null;
    String code = "var x = 1;";
    String wrapper = "NO_PLACEHOLDER";
    String marker = "%output%";

    AbstractCommandLineRunner.writeOutput(ps, compiler, code, wrapper, marker);
    ps.flush();
    String output = baos.toString();
    assertTrue(output.contains("var x = 1;"));
  }

  @Test
  public void testWriteOutputPlaceholderAtEnd() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    Compiler compiler = null;
    String code = "var x = 1;";
    String wrapper = "PREFIX%output%";
    String marker = "%output%";

    AbstractCommandLineRunner.writeOutput(ps, compiler, code, wrapper, marker);
    ps.flush();
    String output = baos.toString();
    assertTrue(output.contains("PREFIXvar x = 1;"));
  }

  @Test
  public void testInitOptionsFromFlags() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    DummyCommandLineRunner runner = new DummyCommandLineRunner(compiler, options);
    runner.getCommandLineConfig().setCharset("UTF-8");

    runner.initOptionsFromFlags(options);
    assertNotNull(runner.getErrorPrintStream());
  }

  @Test
  public void testSetRunOptions() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    DummyCommandLineRunner runner = new DummyCommandLineRunner(compiler, options);
    runner.getCommandLineConfig().setJsOutputFile("testOut.js");
    runner.getCommandLineConfig().setCreateSourceMap("testMap.map");

    runner.setRunOptions(options);
    assertEquals("testOut.js", options.jsOutputFile);
    assertEquals("testMap.map", options.sourceMapOutputPath);
  }
}