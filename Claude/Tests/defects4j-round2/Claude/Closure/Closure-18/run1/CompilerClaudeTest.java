package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.Lists;
import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

public class CompilerClaudeTest {

  // Covers: default constructor, initial error state
  @Test
  public void testConstructorDefault_noErrorsInitially() throws Throwable {
    Compiler compiler = new Compiler();
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers: Compiler(ErrorManager) constructor -> setErrorManager(null) throws NPE
  @Test
  public void testConstructorWithErrorManager_null_throwsNPE() throws Throwable {
    try {
      new Compiler((ErrorManager) null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers: setErrorManager(null) precondition branch
  @Test
  public void testSetErrorManager_null_throwsNPE() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setErrorManager(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers: setErrorManager with valid manager, then getErrorManager returns it (errorManager != null branch in initOptions)
  @Test
  public void testSetErrorManager_thenGetErrorManager_returnsSameInstance() throws Throwable {
    Compiler compiler1 = new Compiler();
    ErrorManager manager = compiler1.getErrorManager();
    Compiler compiler2 = new Compiler();
    compiler2.setErrorManager(manager);
    assertSame(manager, compiler2.getErrorManager());
  }

  // Covers: getErrorManager() lazily calling initOptions when options == null
  @Test
  public void testGetErrorManager_lazyInitializesOptions() throws Throwable {
    Compiler compiler = new Compiler();
    ErrorManager manager = compiler.getErrorManager();
    assertNotNull(manager);
  }

  // Covers: init(List, List, CompilerOptions) happy path
  @Test
  public void testInit_setsUpInputs_noErrors() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("input.js", "var x=1;"));
    compiler.init(externs, inputs, options);
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers: checkFirstModule -> modules.isEmpty() branch reports EMPTY_MODULE_LIST_ERROR
  @Test
  public void testInitModules_emptyModuleList_reportsError() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    List<JSModule> modules = Lists.newArrayList();
    compiler.initModules(externs, modules, options);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // Covers: checkFirstModule -> single empty module with size==1, no error branch
  @Test
  public void testInitModules_singleEmptyModule_noError() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    JSModule module = new JSModule("m1");
    List<JSModule> modules = Lists.newArrayList(module);
    compiler.initModules(externs, modules, options);
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers: checkFirstModule -> empty root module with size>1 reports EMPTY_ROOT_MODULE_ERROR
  @Test
  public void testInitModules_emptyRootModuleMultiple_reportsError() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    JSModule module1 = new JSModule("m1");
    JSModule module2 = new JSModule("m2");
    module2.add(SourceFile.fromCode("input.js", "var x=1;"));
    List<JSModule> modules = Lists.newArrayList(module1, module2);
    compiler.initModules(externs, modules, options);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // Covers: compile() full path with valid code, no errors
  @Test
  public void testCompile_validCode_noErrors() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers: compile() with a syntax error -> hasErrors() branch in compileInternal
  @Test
  public void testCompile_syntaxError_hasErrors() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var = ;");
    compiler.compile(extern, input, options);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // Covers: getResult() after successful compile
  @Test
  public void testGetResult_afterCompile_returnsResult() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    Result result = compiler.getResult();
    assertNotNull(result);
  }

  // Covers: getInputsById() after compile contains entries
  @Test
  public void testGetInputsById_afterCompile_containsInputs() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    assertFalse(compiler.getInputsById().isEmpty());
  }

  // Covers: disableThreads() then compile via non-thread path
  @Test
  public void testDisableThreads_thenCompile_stillWorks() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    assertEquals(0, compiler.getErrorCount());
  }

  // Covers: parse(SourceFile) returns a script root node
  @Test
  public void testParse_simpleCode_returnsScriptNode() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = compiler.parse(SourceFile.fromCode("test.js", "var x = 1;"));
    assertNotNull(root);
    assertTrue(root.isScript());
  }

  // Covers: toSource() after compile returns generated code text
  @Test
  public void testToSource_afterCompile_returnsNonEmptyString() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    String source = compiler.toSource();
    assertTrue(source.length() > 0);
  }

  // Covers: setPassConfig(null) -> NullPointerException via Preconditions.checkNotNull
  @Test
  public void testSetPassConfig_null_throwsNPE() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setPassConfig(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers: setPassConfig() called twice -> IllegalStateException branch
  @Test
  public void testSetPassConfig_calledTwice_throwsIllegalState() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.setPassConfig(new DefaultPassConfig(options));
    try {
      compiler.setPassConfig(new DefaultPassConfig(options));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers: getAstDotGraph() when jsRoot == null returns empty string
  @Test
  public void testGetAstDotGraph_beforeParse_returnsEmptyString() throws Throwable {
    Compiler compiler = new Compiler();
    String dot = compiler.getAstDotGraph();
    assertEquals("", dot);
  }

  // Covers: getSourceLine() with lineNumber < 1 returns null
  @Test
  public void testGetSourceLine_lineNumberLessThanOne_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("test.js", 0));
  }

  // Covers: getSourceRegion() with lineNumber < 1 returns null
  @Test
  public void testGetSourceRegion_lineNumberLessThanOne_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceRegion("test.js", 0));
  }

  // Covers: static getReleaseVersion()
  @Test
  public void testGetReleaseVersion_notNull() throws Throwable {
    String version = Compiler.getReleaseVersion();
    assertNotNull(version);
  }

  // Covers: static getReleaseDate()
  @Test
  public void testGetReleaseDate_notNull() throws Throwable {
    String date = Compiler.getReleaseDate();
    assertNotNull(date);
  }

  // Covers: static setLoggingLevel()
  @Test
  public void testSetLoggingLevel_setsLoggerLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.WARNING);
    Logger logger = Logger.getLogger("com.google.javascript.jscomp");
    assertEquals(Level.WARNING, logger.getLevel());
  }

  // Covers: getErrorLevel(JSError) delegates to warningsGuard
  @Test
  public void testGetErrorLevel_returnsNonNullLevel() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSError error = JSError.make(Compiler.OPTIMIZE_LOOP_ERROR, "5");
    CheckLevel level = compiler.getErrorLevel(error);
    assertNotNull(level);
  }

  // Covers: report(JSError) -> level.isOn() true branch increments error count
  @Test
  public void testReport_addsErrorWhenLevelIsError() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSError error = JSError.make(Compiler.OPTIMIZE_LOOP_ERROR, "5");
    compiler.report(error);
    assertEquals(1, compiler.getErrorCount());
  }

  // Covers: getTypeRegistry() lazy-init branch
  @Test
  public void testGetTypeRegistry_lazyInit_notNull() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    assertNotNull(registry);
  }

  // Covers: getCodingConvention() default convention branch
  @Test
  public void testGetCodingConvention_defaultConvention_notNull() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    CodingConvention convention = compiler.getCodingConvention();
    assertNotNull(convention);
  }

  // Covers: getSourceMap() before any init returns null
  @Test
  public void testGetSourceMap_beforeInit_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceMap());
  }

  // Covers: setProgress() branch newProgress > 1.0 clamps to 1.0
  @Test
  public void testSetProgress_greaterThanOne_clampsToOne() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(1.5);
    assertEquals(1.0, compiler.getProgress(), 1e-9);
  }

  // Covers: setProgress() branch newProgress < 0.0 clamps to 0.0
  @Test
  public void testSetProgress_lessThanZero_clampsToZero() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(-0.5);
    assertEquals(0.0, compiler.getProgress(), 1e-9);
  }

  // Covers: setProgress() branch within [0,1] range keeps exact value
  @Test
  public void testSetProgress_withinRange_setsExactValue() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.setProgress(0.5);
    assertEquals(0.5, compiler.getProgress(), 1e-9);
  }

  // Covers: getUniqueNameIdSupplier() increments uniqueNameId starting at 0
  @Test
  public void testGetUniqueNameIdSupplier_incrementsEachCall() throws Throwable {
    Compiler compiler = new Compiler();
    Supplier<String> supplier = compiler.getUniqueNameIdSupplier();
    String first = supplier.get();
    String second = supplier.get();
    assertEquals("0", first);
    assertEquals("1", second);
  }

  // Covers: newTracer() returns non-null Tracer
  @Test
  public void testNewTracer_returnsNonNullTracer() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    Tracer tracer = compiler.newTracer("testPass");
    assertNotNull(tracer);
  }

  // Covers: getDegenerateModuleGraph() after compile returns non-null graph
  @Test
  public void testGetDegenerateModuleGraph_afterCompile_returnsGraph() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, options);
    JSModuleGraph graph = compiler.getDegenerateModuleGraph();
    assertNotNull(graph);
  }

  // Covers: CodeBuilder.append() without newline updates column count only
  @Test
  public void testCodeBuilderAppend_noNewline_updatesColumnCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello");
    assertEquals(0, cb.getLineIndex());
    assertEquals(5, cb.getColumnIndex());
  }

  // Covers: CodeBuilder.append() with a single newline increments line count
  @Test
  public void testCodeBuilderAppend_withNewline_updatesLineAndColumnCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("line1\nline2");
    assertEquals(1, cb.getLineIndex());
    assertEquals(5, cb.getColumnIndex());
  }

  // Covers: CodeBuilder.append() loop over multiple newlines
  @Test
  public void testCodeBuilderAppend_multipleNewlines_countsAllLines() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("a\nb\nc");
    assertEquals(2, cb.getLineIndex());
    assertEquals(1, cb.getColumnIndex());
  }

  // Covers: CodeBuilder.reset() clears text but keeps line count
  @Test
  public void testCodeBuilderReset_clearsTextKeepsLineCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc\n");
    cb.reset();
    assertEquals(0, cb.getLength());
    assertEquals(1, cb.getLineIndex());
  }

  // Covers: CodeBuilder.toString() returns accumulated text
  @Test
  public void testCodeBuilderToString_returnsAppendedText() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("foo");
    cb.append("bar");
    assertEquals("foobar", cb.toString());
  }

  // Covers: CodeBuilder.getLength() reflects appended text length
  @Test
  public void testCodeBuilderGetLength_reflectsAppendedText() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("12345");
    assertEquals(5, cb.getLength());
  }

  // Bug hunt: endsWith() should return true when buffer text exactly equals the suffix
  // (per its contract "Determines whether the text ends with the given suffix"),
  // but the off-by-one comparison (sb.length() > suffix.length()) wrongly excludes
  // the equal-length case.
  @Test
  public void testCodeBuilderEndsWith_exactMatch_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc");
    assertTrue(cb.endsWith("abc"));
  }

  // Covers: CodeBuilder.endsWith() true branch with proper (shorter) suffix
  @Test
  public void testCodeBuilderEndsWith_properSuffix_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello world");
    assertTrue(cb.endsWith("world"));
  }

  // Covers: CodeBuilder.endsWith() false branch, non-matching suffix
  @Test
  public void testCodeBuilderEndsWith_notSuffix_returnsFalse() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("hello");
    assertFalse(cb.endsWith("world"));
  }

  // Covers: CodeBuilder.endsWith() false branch, suffix longer than buffered text
  @Test
  public void testCodeBuilderEndsWith_suffixLongerThanText_returnsFalse() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("ab");
    assertFalse(cb.endsWith("abcdef"));
  }
}
