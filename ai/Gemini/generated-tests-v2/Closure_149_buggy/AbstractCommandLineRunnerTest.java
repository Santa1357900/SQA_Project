package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractCommandLineRunnerTest {

  private static class DummyCommandLineRunner extends AbstractCommandLineRunner<Compiler, CompilerOptions> {
    private final Compiler compilerInstance;

    DummyCommandLineRunner(Compiler compiler, PrintStream out, PrintStream err) {
      super(out, err);
      this.compilerInstance = compiler;
    }

    @Override
    protected Compiler createCompiler() {
      return compilerInstance;
    }

    @Override
    protected CompilerOptions createOptions() {
      CompilerOptions options = new CompilerOptions();
      return options;
    }
  }

  @Test
  public void testCreateDefineReplacementsBooleanTrue() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myBool=true");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsBooleanFalse() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myBool=false");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsBooleanOmitted() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myBool");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsDouble() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myNum=123.45");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsStringSingleQuotes() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myStr='hello'");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsStringDoubleQuotes() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("myStr=\"hello\"");

    AbstractCommandLineRunner.createDefineReplacements(definitions, options);
    assertNotNull(options);
  }

  @Test
  public void testCreateDefineReplacementsInvalid() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    List<String> definitions = new ArrayList<String>();
    definitions.add("invalidDef=unsupported_value_format_xyz");

    try {
      AbstractCommandLineRunner.createDefineReplacements(definitions, options);
      fail("Expected RuntimeException for invalid define syntax");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("--define flag syntax invalid"));
    }
  }

  @Test
  public void testWriteOutputWithPlaceholder() throws Throwable {
    Appendable out = new StringBuilder();
    Compiler compiler = null;
    String code = "var x = 1;";
    String wrapper = "(function() { %s })().call(this);";
    String placeholder = "%s";

    AbstractCommandLineRunner.writeOutput(out, compiler, code, wrapper, placeholder);
    assertTrue(out.toString().contains("var x = 1;"));
  }

  @Test
  public void testWriteOutputWithoutPlaceholder() throws Throwable {
    Appendable out = new StringBuilder();
    Compiler compiler = null;
    String code = "var x = 1;";
    String wrapper = "no-placeholder";
    String placeholder = "%s";

    AbstractCommandLineRunner.writeOutput(out, compiler, code, wrapper, placeholder);
    assertTrue(out.toString().contains("var x = 1;"));
  }

  @Test
  public void testParseModuleWrappersValid() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:(%s)");
    
    JSModule[] modules = new JSModule[1];
    modules[0] = new JSModule("m1");

    java.util.Map<String, String> wrappers = AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
    assertNotNull(wrappers);
    assertEquals("(%s)", wrappers.get("m1"));
  }

  @Test
  public void testParseModuleWrappersMissingColon() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1-invalid-spec");
    
    JSModule[] modules = new JSModule[1];
    modules[0] = new JSModule("m1");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Expected module wrapper to have"));
    }
  }

  @Test
  public void testParseModuleWrappersUnknownModule() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("unknown:(%s)");
    
    JSModule[] modules = new JSModule[1];
    modules[0] = new JSModule("m1");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Unknown module"));
    }
  }

  @Test
  public void testParseModuleWrappersMissingPlaceholder() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:no-placeholder");
    
    JSModule[] modules = new JSModule[1];
    modules[0] = new JSModule("m1");

    try {
      AbstractCommandLineRunner.parseModuleWrappers(specs, modules);
      fail("Expected FlagUsageException");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("No %s placeholder"));
    }
  }

  @Test
  public void testCreateJsModulesValid() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("m1:1");
    
    List<String> jsFiles = new ArrayList<String>();
    // Create a dummy temp file for testing module creation input
    java.io.File tempFile = java.io.File.createTempFile("testJs", ".js");
    tempFile.deleteOnExit();
    jsFiles.add(tempFile.getAbsolutePath());

    JSModule[] modules = AbstractCommandLineRunner.createJsModules(specs, jsFiles);
    assertNotNull(modules);
    assertEquals(1, modules.length);
    assertEquals("m1", modules[0].getName());
  }

  @Test
  public void testCreateJsModulesInvalidFormat() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("invalid:spec:format:too:many:colons");
    
    List<String> jsFiles = new ArrayList<String>();

    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Expected 2-4 colon-delimited parts"));
    }
  }

  @Test
  public void testCreateJsModulesInvalidName() throws Throwable {
    List<String> specs = new ArrayList<String>();
    specs.add("123invalidName:1");
    
    List<String> jsFiles = new ArrayList<String>();

    try {
      AbstractCommandLineRunner.createJsModules(specs, jsFiles);
      fail("Expected FlagUsageException");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Invalid module name"));
    }
  }

  @Test
  public void testPrintModuleGraphManifestTo() throws Throwable {
    Compiler compiler = new Compiler();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes);
    DummyCommandLineRunner runner = new DummyCommandLineRunner(compiler, System.out, err);

    JSModuleGraph graph = new JSModuleGraph(new ArrayList<JSModule>());
    Appendable out = new StringBuilder();

    runner.printModuleGraphManifestTo(graph, out);
    assertNotNull(out.toString());
  }
}