package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProcessCommonJSModulesTest {

  @Test
  public void testToModuleNameBasic() throws Throwable {
    String moduleName = ProcessCommonJSModules.toModuleName("./my-module.js");
    assertEquals("module$my_module", moduleName);
  }

  @Test
  public void testToModuleNameWithoutPrefix() throws Throwable {
    String moduleName = ProcessCommonJSModules.toModuleName("foo/bar.js");
    assertEquals("module$foo$bar", moduleName);
  }

  @Test
  public void testToModuleNameWithRelativeAddressing() throws Throwable {
    String resolved = ProcessCommonJSModules.toModuleName("./utils.js", "foo/bar.js");
    assertEquals("module$foo$utils", resolved);
  }

  @Test
  public void testToModuleNameWithParentAddressing() throws Throwable {
    String resolved = ProcessCommonJSModules.toModuleName("../utils.js", "foo/bar/baz.js");
    assertEquals("module$foo$utils", resolved);
  }

  @Test
  public void testToModuleNameUriSyntaxExceptionFallback() throws Throwable {
    try {
      ProcessCommonJSModules.toModuleName("http://invalid", "foo/bar.js");
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testConstructorAndGetModule() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix/");
    assertNull(pass.getModule());
  }

  @Test
  public void testConstructorWithoutReportDependencies() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix", false);
    assertNull(pass.getModule());
  }

  @Test
  public void testGuessCJSModuleName() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix/");
    String guessed = pass.guessCJSModuleName("prefix/my-file.js");
    assertEquals("module$my_file", guessed);
  }

  @Test
  public void testProcessTraversal() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "");
    Node root = new Node(Token.SCRIPT);
    pass.process(null, root);
    // Verifies process runs without throwing exceptions on empty script node
    assertNull(pass.getModule());
  }
}