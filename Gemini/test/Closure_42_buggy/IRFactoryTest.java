package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.head.ast.Name;
import com.google.javascript.rhino.head.ast.NumberLiteral;
import com.google.javascript.rhino.head.ast.StringLiteral;
import com.google.javascript.rhino.head.ast.RegExpLiteral;
import com.google.javascript.rhino.head.ast.EmptyExpression;
import com.google.javascript.rhino.head.ast.Block;
import com.google.javascript.rhino.head.ast.FunctionNode;
import com.google.javascript.rhino.head.ast.Assignment;
import com.google.javascript.rhino.head.ast.UnaryExpression;
import com.google.javascript.rhino.head.ErrorReporter;

public class IRFactoryTest {

  private static class DummyErrorReporter implements ErrorReporter {
    public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {}
    public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {}
    public com.google.javascript.rhino.head.EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
      return new com.google.javascript.rhino.head.EvaluatorException(message, sourceName, line, lineSource, lineOffset);
    }
  }

  @Test
  public void testTransformTreeBasic() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.SCRIPT, result.getType());
  }

  @Test
  public void testLanguageModeModes() throws Throwable {
    AstRoot astRoot = new AstRoot();
    ErrorReporter reporter = new DummyErrorReporter();

    Config configES3 = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES3, reporter));

    Config configES5 = new Config(LanguageMode.ECMASCRIPT5, null, false, false, null);
    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES5, reporter));

    Config configES5Strict = new Config(LanguageMode.ECMASCRIPT5_STRICT, null, false, false, null);
    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES5Strict, reporter));
  }

  @Test
  public void testTransformEmptyExpression() throws Throwable {
    AstRoot astRoot = new AstRoot();
    EmptyExpression empty = new EmptyExpression(0, 1);
    astRoot.addChildren(empty);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.SCRIPT, result.getType());
    assertTrue(result.hasChildren());
    assertEquals(Token.EMPTY, result.getFirstChild().getType());
  }

  @Test
  public void testTransformNumberLiteral() throws Throwable {
    AstRoot astRoot = new AstRoot();
    NumberLiteral num = new NumberLiteral(42.0);
    astRoot.addChildren(num);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    Node child = result.getFirstChild();
    assertEquals(Token.NUMBER, child.getType());
    assertEquals(42.0, child.getDouble(), 0.001);
  }

  @Test
  public void testTransformStringLiteral() throws Throwable {
    AstRoot astRoot = new AstRoot();
    StringLiteral str = new StringLiteral();
    str.setValue("hello");
    astRoot.addChildren(str);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    Node child = result.getFirstChild();
    assertEquals(Token.STRING, child.getType());
    assertEquals("hello", child.getString());
  }

  @Test
  public void testTransformRegExpLiteral() throws Throwable {
    AstRoot astRoot = new AstRoot();
    RegExpLiteral regExp = new RegExpLiteral();
    regExp.setValue("abc");
    regExp.setFlags("gi");
    astRoot.addChildren(regExp);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    Node child = result.getFirstChild();
    assertEquals(Token.REGEXP, child.getType());
    assertEquals("abc", child.getFirstChild().getString());
    assertEquals("gi", child.getLastChild().getString());
  }

  @Test
  public void testTransformNameReservedKeywordES5() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Name nameNode = new Name();
    nameNode.setIdentifier("class");
    astRoot.addChildren(nameNode);

    Config config = new Config(LanguageMode.ECMASCRIPT5, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.NAME, result.getFirstChild().getType());
  }

  @Test
  public void testTransformBlock() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Block block = new Block();
    astRoot.addChildren(block);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.BLOCK, result.getFirstChild().getType());
  }

  @Test
  public void testTransformUnnamedFunctionStatement() throws Throwable {
    AstRoot astRoot = new AstRoot();
    FunctionNode func = new FunctionNode();
    func.setFunctionType(FunctionNode.FUNCTION_STATEMENT);
    astRoot.addChildren(func);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.EXPR_RESULT, result.getFirstChild().getType());
  }

  @Test
  public void testTransformUnaryExpressionNegNumber() throws Throwable {
    AstRoot astRoot = new AstRoot();
    UnaryExpression unary = new UnaryExpression();
    unary.setType(com.google.javascript.rhino.head.Token.NEG);
    NumberLiteral num = new NumberLiteral(10.0);
    unary.setOperand(num);
    astRoot.addChildren(unary);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    Node child = result.getFirstChild();
    assertEquals(Token.NUMBER, child.getType());
    assertEquals(-10.0, child.getDouble(), 0.001);
  }

  @Test
  public void testTransformAssignmentInvalidTarget() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Assignment assign = new Assignment();
    NumberLiteral left = new NumberLiteral(1.0);
    NumberLiteral right = new NumberLiteral(2.0);
    assign.setType(com.google.javascript.rhino.head.Token.ASSIGN);
    assign.setLeft(left);
    assign.setRight(right);
    astRoot.addChildren(assign);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "", config, reporter);
    assertNotNull(result);
    assertEquals(Token.ASSIGN, result.getFirstChild().getType());
  }

}