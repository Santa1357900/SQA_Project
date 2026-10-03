package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NormalizeClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  // Static field MAKE_LOCAL_NAMES_UNIQUE must be true per class contract.
  @Test
  public void testMAKE_LOCAL_NAMES_UNIQUE_StaticField_IsTrue() throws Throwable {
    assertTrue(Normalize.MAKE_LOCAL_NAMES_UNIQUE);
  }

  // CATCH_BLOCK_VAR_ERROR constant must be a usable, non-null DiagnosticType.
  @Test
  public void testCATCH_BLOCK_VAR_ERROR_StaticField_IsNotNullDiagnosticType() throws Throwable {
    assertNotNull(Normalize.CATCH_BLOCK_VAR_ERROR);
    assertTrue(Normalize.CATCH_BLOCK_VAR_ERROR instanceof DiagnosticType);
  }

  // Constructor must produce an object that satisfies the CompilerPass contract.
  @Test
  public void testConstructor_NewInstance_ImplementsCompilerPass() throws Throwable {
    Normalize normalize = new Normalize(compiler, false);
    assertTrue(normalize instanceof CompilerPass);
  }

  // var a=0,b=1; must be split into two separate single-declaration VAR statements.
  @Test
  public void testParseAndNormalizeTestCode_MultipleVarDeclaration_SplitsIntoSeparateVarStatements() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var a = 0, b = 1;", "t");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertNull(first.getFirstChild().getNext());
    Node second = first.getNext();
    assertEquals(Token.VAR, second.getType());
    assertNull(second.getFirstChild().getNext());
  }

  // A single-declaration VAR should remain a single statement (no split needed).
  @Test
  public void testParseAndNormalizeTestCode_SingleVarDeclaration_RemainsSingleStatement() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var a = 0;", "t");
    Node first = js.getFirstChild();
    assertEquals(Token.VAR, first.getType());
    assertNull(first.getNext());
  }

  // WHILE must be converted into a FOR with EMPTY init and EMPTY increment slots.
  @Test
  public void testParseAndNormalizeTestCode_WhileLoop_ConvertedToForWithEmptySlots() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "while (a) { b(); }", "t");
    Node forNode = js.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    Node init = forNode.getFirstChild();
    assertEquals(Token.EMPTY, init.getType());
    Node cond = init.getNext();
    assertEquals(Token.NAME, cond.getType());
    assertEquals(Token.EMPTY, cond.getNext().getType());
  }

  // for (var k in obj) must extract "var k;" as a statement before the loop.
  @Test
  public void testParseAndNormalizeTestCode_ForInWithVar_ExtractsVarDeclarationBeforeLoop() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "for (var k in obj) { c(); }", "t");
    Node varNode = js.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    Node forNode = varNode.getNext();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.NAME, forNode.getFirstChild().getType());
  }

  // for (a in b) with an already-declared name must not be touched.
  @Test
  public void testParseAndNormalizeTestCode_ForInWithoutVar_LoopUnchanged() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "for (a in b) { c(); }", "t");
    Node forNode = js.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    Node name = forNode.getFirstChild();
    assertEquals(Token.NAME, name.getType());
    assertEquals("a", name.getString());
  }

  // Non-empty FOR initializer expression must be extracted as its own statement.
  @Test
  public void testParseAndNormalizeTestCode_ForInitializerExpression_ExtractedBeforeLoop() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "for (x = 0; x < 10; x++) { y(); }", "t");
    Node extracted = js.getFirstChild();
    Node forNode = extracted.getNext();
    assertEquals(Token.FOR, forNode.getType());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
    assertTrue(extracted.getType() != Token.FOR);
  }

  // for (;;) already has an EMPTY initializer, so nothing is extracted.
  @Test
  public void testParseAndNormalizeTestCode_ForWithEmptyInitializer_NotExtracted() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "for (;;) { c(); }", "t");
    Node forNode = js.getFirstChild();
    assertEquals(Token.FOR, forNode.getType());
    assertNull(forNode.getNext());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // A labeled non-block/non-loop statement must be wrapped in a BLOCK.
  @Test
  public void testParseAndNormalizeTestCode_LabelOnExpressionStatement_WrappedInBlock() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: x = 1;", "t");
    Node label = js.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    assertEquals(Token.BLOCK, label.getLastChild().getType());
  }

  // A label already on a BLOCK must not be wrapped a second time.
  @Test
  public void testParseAndNormalizeTestCode_LabelOnBlock_NotDoubleWrapped() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: { x = 1; }", "t");
    Node label = js.getFirstChild();
    Node block = label.getLastChild();
    assertEquals(Token.BLOCK, block.getType());
    assertTrue(block.getFirstChild().getType() != Token.BLOCK);
  }

  // A label on a FOR loop must remain unwrapped.
  @Test
  public void testParseAndNormalizeTestCode_LabelOnFor_NotWrapped() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: for (;;) { a(); }", "t");
    Node label = js.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    assertEquals(Token.FOR, label.getLastChild().getType());
  }

  // A label on a WHILE must convert to FOR but remain unwrapped by a BLOCK.
  @Test
  public void testParseAndNormalizeTestCode_LabelOnWhile_ConvertedToForNotWrapped() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: while (a) { b(); }", "t");
    Node label = js.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    assertEquals(Token.FOR, label.getLastChild().getType());
  }

  // A label nested on another label must remain unwrapped (LABEL case in switch).
  @Test
  public void testParseAndNormalizeTestCode_LabelOnLabel_NotWrapped() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: bar: for (;;) {}", "t");
    Node outer = js.getFirstChild();
    assertEquals(Token.LABEL, outer.getType());
    Node inner = outer.getLastChild();
    assertEquals(Token.LABEL, inner.getType());
    assertEquals(Token.FOR, inner.getLastChild().getType());
  }

  // A label on a DO loop must remain unwrapped.
  @Test
  public void testParseAndNormalizeTestCode_LabelOnDo_NotWrapped() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "foo: do { a(); } while (b);", "t");
    Node label = js.getFirstChild();
    assertEquals(Token.LABEL, label.getType());
    assertEquals(Token.DO, label.getLastChild().getType());
  }



  // A hoisted nested function declaration must be moved to the front of the function body.
  @Test
  public void testParseAndNormalizeTestCode_NestedFunctionDeclaration_MovedToFrontOfBody() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "function top() { a(); function b() {} }", "t");
    Node topFn = js.getFirstChild();
    Node body = topFn.getLastChild();
    Node first = body.getFirstChild();
    assertEquals(Token.FUNCTION, first.getType());
    assertTrue(first.getNext().getType() != Token.FUNCTION);
  }

  // A var name in ALL_CAPS convention must be annotated as a constant name.
  @Test
  public void testParseAndNormalizeTestCode_AllCapsVarName_MarkedConstant() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var XYZ = 1;", "t");
    Node nameNode = js.getFirstChild().getFirstChild();
    assertTrue(nameNode.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // A lower-case var name must NOT be annotated as a constant name.
  @Test
  public void testParseAndNormalizeTestCode_LowerCaseVarName_NotMarkedConstant() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var abc = 1;", "t");
    Node nameNode = js.getFirstChild().getFirstChild();
    assertFalse(nameNode.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // An ALL_CAPS property name on the right side of a GETPROP must be marked constant.
  @Test
  public void testParseAndNormalizeTestCode_GetPropAllCapsProperty_MarkedConstant() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "a.XYZ;", "t");
    Node getprop = js.getFirstChild().getFirstChild();
    assertEquals(Token.GETPROP, getprop.getType());
    Node prop = getprop.getLastChild();
    assertTrue(prop.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // An ALL_CAPS object literal key must be marked constant.
  @Test
  public void testParseAndNormalizeTestCode_ObjectLiteralAllCapsKey_MarkedConstant() throws Throwable {
    Node js = Normalize.parseAndNormalizeTestCode(compiler, "var o = {XYZ: 1};", "t");
    Node objLit = js.getFirstChild().getFirstChild().getFirstChild();
    Node key = objLit.getFirstChild();
    assertEquals(Token.STRING, key.getType());
    assertTrue(key.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

















  private boolean containsAssign(Node n) {
    if (n.getType() == Token.ASSIGN) {
      return true;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (containsAssign(c)) {
        return true;
      }
    }
    return false;
  }
}
