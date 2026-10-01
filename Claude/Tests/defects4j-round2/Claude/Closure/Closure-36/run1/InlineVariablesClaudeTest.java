package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import org.junit.Test;
import static org.junit.Assert.*;

public class InlineVariablesClaudeTest {

  private String inlineAndGetSource(String js, InlineVariables.Mode mode,
      boolean inlineAllStrings) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("test.js", js),
        options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    InlineVariables pass = new InlineVariables(compiler, mode, inlineAllStrings);
    pass.process(externsRoot, mainRoot);
    return compiler.toSource();
  }

  private String normalize(String s) {
    return s.replaceAll("\\s+", "");
  }

  private int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  // ครอบคลุม Mode.ALL กับโปรแกรมว่าง (getFilterForMode: case ALL)
  @Test
  public void testProcess_modeAll_emptyProgram_producesEmptyOutput() throws Throwable {
    String out = inlineAndGetSource("", InlineVariables.Mode.ALL, true);
    assertEquals("", out.trim());
  }

  // ครอบคลุม Mode.LOCALS_ONLY กับโปรแกรมว่าง (getFilterForMode: case LOCALS_ONLY)
  @Test
  public void testProcess_modeLocalsOnly_emptyProgram_producesEmptyOutput() throws Throwable {
    String out = inlineAndGetSource("", InlineVariables.Mode.LOCALS_ONLY, true);
    assertEquals("", out.trim());
  }

  // ครอบคลุม Mode.CONSTANTS_ONLY กับโปรแกรมว่าง (getFilterForMode: case CONSTANTS_ONLY)
  @Test
  public void testProcess_modeConstantsOnly_emptyProgram_producesEmptyOutput() throws Throwable {
    String out = inlineAndGetSource("", InlineVariables.Mode.CONSTANTS_ONLY, true);
    assertEquals("", out.trim());
  }

  // ตัวแปร local ใช้ครั้งเดียว: refCount==firstRefAfterInit(2) -> ถูก inline
  @Test
  public void testProcess_modeAll_singleUseLocalVariable_inlinedIntoReturn() throws Throwable {
    String js = "function f(){ var x = 1; return x; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return1"));
    assertFalse(out.contains("varx"));
  }

  // ตัวแปร global ใช้ครั้งเดียว ภายใต้ Mode.ALL ก็ถูก inline เช่นกัน (filter alwaysTrue)
  @Test
  public void testProcess_modeAll_singleUseGlobalVariable_inlinedIntoReturn() throws Throwable {
    String js = "var g = 5; function h(){ return g; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("functionh(){return5}"));
    assertFalse(out.contains("varg"));
  }

  // IdentifyLocals: ตัวแปร global ต้องไม่ถูก inline ภายใต้ Mode.LOCALS_ONLY
  @Test
  public void testProcess_modeLocalsOnly_globalSingleUseVariable_notInlined() throws Throwable {
    String js = "var g = 5; function h(){ return g; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.LOCALS_ONLY, true));
    assertTrue(out.contains("varg=5"));
    assertTrue(out.contains("returng"));
  }

  // IdentifyLocals: ตัวแปร local ต้องถูก inline ภายใต้ Mode.LOCALS_ONLY
  @Test
  public void testProcess_modeLocalsOnly_localSingleUseVariable_inlined() throws Throwable {
    String js = "function f(){ var x = 2; return x; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.LOCALS_ONLY, true));
    assertTrue(out.contains("return2"));
    assertFalse(out.contains("varx"));
  }

  // IdentifyConstants: ตัวแปรธรรมดาไม่ใช่ const ต้องไม่ถูก inline ภายใต้ Mode.CONSTANTS_ONLY
  @Test
  public void testProcess_modeConstantsOnly_nonConstSingleUseVariable_notInlined() throws Throwable {
    String js = "var g = 3; function h(){ return g; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.CONSTANTS_ONLY, true));
    assertTrue(out.contains("varg=3"));
    assertTrue(out.contains("returng"));
  }

  // isImmutableAndWellDefinedVariable: ตัวเลขคงที่ถูกใช้หลายครั้ง -> inline ทุกจุดใช้งาน
  @Test
  public void testProcess_modeAll_multipleReferencesNumberLiteral_inlinedAtAllUsages() throws Throwable {
    String js = "function f(){ var x = 7; return x + x; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return7+7"));
    assertFalse(out.contains("varx"));
  }

  // ไม่มี branch ใดใน inlineNonConstants ทำงาน เพราะตัวแปรไม่ถูกอ้างอิงเลย -> คงเดิม
  @Test
  public void testProcess_modeAll_unusedVariable_declarationUnchanged() throws Throwable {
    String js = "function f(){ var unused = 1; return 2; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("varunused=1"));
    assertTrue(out.contains("return2"));
  }

  // canMoveAggressively: object literal (literal value) ใช้ครั้งเดียว -> inline
  @Test
  public void testProcess_modeAll_objectLiteralSingleUse_inlined() throws Throwable {
    String js = "function f(){ var obj = {}; return obj; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return{}"));
    assertFalse(out.contains("varobj"));
  }

  // canMoveAggressively: array literal ใช้ครั้งเดียว -> inline
  @Test
  public void testProcess_modeAll_arrayLiteralSingleUse_inlined() throws Throwable {
    String js = "function f(){ var arr = [1,2,3]; return arr; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return[1,2,3]"));
    assertFalse(out.contains("vararr"));
  }

  // canInline: value เป็น GetProp และถูกเรียกเป็น callee -> ห้าม inline
  @Test
  public void testProcess_modeAll_getPropUsedAsCallTarget_notInlined() throws Throwable {
    String js = "var b = {}; function f(){ var a = b.c; a(); }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("vara=b.c"));
    assertTrue(out.contains("a()"));
  }

  // canInline: value เป็น GetProp แต่ใช้เป็น argument (ไม่ใช่ callee) -> อนุญาต inline
  @Test
  public void testProcess_modeAll_getPropUsedAsCallArgument_inlined() throws Throwable {
    String js = "var b = {}; function g(){ var a = b.c; foo(a); }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("foo(b.c)"));
    assertFalse(out.contains("vara="));
  }

  // declaration != init: ประกาศแยกจาก assignment แล้ว refCount==firstRefAfterInit(3) -> inline เข้า read
  @Test
  public void testProcess_modeAll_splitDeclarationAndAssignment_inlinedIntoRead() throws Throwable {
    String js = "function f(){ var y; y = 5; return y; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return5"));
    assertFalse(out.contains("vary"));
  }

  // function declaration ถูกเรียกครั้งเดียว -> inline เป็น function expression ที่จุดเรียก
  @Test
  public void testProcess_modeAll_functionDeclarationSingleCall_inlinedAsExpression() throws Throwable {
    String js = "function foo(){ return 1; } foo();";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return1"));
    assertFalse(out.contains("}foo();"));
  }

  // function expression (ไม่ใช่ declaration) ถูกเรียกครั้งเดียว -> inline และลบ var declaration
  @Test
  public void testProcess_modeAll_functionExpressionSingleCall_inlinedAndDeclarationRemoved() throws Throwable {
    String js = "function g(){ var fn = function(){ return 1; }; return fn(); }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertFalse(out.contains("varfn"));
    assertTrue(out.contains("return1"));
    assertFalse(out.contains("returnfn()"));
  }

  // isAssignedOnceInLifetime ล้มเหลว (reassign ภายใต้เงื่อนไข) -> ไม่ inline เลย
  @Test
  public void testProcess_modeAll_reassignedVariable_remainsUninlined() throws Throwable {
    String js = "function f(cond){ var x = 1; if (cond) { x = 2; } return x; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("varx=1"));
    assertTrue(out.contains("x=2"));
    assertTrue(out.contains("returnx"));
  }

  // canInline: ข้าม basic block (declaration นอก if, reference ใน if) -> ห้าม inline
  @Test
  public void testProcess_modeAll_variableUsedAcrossBasicBlockBoundary_remainsUninlined() throws Throwable {
    String js = "function f(cond){ var x = 1; if (cond) { return x; } return 0; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("varx=1"));
    assertTrue(out.contains("returnx"));
  }

  // isStringWorthInlining: string ยาว (len>18) ใช้หลายครั้ง และ inlineAllStrings=false -> ไม่คุ้ม ไม่ inline
  @Test
  public void testProcess_modeAll_longStringMultipleUses_notInlinedDueToSizeHeuristic() throws Throwable {
    String js = "function f(){ var s = 'abcdefghijklmnopqrstuvwxyz1234'; return s + s; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, false));
    assertTrue(out.contains("s+s"));
    assertEquals(1, countOccurrences(out, "abcdefghijklmnopqrstuvwxyz1234"));
  }

  // inlineAllStrings=true บังคับให้คุ้มค่าเสมอ -> string ยาวถูก inline ทั้งสองจุด
  @Test
  public void testProcess_modeAll_longStringMultipleUsesWithInlineAllStrings_inlined() throws Throwable {
    String js = "function f(){ var s = 'abcdefghijklmnopqrstuvwxyz1234'; return s + s; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertFalse(out.contains("s+s"));
    assertEquals(2, countOccurrences(out, "abcdefghijklmnopqrstuvwxyz1234"));
  }

  // string สั้น (noInlineBytes>=inlineBytes) ต้องคุ้มค่า inline แม้ inlineAllStrings=false
  @Test
  public void testProcess_modeAll_shortStringMultipleUses_inlinedDespiteInlineAllStringsFalse() throws Throwable {
    String js = "function f(){ var s = 'ab'; return s + s; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, false));
    assertFalse(out.contains("s+s"));
    assertEquals(2, countOccurrences(out, "ab"));
  }

  // ตัวแปร local ในฟังก์ชันซ้อน (nested scope) ใช้ครั้งเดียว -> inline
  @Test
  public void testProcess_modeAll_nestedFunctionLocalVariable_inlined() throws Throwable {
    String js = "function outer(a){ function inner(){ var z = 9; return z; } "
        + "return inner() + a; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertTrue(out.contains("return9"));
    assertFalse(out.contains("varz"));
  }

  // ตัวแปรที่ยังไม่ถูกกำหนดค่าเลย (never initialized) ใช้ครั้งเดียว -> ยังคง inline ได้ตามสัญญา
  @Test
  public void testProcess_modeAll_uninitializedVariableSingleRead_declarationRemoved() throws Throwable {
    String js = "function f(p){ var y; return p + y; }";
    String out = normalize(inlineAndGetSource(js, InlineVariables.Mode.ALL, true));
    assertFalse(out.contains("vary;"));
  }
}
