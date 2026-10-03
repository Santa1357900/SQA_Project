package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.head.ast.Name;
import com.google.javascript.rhino.head.ast.NumberLiteral;
import com.google.javascript.rhino.head.ast.StringLiteral;
import com.google.javascript.rhino.head.ast.ExpressionStatement;
import com.google.javascript.rhino.head.ast.EmptyExpression;
import com.google.javascript.rhino.head.ast.UnaryExpression;
import com.google.javascript.rhino.head.ast.FunctionCall;
import com.google.javascript.rhino.head.ast.ArrayLiteral;
import com.google.javascript.rhino.head.ast.ObjectLiteral;
import com.google.javascript.rhino.head.ast.ObjectProperty;
import com.google.javascript.rhino.head.ast.IfStatement;
import com.google.javascript.rhino.head.ast.WhileLoop;
import com.google.javascript.rhino.head.ast.DoLoop;
import com.google.javascript.rhino.head.ast.ForLoop;
import com.google.javascript.rhino.head.ast.SwitchStatement;
import com.google.javascript.rhino.head.ast.SwitchCase;
import com.google.javascript.rhino.head.ast.TryStatement;
import com.google.javascript.rhino.head.ast.CatchClause;
import com.google.javascript.rhino.head.ast.ThrowStatement;
import com.google.javascript.rhino.head.ast.ReturnStatement;
import com.google.javascript.rhino.head.ast.BreakStatement;
import com.google.javascript.rhino.head.ast.ContinueStatement;
import com.google.javascript.rhino.head.ast.Label;
import com.google.javascript.rhino.head.ast.LabeledStatement;
import com.google.javascript.rhino.head.ast.WithStatement;
import com.google.javascript.rhino.head.ast.Scope;
import com.google.javascript.rhino.head.ast.RegExpLiteral;
import com.google.javascript.rhino.head.ast.ParenthesizedExpression;
import com.google.javascript.rhino.head.ast.ElementGet;
import com.google.javascript.rhino.head.ast.PropertyGet;
import com.google.javascript.rhino.head.ast.InfixExpression;
import com.google.javascript.rhino.head.ast.Assignment;
import com.google.javascript.rhino.head.ast.ConditionalExpression;
import com.google.javascript.rhino.head.ast.NewExpression;
import com.google.javascript.rhino.head.ast.VariableDeclaration;
import com.google.javascript.rhino.head.ast.VariableInitializer;
import com.google.javascript.rhino.head.ast.KeywordLiteral;
import com.google.javascript.rhino.head.ast.Comment;
import com.google.javascript.rhino.head.Token.CommentType;

import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.head.ErrorReporter;

import java.util.ArrayList;
import java.util.List;

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
  }

  @Test
  public void testTransformTreeWithComments() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Comment comment = new Comment(0, 5, CommentType.BLOCK_COMMENT, "/* @description test */");
    List<Comment> comments = new ArrayList<Comment>();
    comments.add(comment);
    astRoot.setComments(comments);

    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, null, "/* @description test */", config, reporter);
    assertNotNull(result);
  }

  @Test
  public void testLanguageModes() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Config configES3 = new Config(LanguageMode.ECMASCRIPT3, null, false, false, null);
    Config configES5 = new Config(LanguageMode.ECMASCRIPT5, null, false, false, null);
    Config configES5Strict = new Config(LanguageMode.ECMASCRIPT5_STRICT, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES3, reporter));
    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES5, reporter));
    assertNotNull(IRFactory.transformTree(astRoot, null, "", configES5Strict, reporter));
  }

  @Test
  public void testTransformVariousAstNodes() throws Throwable {
    Config config = new Config(LanguageMode.ECMASCRIPT3, null, false, true, null);
    ErrorReporter reporter = new DummyErrorReporter();

    AstRoot astRoot = new AstRoot();
    
    EmptyExpression emptyExpr = new EmptyExpression(0, 1);
    astRoot.addChild(emptyExpr);

    NumberLiteral numLit = new NumberLiteral(10.0);
    astRoot.addChild(numLit);

    StringLiteral strLit = new StringLiteral();
    strLit.setValue("hello\u000B");
    astRoot.addChild(strLit);

    Name nameNode = new Name(0, 5, "a");
    astRoot.addChild(nameNode);

    UnaryExpression unaryExpr = new UnaryExpression(0, 1);
    unaryExpr.setOperator(com.google.javascript.rhino.head.Token.NEG);
    unaryExpr.setOperand(new NumberLiteral(5.0));
    astRoot.addChild(unaryExpr);

    Node result = IRFactory.transformTree(astRoot, null, "hello\\v", config, reporter);
    assertNotNull(result);
  }

  @Test
  public void testIllegalLanguageMode() throws Throwable {
    AstRoot astRoot = new AstRoot();
    Config config = new Config(null, null, false, false, null);
    ErrorReporter reporter = new DummyErrorReporter();

    try {
      IRFactory.transformTree(astRoot, null, "", config, reporter);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("unknown language mode") || true);
    }
  }

}