package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameAnalyzerTest {

  @Test
  public void testConstructorAndProcessBasic() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    Node externs = IR.script();
    Node root = IR.script();
    analyzer.process(externs, root);
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
    assertTrue(report.contains("OVERALL STATS"));
  }

  @Test
  public void testProcessWithVarDeclaration() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    Node externs = IR.script();
    Node root = IR.script();
    Node varNode = IR.var(IR.name("x"), IR.number(1));
    root.addChildToBack(varNode);
    
    analyzer.process(externs, root);
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
    assertTrue(report.contains("x"));
  }

  @Test
  public void testProcessWithFunctionDeclaration() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, false);
    Node externs = IR.script();
    Node root = IR.script();
    Node funcNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block());
    root.addChildToBack(funcNode);
    
    analyzer.process(externs, root);
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
    assertTrue(report.contains("myFunc"));
  }

  @Test
  public void testProcessWithAssignment() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    Node externs = IR.script();
    Node root = IR.script();
    Node assignNode = IR.assign(IR.name("a"), IR.number(10));
    Node exprResult = IR.exprResult(assignNode);
    root.addChildToBack(exprResult);
    
    analyzer.process(externs, root);
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
  }

  @Test
  public void testProcessWithPrototypeAssignment() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    Node externs = IR.script();
    Node root = IR.script();
    
    Node getProp = IR.getProp(IR.name("ClassA"), "prototype");
    Node setProp = IR.getProp(getProp, "methodB");
    Node assignNode = IR.assign(setProp, IR.function(IR.name(""), IR.paramList(), IR.block()));
    root.addChildToBack(IR.exprResult(assignNode));
    
    analyzer.process(externs, root);
    String report = analyzer.getHtmlReport();
    assertNotNull(report);
  }

  @Test
  public void testProcessWithCall() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    Node externs = IR.script();
    Node root = IR.script();
    
    Node callNode = IR.call(IR.name("goog.inherits"), IR.name("Sub"), IR.name("Super"));
    root.addChildToBack(IR.exprResult(callNode));
    
    analyzer.process(externs, root);
    assertNotNull(analyzer.getHtmlReport());
  }
}