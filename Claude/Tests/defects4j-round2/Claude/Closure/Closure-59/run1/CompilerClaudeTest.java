package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

public class CompilerClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  // Covers CodeBuilder.append with a single string, verifying text accumulation
  @Test
  public void testCodeBuilderAppend_singleString_returnsAppendedText() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello");
    assertEquals("hello", cb.toString());
  }

  // Covers CodeBuilder.append called multiple times concatenating in order
  @Test
  public void testCodeBuilderAppend_multipleAppends_concatenatesInOrder() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("foo");
    cb.append("bar");
    assertEquals("foobar", cb.toString());
  }

  // Covers CodeBuilder.reset clearing text but not line count
  @Test
  public void testCodeBuilderReset_afterAppend_clearsTextKeepsLineCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("a\nb\n");
    cb.reset();
    assertEquals("", cb.toString());
    assertEquals(2, cb.getLineIndex());
  }

  // Covers CodeBuilder.getLength reflecting current text length
  @Test
  public void testCodeBuilderGetLength_reflectsAppendedTextLength() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abcde");
    assertEquals(5, cb.getLength());
  }

  // Covers CodeBuilder.getLineIndex with no newlines present
  @Test
  public void testCodeBuilderGetLineIndex_noNewline_returnsZero() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("no newline here");
    assertEquals(0, cb.getLineIndex());
  }

  // Covers CodeBuilder.getLineIndex counting multiple newlines (loop multiple iterations)
  @Test
  public void testCodeBuilderGetLineIndex_withNewlines_countsLines() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("a\nb\nc\n");
    assertEquals(3, cb.getLineIndex());
  }

  // Covers CodeBuilder.getColumnIndex when no newline present (full length used)
  @Test
  public void testCodeBuilderGetColumnIndex_noNewline_returnsStringLength() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abcd");
    assertEquals(4, cb.getColumnIndex());
  }

  // Covers CodeBuilder.getColumnIndex when text ends exactly with a newline
  @Test
  public void testCodeBuilderGetColumnIndex_endsWithNewline_returnsZero() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc\n");
    assertEquals(0, cb.getColumnIndex());
  }

  // Covers CodeBuilder.endsWith branch where suffix is strictly shorter than text
  @Test
  public void testCodeBuilderEndsWith_suffixShorterThanText_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello;");
    assertTrue(cb.endsWith(";"));
  }

  // Covers CodeBuilder.endsWith contract when suffix equals entire text (bug-catching test)
  @Test
  public void testCodeBuilderEndsWith_suffixEqualsText_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append(";");
    assertTrue(cb.endsWith(";"));
  }

  // Covers CodeBuilder.endsWith branch where suffix is longer than text
  @Test
  public void testCodeBuilderEndsWith_suffixLongerThanText_returnsFalse() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append(";");
    assertFalse(cb.endsWith(";;"));
  }

  // Covers CodeBuilder.endsWith when suffix content does not match
  @Test
  public void testCodeBuilderEndsWith_mismatchedSuffix_returnsFalse() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc");
    assertFalse(cb.endsWith("xyz"));
  }

  // Covers CodeBuilder.toString returning full accumulated buffer
  @Test
  public void testCodeBuilderToString_returnsAccumulatedText() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("x");
    cb.append("y");
    cb.append("z");
    assertEquals("xyz", cb.toString());
  }

  // Covers getErrorManager lazy initialization branch (options == null)
  @Test
  public void testGetErrorManager_lazyInit_returnsNonNullManager() throws Throwable {
    assertNotNull(compiler.getErrorManager());
  }

  // Covers Preconditions.checkNotNull guard in setErrorManager when null passed
  @Test
  public void testSetErrorManager_null_throwsNullPointerException() throws Throwable {
    try {
      compiler.setErrorManager(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers getErrorCount after lazy error manager initialization
  @Test
  public void testGetErrorCount_afterLazyInit_isZero() throws Throwable {
    compiler.getErrorManager();
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers getWarningCount after lazy error manager initialization
  @Test
  public void testGetWarningCount_afterLazyInit_isZero() throws Throwable {
    compiler.getErrorManager();
    assertEquals(0, compiler.getWarningCount());
  }

  // Covers getErrors returning an empty (never null) array initially
  @Test
  public void testGetErrors_afterLazyInit_isEmptyArray() throws Throwable {
    compiler.getErrorManager();
    assertEquals(0, compiler.getErrors().length);
  }

  // Covers getWarnings returning an empty (never null) array initially
  @Test
  public void testGetWarnings_afterLazyInit_isEmptyArray() throws Throwable {
    compiler.getErrorManager();
    assertEquals(0, compiler.getWarnings().length);
  }

  // Covers getMessages delegating to getErrors
  @Test
  public void testGetMessages_sameAsGetErrors() throws Throwable {
    compiler.getErrorManager();
    assertEquals(compiler.getErrors().length, compiler.getMessages().length);
  }

  // Covers report() path where level.isOn() is true and error count increases
  @Test
  public void testReport_errorDiagnostic_increasesErrorCountAndHasErrorsTrue() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    compiler.report(JSError.make(Compiler.DUPLICATE_INPUT, "x"));
    assertEquals(1, compiler.getErrorCount());
    assertTrue(compiler.hasErrors());
  }

  // Covers Preconditions.checkNotNull(options) guard in getErrorLevel
  @Test
  public void testGetErrorLevel_withoutOptions_throwsNullPointerException() throws Throwable {
    JSError error = JSError.make(Compiler.DUPLICATE_INPUT, "x");
    try {
      compiler.getErrorLevel(error);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers getErrorLevel normal path returning configured CheckLevel
  @Test
  public void testGetErrorLevel_errorDiagnostic_returnsErrorLevel() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    JSError error = JSError.make(Compiler.DUPLICATE_INPUT, "x");
    assertEquals(CheckLevel.ERROR, compiler.getErrorLevel(error));
  }

  // Covers getUniqueNameIdSupplier producing sequential ids after reset
  @Test
  public void testGetUniqueNameIdSupplier_afterReset_returnsSequentialIds() throws Throwable {
    compiler.resetUniqueNameId();
    Supplier<String> supplier = compiler.getUniqueNameIdSupplier();
    assertEquals("0", supplier.get());
    assertEquals("1", supplier.get());
  }

  // Covers getRoot before any init/parse has happened
  @Test
  public void testGetRoot_beforeInit_returnsNull() throws Throwable {
    assertNull(compiler.getRoot());
  }

  // Covers areNodesEqualForInlining else-branch for structurally equal nodes
  @Test
  public void testAreNodesEqualForInlining_equalNames_returnsTrue() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    Node n1 = IR.name("a");
    Node n2 = IR.name("a");
    assertTrue(compiler.areNodesEqualForInlining(n1, n2));
  }

  // Covers areNodesEqualForInlining else-branch for structurally different nodes
  @Test
  public void testAreNodesEqualForInlining_differentNames_returnsFalse() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    Node n1 = IR.name("a");
    Node n2 = IR.name("b");
    assertFalse(compiler.areNodesEqualForInlining(n1, n2));
  }

  // Covers getCodingConvention returning a non-null convention with default options
  @Test
  public void testGetCodingConvention_defaultOptions_returnsNonNull() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    assertNotNull(compiler.getCodingConvention());
  }

  // Covers isIdeMode default value from fresh CompilerOptions
  @Test
  public void testIsIdeMode_defaultOptions_returnsFalse() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    assertFalse(compiler.isIdeMode());
  }

  // Covers isTypeCheckingEnabled default value from fresh CompilerOptions
  @Test
  public void testIsTypeCheckingEnabled_defaultOptions_returnsFalse() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    assertFalse(compiler.isTypeCheckingEnabled());
  }

  // Covers getTypeRegistry lazy-initialization memoization (same instance returned)
  @Test
  public void testGetTypeRegistry_calledTwice_returnsSameInstance() throws Throwable {
    compiler.initOptions(new CompilerOptions());
    JSTypeRegistry r1 = compiler.getTypeRegistry();
    JSTypeRegistry r2 = compiler.getTypeRegistry();
    assertSame(r1, r2);
  }

  // Covers checkFirstModule branch reporting EMPTY_MODULE_LIST_ERROR for empty modules list
  @Test
  public void testInitModules_emptyModuleList_reportsEmptyModuleListError() throws Throwable {
    compiler.initModules(new ArrayList<JSSourceFile>(), new ArrayList<JSModule>(), new CompilerOptions());
    assertTrue(compiler.hasErrors());
    assertEquals(1, compiler.getErrorCount());
  }

  // Covers initInputsByNameMap else-branch reporting DUPLICATE_INPUT for duplicate names
  @Test
  public void testInitInputsByNameMap_duplicateInputNames_reportsDuplicateInputError() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("dup.js", "var x = 1;"));
    inputs.add(JSSourceFile.fromCode("dup.js", "var y = 2;"));
    compiler.init(externs, inputs, new CompilerOptions());
    assertTrue(compiler.hasErrors());
    assertEquals(1, compiler.getErrorCount());
  }

  // Covers newExternInput success path, registering a brand new extern name
  @Test
  public void testNewExternInput_newName_registersInputSuccessfully() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("dup.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parse();
    CompilerInput created = compiler.newExternInput("new_extern.js");
    assertNotNull(created);
    assertSame(created, compiler.getInput("new_extern.js"));
  }

  // Covers newExternInput throwing IllegalArgumentException for conflicting existing name
  @Test
  public void testNewExternInput_duplicateName_throwsIllegalArgumentException() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("dup.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parse();
    try {
      compiler.newExternInput("dup.js");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Covers getInput returning null for an unknown input name
  @Test
  public void testGetInput_unknownName_returnsNull() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("a.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    assertNull(compiler.getInput("does_not_exist.js"));
  }

  // Covers removeExternInput successfully detaching an existing extern input
  @Test
  public void testRemoveExternInput_existingExtern_removesSuccessfully() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    externs.add(JSSourceFile.fromCode("extern.js", ""));
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("input.js", "var y = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parse();
    compiler.removeExternInput("extern.js");
    assertNull(compiler.getInput("extern.js"));
  }

  // Covers removeExternInput no-op branch when input is not found
  @Test
  public void testRemoveExternInput_unknownName_noEffect() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("a.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.removeExternInput("missing.js");
    assertNull(compiler.getInput("missing.js"));
  }

  // Covers successful full compile() pipeline with no errors on valid simple program
  @Test
  public void testCompile_simpleProgram_noErrorsReported() throws Throwable {
    CompilerOptions options = new CompilerOptions();
    options.skipAllPasses = true;
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
    compiler.disableThreads();
    Result result = compiler.compile(extern, input, options);
    assertNotNull(result);
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers Preconditions.checkState(jsRoot == null) guard rejecting a second compile() call
  @Test
  public void testCompile_calledTwice_throwsIllegalStateException() throws Throwable {
    CompilerOptions options1 = new CompilerOptions();
    options1.skipAllPasses = true;
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
    compiler.disableThreads();
    compiler.compile(extern, input, options1);
    CompilerOptions options2 = new CompilerOptions();
    options2.skipAllPasses = true;
    try {
      compiler.compile(extern, input, options2);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers getSourceLine early-return branch when lineNumber < 1
  @Test
  public void testGetSourceLine_lineNumberLessThanOne_returnsNull() throws Throwable {
    assertNull(compiler.getSourceLine("any.js", 0));
  }

  // Covers getSourceRegion early-return branch when lineNumber < 1
  @Test
  public void testGetSourceRegion_lineNumberLessThanOne_returnsNull() throws Throwable {
    assertNull(compiler.getSourceRegion("any.js", 0));
  }

  // Covers getSourceLine returning null when source name is not registered
  @Test
  public void testGetSourceLine_unknownSourceName_returnsNull() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("input.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    assertNull(compiler.getSourceLine("nonexistent.js", 1));
  }

  // Covers getSourceLine returning the actual line content for a known source
  @Test
  public void testGetSourceLine_knownSource_returnsLineContent() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("input.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    assertEquals("var x = 1;", compiler.getSourceLine("input.js", 1));
  }

  // Covers getSourceRegion returning a non-null region for a known source
  @Test
  public void testGetSourceRegion_knownSource_returnsNonNullRegion() throws Throwable {
    List<JSSourceFile> externs = new ArrayList<JSSourceFile>();
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>();
    inputs.add(JSSourceFile.fromCode("input.js", "var x = 1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    assertNotNull(compiler.getSourceRegion("input.js", 1));
  }
}
