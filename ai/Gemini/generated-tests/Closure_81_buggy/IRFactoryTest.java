package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.mozilla.rhino.ast.AstRoot;
import com.google.javascript.jscomp.mozilla.rhino.ErrorReporter;
import com.google.javascript.jscomp.mozilla.rhino.EvaluatorException;
import com.google.javascript.jscomp.mozilla.rhino.ast.Name;
import com.google.javascript.jscomp.mozilla.rhino.ast.StringLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.ExpressionStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.Assignment;
import com.google.javascript.jscomp.mozilla.rhino.ast.NumberLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.UnaryExpression;
import com.google.javascript.jscomp.mozilla.rhino.Token;
import com.google.javascript.rhino.Node;

public class IRFactoryTest {

  private static final class DummyErrorReporter implements ErrorReporter {
    public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {}
    public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
      throw new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
    }
    public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
      return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
    }
  }

  @Test
  public void testTransformTreeBasic() throws Throwable {
    AstRoot astRoot = new AstRoot();
    astRoot.setSourceName("test.js");
    
    Config config = new Config(null, null, true, true, false);
    ErrorReporter errorReporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, "", config, errorReporter);
    assertNotNull(result);
    assertEquals(com.google.javascript.rhino.Token.SCRIPT, result.getType());
  }

  @Test
  public void testTransformTreeWithDirective() throws Throwable {
    AstRoot astRoot = new AstRoot();
    astRoot.setSourceName("test.js");
    
    StringLiteral strLit = new StringLiteral();
    strLit.setValue("use strict");
    ExpressionStatement exprStmt = new ExpressionStatement(strLit);
    astRoot.addChild(exprStmt);

    Config config = new Config(null, null, true, true, false);
    ErrorReporter errorReporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, "'use strict';", config, errorReporter);
    assertNotNull(result);
    assertTrue(result.getDirectives() != null);
    assertTrue(result.getDirectives().contains("use strict"));
  }

  @Test
  public void testUnaryExpressionNegativeNumber() throws Throwable {
    AstRoot astRoot = new AstRoot();
    astRoot.setSourceName("test.js");

    NumberLiteral numLit = new NumberLiteral();
    numLit.setNumber(5.0);

    UnaryExpression unaryExpr = new UnaryExpression();
    unaryExpr.setType(com.google.javascript.jscomp.mozilla.rhino.Token.NEG);
    unaryExpr.setOperand(numLit);

    ExpressionStatement exprStmt = new ExpressionStatement(unaryExpr);
    astRoot.addChild(exprStmt);

    Config config = new Config(null, null, true, true, false);
    ErrorReporter errorReporter = new DummyErrorReporter();

    Node result = IRFactory.transformTree(astRoot, "-5", config, errorReporter);
    assertNotNull(result);
  }

  @Test
  public void testInvalidAssignmentTarget() throws Throwable {
    AstRoot astRoot = new AstRoot();
    astRoot.setSourceName("test.js");

    NumberLiteral numLit = new NumberLiteral();
    numLit.setNumber(5.0);

    Name nameNode = new Name();
    nameNode.setIdentifier("b");

    Assignment assignment = new Assignment();
    assignment.setType(com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN);
    assignment.setLeft(numLit); // Invalid assignment target (number literal)
    assignment.setRight(nameNode);

    ExpressionStatement exprStmt = new ExpressionStatement(assignment);
    astRoot.addChild(exprStmt);

    Config config = new Config(null, null, true, true, false);
    ErrorReporter errorReporter = new DummyErrorReporter();

    boolean caught = false;
    try {
      IRFactory.transformTree(astRoot, "5 = b;", config, errorReporter);
    } catch (EvaluatorException e) {
      if (e.getMessage() != null && e.getMessage().contains("invalid assignment target")) {
        caught = true;
      }
    }
    assertTrue(caught);
  }
}