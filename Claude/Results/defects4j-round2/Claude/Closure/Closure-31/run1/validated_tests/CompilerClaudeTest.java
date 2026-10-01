package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

public class CompilerClaudeTest {

  // Compiler() default constructor: outStream == null branch, lazy error manager init.
  @Test
  public void testConstructorDefault_errorManagerLazyInit() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getErrorManager());
  }

  // Compiler(PrintStream) constructor: outStream != null branch (PrintStreamErrorManager).
  @Test
  public void testConstructorWithPrintStream_errorManagerNotNull() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    Compiler compiler = new Compiler(ps);
    assertNotNull(compiler.getErrorManager());
  }

  // setErrorManager(null) must throw NullPointerException via Preconditions.checkNotNull.
  @Test
  public void testSetErrorManager_null_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setErrorManager(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // getPassConfig() lazily creates a DefaultPassConfig when passes is null.
  @Test
  public void testGetPassConfig_lazyCreatesDefaultPassConfig() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    PassConfig pc = compiler.getPassConfig();
    assertNotNull(pc);
    assertTrue(pc instanceof DefaultPassConfig);
  }

  // setPassConfig(null) throws NullPointerException.
  @Test
  public void testSetPassConfig_null_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setPassConfig(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // setPassConfig() a second time throws IllegalStateException.
  @Test
  public void testSetPassConfig_alreadyAssigned_throwsIllegalStateException() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    PassConfig pc = compiler.getPassConfig();
    try {
      compiler.setPassConfig(pc);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // precheck() always returns true.
  @Test
  public void testPrecheck_alwaysReturnsTrue() throws Throwable {
    Compiler compiler = new Compiler();
    assertTrue(compiler.precheck());
  }

  // getRoot() before any parsing returns null.
  @Test
  public void testGetRoot_beforeParse_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getRoot());
  }

  // getProgress() initial value is 0.0.
  @Test
  public void testGetProgress_initialValueIsZero() throws Throwable {
    Compiler compiler = new Compiler();
    assertEquals(0.0, compiler.getProgress(), 1e-9);
  }

  // setProgress() clamps values above 1.0 to 1.0.
  @Test
  public void testSetProgress_aboveOne_clampsToOne() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(1.5);
    assertEquals(1.0, compiler.getProgress(), 1e-9);
  }

  // setProgress() boundary: exactly 1.0 remains 1.0 (not clamped away).
  @Test
  public void testSetProgress_exactlyOne_remainsOne() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(1.0);
    assertEquals(1.0, compiler.getProgress(), 1e-9);
  }

  // setProgress() clamps values below 0.0 to 0.0.
  @Test
  public void testSetProgress_belowZero_clampsToZero() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(-0.5);
    assertEquals(0.0, compiler.getProgress(), 1e-9);
  }

  // setProgress() boundary: exactly 0.0 remains 0.0.
  @Test
  public void testSetProgress_exactlyZero_remainsZero() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(0.0);
    assertEquals(0.0, compiler.getProgress(), 1e-9);
  }

  // setProgress() normal in-range value is set as-is.
  @Test
  public void testSetProgress_normalValue_setsAsIs() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(0.42);
    assertEquals(0.42, compiler.getProgress(), 1e-9);
  }

  // createFillFileName() wraps the module name in brackets.
  @Test
  public void testCreateFillFileName_wrapsNameInBrackets() throws Throwable {
    assertEquals("[foo]", Compiler.createFillFileName("foo"));
  }

  // checkFirstModule(): empty module list reports EMPTY_MODULE_LIST_ERROR.
  @Test
  public void testInitModules_emptyModuleList_reportsError() throws Throwable {
    Compiler compiler = new Compiler();
    List<JSModule> modules = Lists.<JSModule>newArrayList();
    compiler.initModules(
        Lists.<SourceFile>newArrayList(), modules, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // checkFirstModule(): empty root module with >1 modules reports EMPTY_ROOT_MODULE_ERROR.
  @Test
  public void testInitModules_emptyRootModuleWithMultipleModules_reportsError() throws Throwable {
    Compiler compiler = new Compiler();
    JSModule m1 = new JSModule("m1");
    JSModule m2 = new JSModule("m2");
    m2.add(SourceFile.fromCode("m2.js", "var a = 1;"));
    List<JSModule> modules = Lists.newArrayList(m1, m2);
    compiler.initModules(
        Lists.<SourceFile>newArrayList(), modules, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // initInputsByIdMap(): duplicate input names report DUPLICATE_INPUT error.
  @Test
  public void testInit_duplicateInputNames_reportsError() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = Lists.newArrayList(
        SourceFile.fromCode("dup.js", "var a = 1;"),
        SourceFile.fromCode("dup.js", "var b = 2;"));
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // init() with a single valid module input produces no errors.
  @Test
  public void testInit_singleModule_noErrors() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a = 1;"));
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, new CompilerOptions());
    assertFalse(compiler.hasErrors());
  }

  // getSourceLine(): lineNumber < 1 returns null immediately.
  @Test
  public void testGetSourceLine_lineNumberLessThanOne_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("any.js", 0));
  }

  // getSourceRegion(): lineNumber < 1 returns null immediately.
  @Test
  public void testGetSourceRegion_lineNumberLessThanOne_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceRegion("any.js", -1));
  }

  // getSourceLine(): unknown source name returns null (input == null branch).
  @Test
  public void testGetSourceLine_unknownSourceName_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.init(Lists.<SourceFile>newArrayList(),
        Lists.newArrayList(SourceFile.fromCode("a.js", "var x;")), new CompilerOptions());
    assertNull(compiler.getSourceLine("unknown.js", 1));
  }

  // getSourceLine(): valid source name/line returns the actual line text.
  @Test
  public void testGetSourceLine_validSource_returnsLineText() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.init(Lists.<SourceFile>newArrayList(),
        Lists.newArrayList(SourceFile.fromCode("a.js", "var x;")), new CompilerOptions());
    assertEquals("var x;", compiler.getSourceLine("a.js", 1));
  }

  // getInput() returns the registered CompilerInput by id.
  @Test
  public void testGetInput_returnsRegisteredInput() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a = 1;"));
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, new CompilerOptions());
    CompilerInput input = compiler.getInput(new InputId("a.js"));
    assertNotNull(input);
    assertEquals("a.js", input.getName());
  }

  // getInputsInOrder()/getExternsInOrder() return unmodifiable views.
  @Test
  public void testGetInputsInOrder_and_ExternsInOrder_areUnmodifiable() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("e.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a=1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    List<CompilerInput> inputList = compiler.getInputsInOrder();
    List<CompilerInput> externList = compiler.getExternsInOrder();
    assertEquals(1, inputList.size());
    assertEquals(1, externList.size());
    try {
      inputList.add(inputList.get(0));
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }
  }

  // getInputsById() returns an unmodifiable map view.
  @Test
  public void testGetInputsById_isUnmodifiable() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a=1;"));
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, new CompilerOptions());
    Map<InputId, CompilerInput> map = compiler.getInputsById();
    assertTrue(map.containsKey(new InputId("a.js")));
    try {
      map.clear();
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }
  }

  // removeExternInput(): unknown id is a no-op (input == null branch).
  @Test
  public void testRemoveExternInput_unknownId_noOp() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("e.js", ""));
    compiler.init(externs,
        Lists.newArrayList(SourceFile.fromCode("a.js", "var a=1;")), new CompilerOptions());
    int before = compiler.getExternsForTesting().size();
    compiler.removeExternInput(new InputId("nonexistent.js"));
    assertEquals(before, compiler.getExternsForTesting().size());
  }

  // removeExternInput(): existing extern is removed and no longer retrievable.
  @Test
  public void testRemoveExternInput_existingExtern_removesInput() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("e.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a=1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parseInputs();
    assertFalse(compiler.hasErrors());
    InputId id = new InputId("e.js");
    assertNotNull(compiler.getInput(id));
    compiler.removeExternInput(id);
    assertNull(compiler.getInput(id));
  }

  // newExternInput() with a name that conflicts with an existing input throws IllegalArgumentException.
  @Test
  public void testNewExternInput_conflictingName_throwsIllegalArgumentException() throws Throwable {
    Compiler compiler = new Compiler();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("e.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("a.js", "var a=1;"));
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parseInputs();
    assertFalse(compiler.hasErrors());
    try {
      compiler.newExternInput("e.js");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // getUniqueNameIdSupplier(): after resetUniqueNameId() ids start at 0 and increment.
  @Test
  public void testGetUniqueNameIdSupplier_afterReset_generatesSequentialIds() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.resetUniqueNameId();
    Supplier<String> supplier = compiler.getUniqueNameIdSupplier();
    assertEquals("0", supplier.get());
    assertEquals("1", supplier.get());
  }

  // parseSyntheticCode(fileName, js) returns a valid SCRIPT node.
  @Test
  public void testParseSyntheticCodeTwoArg_returnsScriptNode() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = compiler.parseSyntheticCode("synthetic.js", "var y = 2;");
    assertNotNull(root);
    assertTrue(root.isScript());
  }

  // parseTestCode(js) returns a valid SCRIPT node, initializing inputsById lazily.
  @Test
  public void testParseTestCode_returnsScriptNode() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = compiler.parseTestCode("var z = 3;");
    assertNotNull(root);
    assertTrue(root.isScript());
  }

  // getDefaultErrorReporter() is never null.
  @Test
  public void testGetDefaultErrorReporter_notNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNotNull(compiler.getDefaultErrorReporter());
  }

  // toSource() with no jsRoot (never parsed) returns empty string.
  @Test
  public void testToSource_noJsRoot_returnsEmptyString() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    compiler.initOptions(new CompilerOptions());
    String src = compiler.toSource();
    assertEquals("", src);
  }

  // compile() with valid simple code produces no errors.
  @Test
  public void testCompile_validCode_noErrors() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("in.js", "var x = 1;"),
        options);
    assertEquals(0, compiler.getErrorCount());
  }

  // compile() called a second time throws IllegalStateException (jsRoot already set).
  @Test
  public void testCompile_calledTwice_throwsIllegalStateException() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("e1.js", ""),
        SourceFile.fromCode("i1.js", "var a=1;"),
        options);
    try {
      compiler.compile(
          SourceFile.fromCode("e2.js", ""),
          SourceFile.fromCode("i2.js", "var b=2;"),
          options);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // CodeBuilder.append()/getLength()/toString() basic behavior.
  @Test
  public void testCodeBuilder_append_and_getLength() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello");
    assertEquals(5, cb.getLength());
    assertEquals("hello", cb.toString());
  }

  // CodeBuilder.append() with a newline updates line and column index correctly.
  @Test
  public void testCodeBuilder_appendWithNewline_updatesLineAndColumn() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("line1\nline2");
    assertEquals(1, cb.getLineIndex());
    assertEquals(5, cb.getColumnIndex());
  }

  // CodeBuilder.append() without newline accumulates column count across calls.
  @Test
  public void testCodeBuilder_appendMultipleTimesWithoutNewline_accumulatesColumn() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc");
    cb.append("de");
    assertEquals(5, cb.getColumnIndex());
    assertEquals(0, cb.getLineIndex());
  }

  // CodeBuilder.reset() clears text but leaves the line count unchanged (per javadoc).
  @Test
  public void testCodeBuilder_reset_clearsTextKeepsLineCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc\n");
    cb.reset();
    assertEquals(0, cb.getLength());
    assertEquals(1, cb.getLineIndex());
  }



  // endsWith() true when buffer is strictly longer than the suffix and matches.
  @Test
  public void testCodeBuilder_endsWith_trueWhenBufferLongerThanSuffix() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc;");
    assertTrue(cb.endsWith(";"));
  }

  // endsWith() false when the buffer does not end with the given suffix.
  @Test
  public void testCodeBuilder_endsWith_falseWhenMismatch() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc");
    assertFalse(cb.endsWith(";"));
  }

  // endsWith() false when buffer is shorter than the suffix.
  @Test
  public void testCodeBuilder_endsWith_falseWhenBufferShorterThanSuffix() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("a");
    assertFalse(cb.endsWith("ab"));
  }
}
