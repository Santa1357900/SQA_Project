package com.google.javascript.jscomp;

import java.util.ArrayList;
import java.util.List;

import com.google.javascript.rhino.Node;

import org.junit.Test;
import static org.junit.Assert.*;

public class InlineVariablesClaudeTest {

  /**
   * Helper: parses the given code, runs InlineVariables with the given
   * mode/flag directly (bypassing default pass scheduling), and returns the
   * resulting source text.
   */
  private String runPass(InlineVariables.Mode mode, boolean inlineAllStrings,
      String code) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", code));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    InlineVariables pass = new InlineVariables(compiler, mode, inlineAllStrings);
    pass.process(externsRoot, mainRoot);
    return compiler.toSource();
  }

  // Mode.values() must return the three declared constants, in declaration order
  @Test
  public void testModeValues_order() throws Throwable {
    InlineVariables.Mode[] modes = InlineVariables.Mode.values();
    assertEquals(3, modes.length);
    assertEquals(InlineVariables.Mode.CONSTANTS_ONLY, modes[0]);
    assertEquals(InlineVariables.Mode.LOCALS_ONLY, modes[1]);
    assertEquals(InlineVariables.Mode.ALL, modes[2]);
  }

  // Mode.valueOf("CONSTANTS_ONLY") returns the matching constant
  @Test
  public void testModeValueOf_constantsOnly() throws Throwable {
    assertEquals(InlineVariables.Mode.CONSTANTS_ONLY,
        InlineVariables.Mode.valueOf("CONSTANTS_ONLY"));
  }

  // Mode.valueOf("LOCALS_ONLY") returns the matching constant
  @Test
  public void testModeValueOf_localsOnly() throws Throwable {
    assertEquals(InlineVariables.Mode.LOCALS_ONLY,
        InlineVariables.Mode.valueOf("LOCALS_ONLY"));
  }

  // Mode.valueOf("ALL") returns the matching constant
  @Test
  public void testModeValueOf_all() throws Throwable {
    assertEquals(InlineVariables.Mode.ALL, InlineVariables.Mode.valueOf("ALL"));
  }

  // Mode.valueOf with an unknown name must throw per Enum contract
  @Test
  public void testModeValueOf_invalidName_throwsIllegalArgumentException() throws Throwable {
    try {
      InlineVariables.Mode.valueOf("NOT_A_MODE");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Constructor with ALL mode constructs a usable, non-null pass instance
  @Test
  public void testConstructor_allMode_createsNonNullInstance() throws Throwable {
    Compiler compiler = new Compiler();
    InlineVariables pass =
        new InlineVariables(compiler, InlineVariables.Mode.ALL, true);
    assertNotNull(pass);
  }

  // Constructor with LOCALS_ONLY mode constructs a usable, non-null pass instance
  @Test
  public void testConstructor_localsOnlyMode_createsNonNullInstance() throws Throwable {
    Compiler compiler = new Compiler();
    InlineVariables pass =
        new InlineVariables(compiler, InlineVariables.Mode.LOCALS_ONLY, false);
    assertNotNull(pass);
  }

  // Constructor with CONSTANTS_ONLY mode constructs a usable, non-null pass instance
  @Test
  public void testConstructor_constantsOnlyMode_createsNonNullInstance() throws Throwable {
    Compiler compiler = new Compiler();
    InlineVariables pass =
        new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY, false);
    assertNotNull(pass);
  }

  // Single-use local literal variable: declaration==init, refCount==2 branch -> inlined
  @Test
  public void testProcess_allMode_inlinesSingleUseLocalLiteralVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var a=7;return a;}");
    assertFalse(out.contains("var a"));
    assertTrue(out.contains("7"));
  }

  // Immutable literal referenced twice -> isImmutableAndWellDefinedVariable branch, both inlined
  @Test
  public void testProcess_allMode_inlinesImmutableVariableUsedMultipleTimes() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var a=5;alert(a);alert(a);}");
    assertFalse(out.contains("var a"));
    assertTrue(out.contains("5"));
  }

  // Variable reassigned after declaration is not "assigned once" -> must remain declared
  @Test
  public void testProcess_allMode_doesNotInlineReassignedVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var a=1;a=2;alert(a);}");
    assertTrue(out.contains("var a"));
  }

  // Declared-but-never-read variable: refCount==1, no branch applies, stays declared
  @Test
  public void testProcess_allMode_doesNotInlineUnusedVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var a=1;}");
    assertTrue(out.contains("var a"));
  }

  // isVarInlineForbidden: the special rename-property function name is never inlined
  @Test
  public void testProcess_allMode_forbidsInliningRenamePropertyFunctionVariable() throws Throwable {
    String name = RenameProperties.RENAME_PROPERTY_FUNCTION_NAME;
    String code = "function f(){var " + name + "=1;alert(" + name + ");}";
    String out = runPass(InlineVariables.Mode.ALL, false, code);
    assertTrue(out.contains("var " + name));
  }

  // IdentifyLocals filter: a global variable is excluded from LOCALS_ONLY mode
  @Test
  public void testProcess_localsOnlyMode_doesNotInlineGlobalVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.LOCALS_ONLY, false,
        "var a=1;alert(a);");
    assertTrue(out.contains("var a"));
  }

  // IdentifyLocals filter: a local, single-use variable is inlined under LOCALS_ONLY
  @Test
  public void testProcess_localsOnlyMode_inlinesLocalSingleUseVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.LOCALS_ONLY, false,
        "function f(){var a=1;return a;}");
    assertFalse(out.contains("var a"));
    assertTrue(out.contains("1"));
  }

  // IdentifyConstants filter: a plain (non-const) variable is never collected in CONSTANTS_ONLY mode
  @Test
  public void testProcess_constantsOnlyMode_doesNotInlineNonConstantVariable() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, false,
        "function f(){var a=1;return a;}");
    assertTrue(out.contains("var a"));
  }

  // @const-annotated literal constant is eligible and gets inlined via inlineDeclaredConstant
  @Test
  public void testProcess_constantsOnlyMode_inlinesConstAnnotatedLiteral() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, false,
        "/** @const */ var FOO=1; alert(FOO);");
    assertFalse(out.contains("var FOO"));
    assertTrue(out.contains("1"));
  }

  // isInlineableDeclaredConstant: a mutable object literal is not an immutable value -> not inlined
  @Test
  public void testProcess_constantsOnlyMode_doesNotInlineMutableObjectConstant() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, false,
        "/** @const */ var FOO={}; alert(FOO);");
    assertTrue(out.contains("var FOO"));
  }

  // isValidDeclaration: a for-loop VAR initializer must not be inlined
  @Test
  public void testProcess_allMode_doesNotInlineForLoopDeclaration() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){for(var i=0;;){return i;}}");
    assertTrue(out.contains("var i"));
  }

  // isStringWorthInlining short-circuits to true whenever inlineAllStrings is set
  @Test
  public void testProcess_inlineAllStringsTrue_inlinesShortStringConstant() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, true,
        "/** @const */ var S='x'; alert(S);");
    assertFalse(out.contains("var S"));
    assertTrue(out.contains("x"));
  }

  // canMoveAggressively treats string literals as literal values, so single-use strings always inline
  @Test
  public void testProcess_allMode_inlinesStringLiteralUsedOnceRegardlessOfFlag() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var s='hello';return s;}");
    assertFalse(out.contains("var s"));
    assertTrue(out.contains("hello"));
  }

  // declaration != init, refCount==2 (only the write, no reads): both declaration and assignment removed
  @Test
  public void testProcess_allMode_separateDeclarationAndAssignment_removesBoth() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false, "var a;a=5;");
    assertFalse(out.contains("var a"));
    assertFalse(out.contains("5"));
  }

  // Empty program in ALL mode: pass must run over an empty scope without throwing
  @Test
  public void testProcess_allMode_emptyProgram_doesNotThrow() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false, "");
    assertNotNull(out);
  }

  // Empty program in CONSTANTS_ONLY mode: pass must run without throwing
  @Test
  public void testProcess_constantsOnlyMode_emptyProgram_doesNotThrow() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, false, "");
    assertNotNull(out);
  }

  // Uninitialized-but-never-assigned variable is treated as well-defined (undefined) and inlined away
  @Test
  public void testProcess_allMode_inlinesUninitializedVariable_removesDeclaration() throws Throwable {
    String out = runPass(InlineVariables.Mode.ALL, false,
        "function f(){var a;alert(a);}");
    assertFalse(out.contains("var a"));
  }

  // isInlineableDeclaredConstant: a constant assigned more than once fails isAssignedOnceInLifetime
  @Test
  public void testProcess_constantsOnlyMode_doesNotInlineWhenAssignedMultipleTimes() throws Throwable {
    String out = runPass(InlineVariables.Mode.CONSTANTS_ONLY, false,
        "/** @const */ var FOO=1; FOO=2; alert(FOO);");
    assertTrue(out.contains("var FOO"));
  }
}
