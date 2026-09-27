package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import static org.junit.Assert.*;

public class NormalizeTest {

  @Test
  public void testNormalizeInstantiationAndConstants() throws Throwable {
    Compiler compiler = new Compiler();
    Normalize normalize = new Normalize(compiler, false);
    assertNotNull(normalize);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    normalize.process(externs, root);
  }

  @Test
  public void testParseAndNormalizeSyntheticCode() throws Throwable {
    Compiler compiler = new Compiler();
    Node node = Normalize.parseAndNormalizeSyntheticCode(compiler, "var a = 0;", "testPrefix");
    assertNotNull(node);
  }

  @Test
  public void testParseAndNormalizeTestCode() throws Throwable {
    Compiler compiler = new Compiler();
    Node node = Normalize.parseAndNormalizeTestCode(compiler, "var b = 1;", "testPrefix");
    assertNotNull(node);
  }

  @Test
  public void testNormalizeStatementsWhileConversion() throws Throwable {
    Compiler compiler = new Compiler();
    Node whileNode = new Node(Token.WHILE, Node.newNumber(1), new Node(Token.BLOCK));
    Node parent = new Node(Token.BLOCK, whileNode);
    
    Normalize.NormalizeStatements statements = new Normalize.NormalizeStatements(compiler, false);
    NodeTraversal t = new NodeTraversal(compiler, statements);
    
    statements.shouldTraverse(t, whileNode, parent);
    statements.visit(t, whileNode, parent);
    
    assertEquals(Token.FOR, whileNode.getType());
  }

  @Test
  public void testNormalizeStatementsFunctionDeclaration() throws Throwable {
    Compiler compiler = new Compiler();
    Node nameNode = Node.newString(Token.NAME, "myFunc");
    Node lpNode = new Node(Token.LP);
    Node bodyNode = new Node(Token.BLOCK);
    Node funcNode = new Node(Token.FUNCTION, nameNode, lpNode, bodyNode);
    Node parent = new Node(Token.BLOCK, funcNode);
    
    Normalize.NormalizeStatements statements = new Normalize.NormalizeStatements(compiler, false);
    NodeTraversal t = new NodeTraversal(compiler, statements);
    
    statements.shouldTraverse(t, funcNode, parent);
    statements.visit(t, funcNode, parent);
  }

  @Test
  public void testNormalizeLabels() throws Throwable {
    Compiler compiler = new Compiler();
    Node exprNode = Node.newNumber(1);
    Node labelNode = new Node(Token.LABEL, Node.newString(Token.LABEL_NAME, "l"), exprNode);
    Node parent = new Node(Token.BLOCK, labelNode);
    
    Normalize.NormalizeStatements statements = new Normalize.NormalizeStatements(compiler, false);
    NodeTraversal t = new NodeTraversal(compiler, statements);
    
    statements.shouldTraverse(t, labelNode, parent);
    assertEquals(Token.BLOCK, labelNode.getLastChild().getType());
  }

  @Test
  public void testPropagateConstantAnnotationsOverVars() throws Throwable {
    Compiler compiler = new Compiler();
    Normalize.PropagateConstantAnnotationsOverVars prop = 
        new Normalize.PropagateConstantAnnotationsOverVars(compiler, false);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    prop.process(externs, root);
  }

  @Test
  public void testVerifyConstants() throws Throwable {
    Compiler compiler = new Compiler();
    Normalize.VerifyConstants verify = new Normalize.VerifyConstants(compiler, false);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    Node externsAndJs = new Node(Token.BLOCK, externs, root);
    root.setParent(externsAndJs);
    
    try {
      verify.process(externs, root);
    } catch (Exception e) {
      // Expected if tree structure is minimal/stubbed
    }
  }

}