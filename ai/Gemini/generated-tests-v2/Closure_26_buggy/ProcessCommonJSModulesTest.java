package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import org.junit.Test;

import java.io.File;
import static org.junit.Assert.*;

public class ProcessCommonJSModulesTest {

  @Test
  public void testToModuleNameBasic() throws Throwable {
    String result = ProcessCommonJSModules.toModuleName("foo.js");
    assertEquals("module$foo", result);
  }

  @Test
  public void testToModuleNameWithPrefixAndHyphen() throws Throwable {
    String result = ProcessCommonJSModules.toModuleName("." + File.separator + "foo-bar.js");
    assertEquals("module$foo_bar", result);
  }

  @Test
  public void testToModuleNameRelative() throws Throwable {
    String result = ProcessCommonJSModules.toModuleName("./bar.js", "foo/baz.js");
    assertEquals("module$foo$bar", result);
  }

  @Test
  public void testToModuleNameRelativeParent() throws Throwable {
    String result = ProcessCommonJSModules.toModuleName("../bar.js", "foo/baz/qux.js");
    assertEquals("module$foo$bar", result);
  }

  @Test
  public void testToModuleNameURISyntaxException() throws Throwable {
    try {
      ProcessCommonJSModules.toModuleName("http://[::1%]:80/", "foo.js");
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testGuessCJSModuleName() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix");
    String guess = pass.guessCJSModuleName("prefix" + File.separator + "module.js");
    assertEquals("module$module", guess);
  }

  @Test
  public void testGetModuleInitiallyNull() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix");
    assertNull(pass.getModule());
  }

  @Test
  public void testProcessWithReportDependencies() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix", true);
    Node root = new Node(Token.SCRIPT);
    root.setSourceFileName("prefix" + File.separator + "test.js");
    pass.process(null, root);
    assertNotNull(pass.getModule());
  }

  @Test
  public void testProcessWithoutReportDependencies() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessCommonJSModules pass = new ProcessCommonJSModules(compiler, "prefix", false);
    Node root = new Node(Token.SCRIPT);
    root.setSourceFileName("prefix" + File.separator + "test.js");
    pass.process(null, root);
    assertNull(pass.getModule());
  }
}