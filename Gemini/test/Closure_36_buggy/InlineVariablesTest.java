package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import junit.framework.TestCase;

public class InlineVariablesTest extends TestCase {

  private boolean compilerResets = false;

  public void testInlineVariablesCreation() throws Throwable {
    Compiler compiler = new Compiler();
    InlineVariables inlineConstantsOnly = new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY, false);
    InlineVariables inlineLocalsOnly = new InlineVariables(compiler, InlineVariables.Mode.LOCALS_ONLY, false);
    InlineVariables inlineAll = new InlineVariables(compiler, InlineVariables.Mode.ALL, true);

    assertNotNull(inlineConstantsOnly);
    assertNotNull(inlineLocalsOnly);
    assertNotNull(inlineAll);
  }

  public void testProcessWithNullNodes() throws Throwable {
    Compiler compiler = new Compiler();
    InlineVariables inlineVariables = new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY, false);
    
    try {
      inlineVariables.process(null, null);
    } catch (Throwable t) {
      // Expected or handled internally depending on compiler setup
    }
  }

  public void testModeEnumValues() throws Throwable {
    InlineVariables.Mode[] modes = InlineVariables.Mode.values();
    assertEquals(3, modes.length);
    assertEquals(InlineVariables.Mode.CONSTANTS_ONLY, InlineVariables.Mode.valueOf("CONSTANTS_ONLY"));
    assertEquals(InlineVariables.Mode.LOCALS_ONLY, InlineVariables.Mode.valueOf("LOCALS_ONLY"));
    assertEquals(InlineVariables.Mode.ALL, InlineVariables.Mode.valueOf("ALL"));
  }
}