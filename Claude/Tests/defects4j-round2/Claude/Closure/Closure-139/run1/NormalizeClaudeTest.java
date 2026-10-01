package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class NormalizeClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  // shouldTraverse: โหนดธรรมดาที่ไม่ใช่ LABEL/BLOCK/FUNCTION ไม่ถูกแก้ไข และคืนค่า true เสมอ
  @Test
  public void testShouldTraverse_plainNameNode_returnsTrueNoOp() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node nameNode = IR.name("x");

    boolean result = ns.shouldTraverse(null, nameNode, null);

    assertTrue(result);
    assertEquals(Token.NAME, nameNode.getType());
  }

  // visit: WHILE + assertOnChange=true -> ต้อง throw เพราะมีการแปลงเป็น FOR
  @Test
  public void testVisit_whileStatement_assertOnChangeTrue_throws() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node whileNode = new Node(Token.WHILE, IR.name("c"), new Node(Token.BLOCK));

    try {
      ns.visit(null, whileNode, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("WHILE node"));
    }
  }

  // visit: WHILE + assertOnChange=false -> ต้องถูกแปลงเป็น FOR(EMPTY, cond, EMPTY, body)
  @Test
  public void testVisit_whileStatement_assertOnChangeFalse_convertsToFor() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);
    Node cond = IR.name("c");
    Node body = new Node(Token.BLOCK);
    Node whileNode = new Node(Token.WHILE, cond, body);

    ns.visit(null, whileNode, null);

    assertEquals(Token.FOR, whileNode.getType());
    Node first = whileNode.getFirstChild();
    Node second = first.getNext();
    Node third = second.getNext();
    Node fourth = third.getNext();
    assertEquals(Token.EMPTY, first.getType());
    assertSame(cond, second);
    assertEquals(Token.EMPTY, third.getType());
    assertSame(body, fourth);
    assertNull(fourth.getNext());
  }

  // visit: ชนิด node อื่นที่ไม่ใช่ WHILE -> switch default, ไม่มีการแก้ไขใดๆ
  @Test
  public void testVisit_nonWhileStatement_noOp() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node nameNode = IR.name("x");

    ns.visit(null, nameNode, null);

    assertEquals(Token.NAME, nameNode.getType());
  }

  // normalizeLabels: last child เป็น BLOCK แล้ว -> ไม่มีการแก้ไข
  @Test
  public void testNormalizeLabels_lastChildBlock_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node labelName = IR.name("L");
    Node bodyBlock = new Node(Token.BLOCK);
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(bodyBlock, labelName);

    boolean result = ns.shouldTraverse(null, label, null);

    assertTrue(result);
    assertSame(bodyBlock, label.getLastChild());
  }

  // normalizeLabels: last child เป็น FOR (ที่ init เป็น EMPTY อยู่แล้ว) -> ไม่มีการแก้ไข
  @Test
  public void testNormalizeLabels_lastChildFor_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node e1 = new Node(Token.EMPTY);
    Node forNode = new Node(Token.FOR, e1);
    Node e2 = new Node(Token.EMPTY);
    forNode.addChildAfter(e2, e1);
    Node e3 = new Node(Token.EMPTY);
    forNode.addChildAfter(e3, e2);
    Node forBody = new Node(Token.BLOCK);
    forNode.addChildAfter(forBody, e3);

    Node labelName = IR.name("L");
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(forNode, labelName);

    ns.shouldTraverse(null, label, null);

    assertSame(forNode, label.getLastChild());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // normalizeLabels: last child เป็น WHILE -> ไม่มีการแก้ไข (ไม่ถูกห่อ BLOCK)
  @Test
  public void testNormalizeLabels_lastChildWhile_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node whileNode = new Node(Token.WHILE, IR.name("c"), new Node(Token.BLOCK));
    Node labelName = IR.name("L");
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(whileNode, labelName);

    ns.shouldTraverse(null, label, null);

    assertSame(whileNode, label.getLastChild());
    assertEquals(Token.WHILE, whileNode.getType());
  }

  // normalizeLabels: last child เป็น DO -> ไม่มีการแก้ไข
  @Test
  public void testNormalizeLabels_lastChildDo_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), IR.name("c"));
    Node labelName = IR.name("L");
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(doNode, labelName);

    ns.shouldTraverse(null, label, null);

    assertSame(doNode, label.getLastChild());
  }

  // normalizeLabels: last child เป็น LABEL ซ้อน (nested label) -> ไม่มีการแก้ไข
  @Test
  public void testNormalizeLabels_lastChildLabel_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node innerBlock = new Node(Token.BLOCK);
    Node innerLabelName = IR.name("I");
    Node innerLabel = new Node(Token.LABEL, innerLabelName);
    innerLabel.addChildAfter(innerBlock, innerLabelName);

    Node outerLabelName = IR.name("O");
    Node outerLabel = new Node(Token.LABEL, outerLabelName);
    outerLabel.addChildAfter(innerLabel, outerLabelName);

    ns.shouldTraverse(null, outerLabel, null);

    assertSame(innerLabel, outerLabel.getLastChild());
    assertSame(innerBlock, innerLabel.getLastChild());
  }

  // normalizeLabels: last child เป็น EXPR_RESULT (default case) + assertOnChange=true -> ต้อง throw
  @Test
  public void testNormalizeLabels_lastChildExprResult_assertOnChangeTrue_throws() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node exprStmt = new Node(Token.EXPR_RESULT, IR.name("x"));
    Node labelName = IR.name("L");
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(exprStmt, labelName);

    try {
      ns.shouldTraverse(null, label, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("LABEL normalization"));
    }
  }

  // normalizeLabels: default case + assertOnChange=false -> ต้องถูกห่อด้วย BLOCK ใหม่
  @Test
  public void testNormalizeLabels_lastChildExprResult_assertOnChangeFalse_wrapsInBlock() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);
    Node exprStmt = new Node(Token.EXPR_RESULT, IR.name("x"));
    Node labelName = IR.name("L");
    Node label = new Node(Token.LABEL, labelName);
    label.addChildAfter(exprStmt, labelName);

    ns.shouldTraverse(null, label, null);

    Node newLast = label.getLastChild();
    assertEquals(Token.BLOCK, newLast.getType());
    assertSame(exprStmt, newLast.getFirstChild());
  }

  // extractForInitializer: FOR มี VAR เป็น initializer + assertOnChange=true -> ต้อง throw
  @Test
  public void testExtractForInitializer_forWithVarInit_assertOnChangeTrue_throws() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node initVar = new Node(Token.VAR, IR.name("i"));
    Node forNode = new Node(Token.FOR, initVar);
    Node cond = new Node(Token.EMPTY);
    forNode.addChildAfter(cond, initVar);
    Node incr = new Node(Token.EMPTY);
    forNode.addChildAfter(incr, cond);
    Node body = new Node(Token.BLOCK);
    forNode.addChildAfter(body, incr);
    Node block = new Node(Token.BLOCK, forNode);

    try {
      ns.shouldTraverse(null, block, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("FOR initializer"));
    }
  }

  // extractForInitializer: FOR มี init เป็น EMPTY อยู่แล้ว -> ไม่มีการแก้ไข
  @Test
  public void testExtractForInitializer_forWithEmptyInit_assertOnChangeTrue_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node e1 = new Node(Token.EMPTY);
    Node forNode = new Node(Token.FOR, e1);
    Node e2 = new Node(Token.EMPTY);
    forNode.addChildAfter(e2, e1);
    Node e3 = new Node(Token.EMPTY);
    forNode.addChildAfter(e3, e2);
    Node body = new Node(Token.BLOCK);
    forNode.addChildAfter(body, e3);
    Node block = new Node(Token.BLOCK, forNode);

    boolean result = ns.shouldTraverse(null, block, null);

    assertTrue(result);
    assertSame(e1, forNode.getFirstChild());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // extractForInitializer: FOR มี expression (ไม่ใช่ VAR) เป็น initializer -> ต้อง throw เมื่อ assertOnChange=true
  @Test
  public void testExtractForInitializer_forWithExpressionInit_assertOnChangeTrue_throws() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node assign = new Node(Token.ASSIGN, IR.name("i"), IR.number(0));
    Node forNode = new Node(Token.FOR, assign);
    Node cond = new Node(Token.EMPTY);
    forNode.addChildAfter(cond, assign);
    Node incr = new Node(Token.EMPTY);
    forNode.addChildAfter(incr, cond);
    Node body = new Node(Token.BLOCK);
    forNode.addChildAfter(body, incr);
    Node block = new Node(Token.BLOCK, forNode);

    try {
      ns.shouldTraverse(null, block, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("FOR initializer"));
    }
  }

  // extractForInitializer: assertOnChange=false -> VAR initializer ต้องถูกย้ายออกมาก่อน FOR ในบล็อก
  @Test
  public void testExtractForInitializer_assertOnChangeFalse_movesVarInitBeforeFor() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);
    Node initVar = new Node(Token.VAR, IR.name("i"));
    Node forNode = new Node(Token.FOR, initVar);
    Node cond = new Node(Token.EMPTY);
    forNode.addChildAfter(cond, initVar);
    Node incr = new Node(Token.EMPTY);
    forNode.addChildAfter(incr, cond);
    Node body = new Node(Token.BLOCK);
    forNode.addChildAfter(body, incr);
    Node block = new Node(Token.BLOCK, forNode);

    ns.shouldTraverse(null, block, null);

    Node first = block.getFirstChild();
    Node second = first.getNext();
    assertSame(initVar, first);
    assertSame(forNode, second);
    assertNull(second.getNext());
    assertEquals(Token.EMPTY, forNode.getFirstChild().getType());
  }

  // splitVarDeclarations: VAR มี 2 ชื่อ + assertOnChange=true -> ต้อง throw
  @Test
  public void testSplitVarDeclarations_twoNames_assertOnChangeTrue_throwsIllegalStateException() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node a = IR.name("a");
    Node b = IR.name("b");
    Node varNode = new Node(Token.VAR, a);
    varNode.addChildAfter(b, a);
    Node block = new Node(Token.BLOCK, varNode);

    try {
      ns.shouldTraverse(null, block, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("VAR with multiple children"));
    }
  }

  // splitVarDeclarations: VAR มีชื่อเดียว -> ไม่มีการแก้ไข
  @Test
  public void testSplitVarDeclarations_singleName_assertOnChangeTrue_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node a = IR.name("a");
    Node varNode = new Node(Token.VAR, a);
    Node block = new Node(Token.BLOCK, varNode);

    boolean result = ns.shouldTraverse(null, block, null);

    assertTrue(result);
    assertSame(varNode, block.getFirstChild());
    assertNull(block.getFirstChild().getNext());
    assertTrue(varNode.hasOneChild());
  }

  // splitVarDeclarations: VAR ไม่มีลูกเลย + assertOnChange=true -> ต้อง throw "Empty VAR node."
  @Test
  public void testSplitVarDeclarations_emptyVar_assertOnChangeTrue_throwsIllegalStateException() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);
    Node varNode = new Node(Token.VAR);
    Node block = new Node(Token.BLOCK, varNode);

    try {
      ns.shouldTraverse(null, block, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("Empty VAR"));
    }
  }

  // ตามบั๊ก: statement VAR ที่ถูกแยกออกมาต้องคง lineno/charno ของ statement เดิม (c) ไม่ใช่ของ parent block (n)
  @Test
  public void testSplitVarDeclarations_assertOnChangeFalse_preservesOriginalSourcePosition() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);
    Node nameA = IR.name("a");
    Node nameB = IR.name("b");
    Node varNode = new Node(Token.VAR, nameA, 5, 10);
    varNode.addChildAfter(nameB, nameA);
    Node block = new Node(Token.BLOCK, varNode, 999, 888);

    ns.shouldTraverse(null, block, null);

    Node first = block.getFirstChild();
    assertEquals(5, first.getLineno());
    assertEquals(10, first.getCharno());
  }

  // splitVarDeclarations: VAR มี 3 ชื่อ -> ต้องถูกแยกเป็น VAR 3 statement แยกกัน ตามลำดับเดิม
  @Test
  public void testSplitVarDeclarations_threeNames_assertOnChangeFalse_producesThreeSeparateVars() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);
    Node a = IR.name("a");
    Node b = IR.name("b");
    Node c = IR.name("c");
    Node varNode = new Node(Token.VAR, a);
    varNode.addChildAfter(b, a);
    varNode.addChildAfter(c, b);
    Node block = new Node(Token.BLOCK, varNode);

    ns.shouldTraverse(null, block, null);

    Node v1 = block.getFirstChild();
    Node v2 = v1.getNext();
    Node v3 = v2.getNext();
    assertNull(v3.getNext());
    assertTrue(v1.hasOneChild());
    assertTrue(v2.hasOneChild());
    assertTrue(v3.hasOneChild());
    assertSame(a, v1.getFirstChild());
    assertSame(b, v2.getFirstChild());
    assertSame(c, v3.getFirstChild());
  }

  // moveNamedFunctions: ฟังก์ชันอยู่ด้านบนอย่างถูกต้องแล้ว -> ไม่มีการแก้ไข
  @Test
  public void testMoveNamedFunctions_alreadyOrdered_assertOnChangeTrue_noChange() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);

    Node innerName1 = IR.name("f1");
    Node innerFunc1 = new Node(Token.FUNCTION, innerName1);
    Node lp1 = new Node(Token.LP);
    innerFunc1.addChildAfter(lp1, innerName1);
    Node innerBody1 = new Node(Token.BLOCK);
    innerFunc1.addChildAfter(innerBody1, lp1);

    Node exprStmt = new Node(Token.EXPR_RESULT, IR.name("x"));

    Node funcBody = new Node(Token.BLOCK, innerFunc1);
    funcBody.addChildAfter(exprStmt, innerFunc1);

    Node outerName = IR.name("outer");
    Node outerFunc = new Node(Token.FUNCTION, outerName);
    Node outerLp = new Node(Token.LP);
    outerFunc.addChildAfter(outerLp, outerName);
    outerFunc.addChildAfter(funcBody, outerLp);

    boolean result = ns.shouldTraverse(null, outerFunc, null);

    assertTrue(result);
    assertSame(innerFunc1, funcBody.getFirstChild());
    assertSame(exprStmt, innerFunc1.getNext());
  }

  // moveNamedFunctions: ฟังก์ชันที่ถูกวางผิดตำแหน่ง (หลัง statement อื่น) ต้องถูกย้ายไปด้านบนของ body
  @Test
  public void testMoveNamedFunctions_misplacedDeclaration_assertOnChangeFalse_movesToFront() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, false);

    Node innerName1 = IR.name("f1");
    Node innerFunc1 = new Node(Token.FUNCTION, innerName1);
    Node lp1 = new Node(Token.LP);
    innerFunc1.addChildAfter(lp1, innerName1);
    Node innerBody1 = new Node(Token.BLOCK);
    innerFunc1.addChildAfter(innerBody1, lp1);

    Node innerName2 = IR.name("f2");
    Node innerFunc2 = new Node(Token.FUNCTION, innerName2);
    Node lp2 = new Node(Token.LP);
    innerFunc2.addChildAfter(lp2, innerName2);
    Node innerBody2 = new Node(Token.BLOCK);
    innerFunc2.addChildAfter(innerBody2, lp2);

    Node exprStmt = new Node(Token.EXPR_RESULT, IR.name("x"));

    Node funcBody = new Node(Token.BLOCK, innerFunc1);
    funcBody.addChildAfter(exprStmt, innerFunc1);
    funcBody.addChildAfter(innerFunc2, exprStmt);

    Node outerName = IR.name("outer");
    Node outerFunc = new Node(Token.FUNCTION, outerName);
    Node outerLp = new Node(Token.LP);
    outerFunc.addChildAfter(outerLp, outerName);
    outerFunc.addChildAfter(funcBody, outerLp);

    ns.shouldTraverse(null, outerFunc, null);

    assertSame(innerFunc1, funcBody.getFirstChild());
    assertSame(innerFunc2, innerFunc1.getNext());
    assertSame(exprStmt, innerFunc2.getNext());
    assertNull(exprStmt.getNext());
  }

  // moveNamedFunctions: เดิมเหมือนกับเทสต์ก่อนหน้า แต่ assertOnChange=true -> ต้อง throw
  @Test
  public void testMoveNamedFunctions_misplacedDeclaration_assertOnChangeTrue_throws() throws Throwable {
    Normalize.NormalizeStatements ns = new Normalize.NormalizeStatements(compiler, true);

    Node innerName1 = IR.name("f1");
    Node innerFunc1 = new Node(Token.FUNCTION, innerName1);
    Node lp1 = new Node(Token.LP);
    innerFunc1.addChildAfter(lp1, innerName1);
    Node innerBody1 = new Node(Token.BLOCK);
    innerFunc1.addChildAfter(innerBody1, lp1);

    Node innerName2 = IR.name("f2");
    Node innerFunc2 = new Node(Token.FUNCTION, innerName2);
    Node lp2 = new Node(Token.LP);
    innerFunc2.addChildAfter(lp2, innerName2);
    Node innerBody2 = new Node(Token.BLOCK);
    innerFunc2.addChildAfter(innerBody2, lp2);

    Node exprStmt = new Node(Token.EXPR_RESULT, IR.name("x"));

    Node funcBody = new Node(Token.BLOCK, innerFunc1);
    funcBody.addChildAfter(exprStmt, innerFunc1);
    funcBody.addChildAfter(innerFunc2, exprStmt);

    Node outerName = IR.name("outer");
    Node outerFunc = new Node(Token.FUNCTION, outerName);
    Node outerLp = new Node(Token.LP);
    outerFunc.addChildAfter(outerLp, outerName);
    outerFunc.addChildAfter(funcBody, outerLp);

    try {
      ns.shouldTraverse(null, outerFunc, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("Move function declaration"));
    }
  }

  // PropogateConstantAnnotations.visit: ชื่อว่าง "" -> return ทันที ไม่ใส่ annotation ใดๆ
  @Test
  public void testPropogateConstantAnnotationsVisit_emptyNameNode_noOp() throws Throwable {
    Normalize.PropogateConstantAnnotations pca =
        new Normalize.PropogateConstantAnnotations(compiler, false);
    Node emptyName = Node.newString(Token.NAME, "");

    pca.visit(null, emptyName, null);

    assertFalse(emptyName.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // PropogateConstantAnnotations.visit: node ที่ไม่ใช่ NAME -> ไม่ทำอะไร
  @Test
  public void testPropogateConstantAnnotationsVisit_nonNameNode_noOp() throws Throwable {
    Normalize.PropogateConstantAnnotations pca =
        new Normalize.PropogateConstantAnnotations(compiler, false);
    Node numberNode = IR.number(1);

    pca.visit(null, numberNode, null);

    assertFalse(numberNode.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // VerifyConstants.visit: ชื่อเดียวกัน annotate ตรงกันทุกครั้ง -> ไม่ throw
  @Test
  public void testVerifyConstantsVisit_consistentAnnotation_noException() throws Throwable {
    Normalize.VerifyConstants vc = new Normalize.VerifyConstants(compiler, false);
    Node n1 = Node.newString(Token.NAME, "x");
    Node n2 = Node.newString(Token.NAME, "x");

    vc.visit(null, n1, null);
    vc.visit(null, n2, null);

    assertFalse(n1.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertFalse(n2.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // VerifyConstants.visit: ชื่อเดียวกันแต่ annotate ไม่ตรงกัน -> ต้อง throw IllegalStateException
  @Test
  public void testVerifyConstantsVisit_inconsistentAnnotation_throwsIllegalStateException() throws Throwable {
    Normalize.VerifyConstants vc = new Normalize.VerifyConstants(compiler, false);
    Node n1 = Node.newString(Token.NAME, "x");
    Node n2 = Node.newString(Token.NAME, "x");
    n2.putBooleanProp(Node.IS_CONSTANT_NAME, true);

    vc.visit(null, n1, null);
    try {
      vc.visit(null, n2, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("not consistently annotated"));
    }
  }

  // VerifyConstants.visit: ชื่อว่าง "" ซ้ำสองครั้ง -> return ก่อนถึง map check เสมอ ไม่ throw
  @Test
  public void testVerifyConstantsVisit_emptyNameTwice_noException() throws Throwable {
    Normalize.VerifyConstants vc = new Normalize.VerifyConstants(compiler, false);
    Node n1 = Node.newString(Token.NAME, "");
    Node n2 = Node.newString(Token.NAME, "");
    n2.putBooleanProp(Node.IS_CONSTANT_NAME, true);

    vc.visit(null, n1, null);
    vc.visit(null, n2, null);

    assertFalse(n1.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertTrue(n2.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // ค่าคงที่ MAKE_LOCAL_NAMES_UNIQUE ต้องเป็น true ตามสัญญาที่ process() อาศัยอยู่
  @Test
  public void testMakeLocalNamesUniqueConstant_isTrue() throws Throwable {
    assertTrue(Normalize.MAKE_LOCAL_NAMES_UNIQUE);
  }
}
