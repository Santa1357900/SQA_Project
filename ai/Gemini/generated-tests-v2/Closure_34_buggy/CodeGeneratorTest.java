package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Token;
import com.google.common.base.Charsets;
import java.nio.charset.Charset;

public class CodeGeneratorTest {

  private static class DummyConsumer implements CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

    public void add(String str) {
      sb.append(str);
    }

    public boolean continueProcessing() {
      return true;
    }

    public void startSourceMapping(Node n) {}

    public void endSourceMapping(Node n) {}

    public void endStatement() {
      sb.append(";");
    }

    public void endStatement(boolean optional) {
      if (!optional) {
        sb.append(";");
      }
    }

    public void addOp(String op, boolean linewrapOnOps) {
      sb.append(op);
    }

    public void addNumber(double x) {
      sb.append(String.valueOf(x));
    }

    public void addIdentifier(String identifier) {
      sb.append(identifier);
    }

    public void beginBlock() {
      sb.append("{");
    }

    public void endBlock(boolean statement_context) {
      sb.append("}");
    }

    public void maybeLineBreak() {}

    public void notePreferredLineBreak() {}

    public void listSeparator() {
      sb.append(",");
    }

    public void beginCaseBody() {
      sb.append("{");
    }

    public void endCaseBody() {
      sb.append("}");
    }

    public boolean breakAfterBlockFor(Node n, boolean statementContext) {
      return false;
    }

    public boolean shouldPreserveExtraBlocks() {
      return false;
    }

    public String getOutput() {
      return sb.toString();
    }
  }

  @Test
  public void testConstructorsAndTagAsStrict() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    cg.tagAsStrict();
    assertEquals("'use strict';", consumer.getOutput());

    DummyConsumer consumer2 = new DummyConsumer();
    CodeGenerator cg2 = new CodeGenerator(consumer2, Charsets.UTF_8);
    cg2.tagAsStrict();
    assertTrue(consumer2.getOutput().contains("use strict"));
  }

  @Test
  public void testIsSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("123"));
    assertFalse(CodeGenerator.isSimpleNumber("0123"));
    assertFalse(CodeGenerator.isSimpleNumber("-123"));
    assertFalse(CodeGenerator.isSimpleNumber("123a"));
    assertFalse(CodeGenerator.isSimpleNumber(""));
  }

  @Test
  public void testGetSimpleNumber() throws Throwable {
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("0123")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("notanumber")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("999999999999999999999999")));
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    String escaped = CodeGenerator.regexpEscape("abc/def");
    assertTrue(escaped.contains("/"));
    
    String escapedWithCharset = CodeGenerator.regexpEscape("abc", Charsets.US_ASCII);
    assertEquals("/abc/", escapedWithCharset);
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    String res = CodeGenerator.escapeToDoubleQuotedJsString("hello\"world");
    assertTrue(res.contains("\\\""));
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    assertEquals("abc", CodeGenerator.identifierEscape("abc"));
    String escaped = CodeGenerator.identifierEscape("\u0001");
    assertTrue(escaped.contains("\\u0001"));
  }

  @Test
  public void testNumberNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node numberNode = IR.number(42.0);
    cg.add(numberNode);
    assertEquals("42.0", consumer.getOutput());
  }

  @Test
  public void testStringNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node strNode = IR.string("test");
    cg.add(strNode);
    assertTrue(consumer.getOutput().contains("test"));
  }

  @Test
  public void testNullTrueFalseNodes() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    
    cg.add(IR.nullNode());
    cg.add(IR.trueNode());
    cg.add(IR.falseNode());
    
    assertEquals("nulltruetrue", consumer.getOutput() == null ? "" : consumer.getOutput());
  }

  @Test
  public void testArrayLitNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node arrayNode = IR.arrayLit(IR.number(1.0), IR.number(2.0));
    cg.add(arrayNode);
    assertEquals("[1.0,2.0]", consumer.getOutput());
  }

  @Test
  public void testUnaryNegNodeOnNumber() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node negNode = new Node(Token.NEG, IR.number(5.0));
    cg.add(negNode);
    assertEquals("-5.0", consumer.getOutput());
  }

  @Test
  public void testUnaryNegNodeOnNonNumber() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node negNode = new Node(Token.NEG, IR.name("x"));
    cg.add(negNode);
    assertEquals("-x", consumer.getOutput());
  }

  @Test
  public void testBinaryOpNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node addNode = IR.add(IR.number(1.0), IR.number(2.0));
    cg.add(addNode);
    assertEquals("1.0+2.0", consumer.getOutput());
  }

  @Test
  public void testHookNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node hookNode = new Node(Token.HOOK, IR.trueNode(), IR.number(1.0), IR.number(2.0));
    cg.add(hookNode);
    assertEquals("true?1.0:2.0", consumer.getOutput());
  }

  @Test
  public void testThrowNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node throwNode = new Node(Token.THROW, IR.string("error"));
    cg.add(throwNode);
    assertTrue(consumer.getOutput().contains("throw"));
  }

  @Test
  public void testReturnNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node retNode = new Node(Token.RETURN);
    cg.add(retNode);
    assertTrue(consumer.getOutput().contains("return"));
  }

  @Test
  public void testDebuggerNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node dbgNode = new Node(Token.DEBUGGER);
    cg.add(dbgNode);
    assertTrue(consumer.getOutput().contains("debugger"));
  }

  @Test
  public void testEmptyNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node emptyNode = new Node(Token.EMPTY);
    cg.add(emptyNode);
    assertEquals("", consumer.getOutput());
  }

  @Test
  public void testThisNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    Node thisNode = new Node(Token.THIS);
    cg.add(thisNode);
    assertEquals("this", consumer.getOutput());
  }

}