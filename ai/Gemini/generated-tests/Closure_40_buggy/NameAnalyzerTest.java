package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import junit.framework.TestCase;

public class NameAnalyzerTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testProcessSimpleAssignment() throws Throwable {
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    
    Node varNode = IR.var(IR.name("a"), IR.number(1));
    root.addChildToBack(varNode);

    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(externs, root);
    
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
    assertTrue(report.contains("Total Names"));
  }

  public void testProcessFunctionDeclaration() throws Throwable {
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);

    Node func = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    root.addChildToBack(func);

    NameAnalyzer analyzer = new NameAnalyzer(compiler, false);
    analyzer.process(externs, root);

    String report = analyzer.getHtmlReport();
    assertNotNull(report);
    assertTrue(report.contains("foo"));
  }

  public void testHtmlReportGeneration() throws Throwable {
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);

    Node assign = IR.assign(IR.name("x"), IR.number(5));
    Node expr = IR.exprResult(assign);
    root.addChildToBack(expr);

    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(externs, root);

    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("<html>"));
    assertTrue(report.contains("OVERALL STATS"));
    assertTrue(report.contains("ALL NAMES"));
  }

  public void testPrototypeAssignment() throws Throwable {
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);

    Node ctor = IR.var(IR.name("A"), IR.function(IR.name("A"), IR.paramList(), IR.block()));
    Node protoAssign = IR.assign(
        IR.getProp(IR.name("A"), "prototype"),
        IR.objectLit()
    );
    Node expr = IR.exprResult(protoAssign);

    root.addChildToBack(ctor);
    root.addChildToBack(expr);

    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(externs, root);

    String report = analyzer.getHtmlReport();
    assertNotNull(report);
  }

  public void testUnreferencedRemoval() throws Throwable {
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);

    Node varNode = IR.var(IR.name("unusedVar"), IR.number(10));
    root.addChildToBack(varNode);

    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(externs, root);

    assertEquals(-1, root.getIndexOfChild(varNode));
  }
}