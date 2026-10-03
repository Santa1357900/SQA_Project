package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

public class CompilerClaudeTest {

  // CodeBuilder.append: single string, no newline -> length/content/colCount updated
  @Test
  public void testCodeBuilderAppend_noNewline_tracksColumnIndex() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("abc");
    assertEquals("abc", cb.toString());
    assertEquals(3, cb.getLength());
    assertEquals(3, cb.getColumnIndex());
    assertEquals(0, cb.getLineIndex());
  }

  // CodeBuilder.append: multiple newlines -> lineCount incremented, colCount from last line
  @Test
  public void testCodeBuilderAppend_multipleLines_tracksLineAndColumnIndex() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("line1\nline2\n");
    assertEquals("line1\nline2\n", cb.toString());
    assertEquals(2, cb.getLineIndex());
    assertEquals(0, cb.getColumnIndex());
  }

  // CodeBuilder.endsWith: buffer exactly equal to suffix must be true (String.endsWith semantics)
  @Test
  public void testCodeBuilderEndsWith_exactMatch_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append(";");
    assertTrue(cb.endsWith(";"));
  }

  // CodeBuilder.endsWith: buffer longer than suffix and matches -> true branch
  @Test
  public void testCodeBuilderEndsWith_longerContentWithSuffix_returnsTrue() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("foo;");
    assertTrue(cb.endsWith(";"));
  }

  // CodeBuilder.endsWith: suffix longer than content -> false branch
  @Test
  public void testCodeBuilderEndsWith_suffixLongerThanContent_returnsFalse() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("ab");
    assertFalse(cb.endsWith("abc"));
  }

  // CodeBuilder.reset: clears text but javadoc says line count unchanged
  @Test
  public void testCodeBuilderReset_clearsContentKeepsLineCount() throws Throwable {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("line1\n");
    cb.reset();
    assertEquals("", cb.toString());
    assertEquals(1, cb.getLineIndex());
  }

  // Default constructor -> getErrorManager() lazily initializes and is never null
  @Test
  public void testConstructorDefault_getErrorManagerNotNull() throws Throwable {
    Compiler compiler = new Compiler();
    ErrorManager em = compiler.getErrorManager();
    assertNotNull(em);
  }

  // PrintStream constructor -> error manager still initializes correctly
  @Test
  public void testConstructorWithPrintStream_getErrorManagerNotNull() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    Compiler compiler = new Compiler(ps);
    assertNotNull(compiler.getErrorManager());
  }

  // setErrorManager(null) must throw per Preconditions.checkNotNull contract
  @Test
  public void testSetErrorManager_null_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    try {
      compiler.setErrorManager(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  // initOptions stores the options instance, retrievable via getOptions()
  @Test
  public void testInitOptions_setsOptionsField() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    assertSame(options, compiler.getOptions());
  }

  // initInputsByNameMap: duplicate extern names -> DUPLICATE_EXTERN_INPUT reported
  @Test
  public void testInit_duplicateExternInput_reportsError() throws Throwable {
    JSSourceFile e1 = JSSourceFile.fromCode("dup.js", "");
    JSSourceFile e2 = JSSourceFile.fromCode("dup.js", "");
    JSSourceFile in = JSSourceFile.fromCode("in.js", "var a=1;");
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[] {e1, e2}, new JSSourceFile[] {in}, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // initInputsByNameMap: duplicate source input names -> DUPLICATE_INPUT reported
  @Test
  public void testInit_duplicateSourceInput_reportsError() throws Throwable {
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile in1 = JSSourceFile.fromCode("dup.js", "var a=1;");
    JSSourceFile in2 = JSSourceFile.fromCode("dup.js", "var b=2;");
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {in1, in2}, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // checkFirstModule: empty module list -> EMPTY_MODULE_LIST_ERROR, compile stops early
  @Test
  public void testCompile_emptyModuleList_reportsErrorAndHasErrors() throws Throwable {
    Compiler compiler = new Compiler();
    Result result = compiler.compile(new JSSourceFile[0], new JSModule[0], new CompilerOptions());
    assertNotNull(result);
    assertTrue(compiler.hasErrors());
    assertTrue(compiler.getErrorCount() > 0);
  }

  // checkFirstModule: first module empty AND size>1 -> EMPTY_ROOT_MODULE_ERROR
  @Test
  public void testCompile_firstModuleEmptyWithMultipleModules_reportsError() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule m2 = new JSModule("m2");
    m2.add(JSSourceFile.fromCode("b.js", "var b=1;"));
    Compiler compiler = new Compiler();
    compiler.compile(new JSSourceFile[0], new JSModule[] {m1, m2}, new CompilerOptions());
    assertTrue(compiler.hasErrors());
  }

  // checkFirstModule: single empty module is allowed (size==1) -> no error, placeholder filled
  @Test
  public void testCompile_singleEmptyModule_noErrorForEmptyFirstModule() throws Throwable {
    JSModule m1 = new JSModule("m1");
    Compiler compiler = new Compiler();
    compiler.compile(new JSSourceFile[0], new JSModule[] {m1}, new CompilerOptions());
    assertFalse(compiler.hasErrors());
  }

  // compile() full pipeline: valid code produces no errors
  @Test
  public void testCompile_validCode_noErrors() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
    Result result = compiler.compile(extern, input, new CompilerOptions());
    assertNotNull(result);
    assertFalse(compiler.hasErrors());
    assertEquals(0, compiler.getErrorCount());
  }

  // compile() full pipeline: syntax error produces at least one error
  @Test
  public void testCompile_syntaxError_hasErrors() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = ;");
    compiler.compile(extern, input, new CompilerOptions());
    assertTrue(compiler.hasErrors());
    assertTrue(compiler.getErrorCount() > 0);
  }

  // compile() can only be called once: second invocation throws IllegalStateException
  @Test
  public void testCompile_calledTwice_throwsIllegalStateException() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
    CompilerOptions options = new CompilerOptions();
    compiler.compile(extern, input, options);
    try {
      compiler.compile(extern, input, options);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  // parse(JSSourceFile): returns a SCRIPT root node for valid code
  @Test
  public void testParse_validCode_returnsScriptNode() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = compiler.parse(JSSourceFile.fromCode("t.js", "var y = 2;"));
    assertNotNull(root);
    assertEquals(Token.SCRIPT, root.getType());
  }

  // toSource(): after successful compile, output contains the declared variable
  @Test
  public void testToSource_afterCompile_containsVariableName() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var zzz = 5;");
    compiler.compile(extern, input, new CompilerOptions());
    String src = compiler.toSource();
    assertTrue(src.contains("zzz"));
  }

  // toSourceArray(): length matches number of source inputs
  @Test
  public void testToSourceArray_afterCompile_lengthMatchesInputCount() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var q = 1;");
    compiler.compile(extern, input, new CompilerOptions());
    String[] arr = compiler.toSourceArray();
    assertEquals(1, arr.length);
  }

  // toSource(JSModule): module with zero inputs returns empty string branch
  @Test
  public void testToSource_moduleWithNoInputs_returnsEmptyString() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JSModule empty = new JSModule("empty");
    String src = compiler.toSource(empty);
    assertEquals("", src);
  }

  // toSourceArray(JSModule): module with zero inputs returns empty array branch
  @Test
  public void testToSourceArray_moduleWithNoInputs_returnsEmptyArray() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JSModule empty = new JSModule("empty");
    String[] arr = compiler.toSourceArray(empty);
    assertEquals(0, arr.length);
  }

  // getInput: unknown name returns null
  @Test
  public void testGetInput_unknownName_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var a=1;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    assertNull(compiler.getInput("does_not_exist.js"));
  }

  // newExternInput: new name is registered and retrievable via getInput
  @Test
  public void testNewExternInput_newName_addsToInputsByName() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var a=1;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    CompilerInput ci = compiler.newExternInput("new_extern.js");
    assertNotNull(ci);
    assertSame(ci, compiler.getInput("new_extern.js"));
  }

  // newExternInput: duplicate name throws IllegalArgumentException
  @Test
  public void testNewExternInput_duplicateName_throwsIllegalArgumentException() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var a=1;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    try {
      compiler.newExternInput("extern.js");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Conflicting"));
    }
  }

  // removeInput: non-existent name is a no-op, getInput remains null
  @Test
  public void testRemoveInput_nonExistentName_remainsNull() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var a=1;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    compiler.removeInput("nonexistent.js");
    assertNull(compiler.getInput("nonexistent.js"));
  }

  // rebuildInputsFromModules: picks up inputs added to module after init
  @Test
  public void testRebuildInputsFromModules_afterAddingInput_updatesInputsForTesting() throws Throwable {
    JSModule module = new JSModule("mod1");
    module.add(JSSourceFile.fromCode("a.js", "var a=1;"));
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[0], new JSModule[] {module}, new CompilerOptions());
    module.add(JSSourceFile.fromCode("b.js", "var b=2;"));
    compiler.rebuildInputsFromModules();
    List<CompilerInput> inputsForTesting = compiler.getInputsForTesting();
    assertEquals(2, inputsForTesting.size());
  }

  // getTypeRegistry: lazily created once, subsequent calls return same instance
  @Test
  public void testGetTypeRegistry_calledTwice_returnsSameInstance() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JSTypeRegistry r1 = compiler.getTypeRegistry();
    JSTypeRegistry r2 = compiler.getTypeRegistry();
    assertNotNull(r1);
    assertSame(r1, r2);
  }

  // getReverseAbstractInterpreter: lazily created once, subsequent calls return same instance
  @Test
  public void testGetReverseAbstractInterpreter_calledTwice_returnsSameInstance() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    ReverseAbstractInterpreter i1 = compiler.getReverseAbstractInterpreter();
    ReverseAbstractInterpreter i2 = compiler.getReverseAbstractInterpreter();
    assertNotNull(i1);
    assertSame(i1, i2);
  }

  // report(): a reported error increments the error count and sets hasErrors()
  @Test
  public void testReport_addsErrorAndIncrementsCount() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.getErrorManager();
    compiler.report(JSError.make(Compiler.MISSING_ENTRY_ERROR, "foo"));
    assertEquals(1, compiler.getErrorCount());
    assertTrue(compiler.hasErrors());
  }

  // getWarnings/getWarningCount: initially no warnings have been reported
  @Test
  public void testGetWarnings_initiallyEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.getErrorManager();
    assertEquals(0, compiler.getWarnings().length);
    assertEquals(0, compiler.getWarningCount());
  }

  // getMessages(): mirrors getErrors() length after a reported error
  @Test
  public void testGetMessages_matchesGetErrorsLength() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.getErrorManager();
    compiler.report(JSError.make(Compiler.MISSING_ENTRY_ERROR, "bar"));
    assertEquals(compiler.getErrors().length, compiler.getMessages().length);
  }

  // getSourceLine: lineNumber < 1 returns null immediately
  @Test
  public void testGetSourceLine_zeroLineNumber_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceLine("any.js", 0));
  }

  // getSourceLine: valid line number on a known source returns its text
  @Test
  public void testGetSourceLine_validLineNumber_returnsLineContent() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var x = 1;\nvar y = 2;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    String line = compiler.getSourceLine("in.js", 1);
    assertNotNull(line);
    assertTrue(line.contains("var x = 1;"));
  }

  // getSourceLine: unknown source name returns null
  @Test
  public void testGetSourceLine_unknownSourceName_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
    JSSourceFile input = JSSourceFile.fromCode("in.js", "var a=1;");
    compiler.init(new JSSourceFile[] {extern}, new JSSourceFile[] {input}, new CompilerOptions());
    assertNull(compiler.getSourceLine("unknown.js", 1));
  }

  // getSourceRegion: negative lineNumber returns null immediately
  @Test
  public void testGetSourceRegion_negativeLineNumber_returnsNull() throws Throwable {
    Compiler compiler = new Compiler();
    assertNull(compiler.getSourceRegion("any.js", -1));
  }

  // getAstDotGraph: before any parse (jsRoot null) returns empty string
  @Test
  public void testGetAstDotGraph_beforeParse_returnsEmptyString() throws Throwable {
    Compiler compiler = new Compiler();
    String dot = compiler.getAstDotGraph();
    assertEquals("", dot);
  }

  // setLoggingLevel: updates the shared compiler package logger's level
  @Test
  public void testSetLoggingLevel_updatesLoggerLevel() throws Throwable {
    Compiler.setLoggingLevel(Level.WARNING);
    Logger logger = Logger.getLogger("com.google.javascript.jscomp");
    assertEquals(Level.WARNING, logger.getLevel());
  }

  // getState: after successful compile, returns a non-null intermediate state
  @Test
  public void testGetState_afterCompile_returnsNonNullState() throws Throwable {
    Compiler compiler = new Compiler();
    JSSourceFile extern = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
    compiler.compile(extern, input, new CompilerOptions());
    Compiler.IntermediateState state = compiler.getState();
    assertNotNull(state);
  }
}
