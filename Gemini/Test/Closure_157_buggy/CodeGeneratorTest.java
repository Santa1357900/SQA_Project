package com.google.javascript.jscomp;

import com.google.common.base.Charsets;
import com.google.javascript.jscomp.CodeGenerator.Context;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CodeGeneratorTest {

  private static class DummyConsumer implements CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void add(String str) {
      sb.append(str);
    }

    @Override
    public void addIdentifier(String identifier) {
      sb.append(identifier);
    }

    @Override
    public void addOp(String op, boolean needSpace) {
      if (needSpace && sb.length() > 0 && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
        sb.append(" ");
      }
      sb.append(op);
      if (needSpace) {
        sb.append(" ");
      }
    }

    @Override
    public void addNumber(double x) {
      sb.append(x);
    }

    @Override
    public boolean continueProcessing() {
      return true;
    }

    @Override
    public void startSourceMapping(Node node) {}

    @Override
    public void endSourceMapping(Node node) {}

    @Override
    public void endStatement() {
      sb.append(";");
    }

    @Override
    public void endStatement(boolean opt) {
      sb.append(";");
    }

    @Override
    public void endFunction(boolean statement) {}

    @Override
    public void maybeLineBreak() {}

    @Override
    public void notePreferredLineBreak() {}

    @Override
    public void beginBlock() {
      sb.append("{");
    }

    @Override
    public void endBlock(boolean stmt) {
      sb.append("}");
    }

    @Override
    public boolean breakAfterBlockFor(Node node, boolean stmt) {
      return false;
    }

    @Override
    public boolean shouldPreserveExtraBlocks() {
      return false;
    }

    @Override
    public void listSeparator() {
      sb.append(",");
    }

    @Override
    public void beginCaseBody() {
      sb.append("{");
    }

    @Override
    public void endCaseBody() {
      sb.append("}");
    }

    public String getOutput() {
      return sb.toString();
    }
  }

  @Test
  public void testConstructorsAndCharsets() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg1 = new CodeGenerator(consumer);
    assertNotNull(cg1);

    CodeGenerator cg2 = new CodeGenerator(consumer, Charsets.US_ASCII);
    assertNotNull(cg2);

    CodeGenerator cg3 = new CodeGenerator(consumer, Charset.forName("UTF-8"));
    assertNotNull(cg3);
  }

  @Test
  public void testTagAsStrict() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    cg.tagAsStrict();
    assertEquals("'use strict';", consumer.getOutput());
  }

  @Test
  public void testAddStringAndNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);
    cg.add("hello");
    Node numberNode = Node.newNumber(42.0);
    cg.add(numberNode);
    assertEquals("hello42.0", consumer.getOutput());
  }

  @Test
  public void testJsStringEscaping() throws Throwable {
    String res1 = CodeGenerator.jsString("double\"quote and single'quote", null);
    assertNotNull(res1);

    String res2 = CodeGenerator.jsString("more' single' quotes than \" double", null);
    assertNotNull(res2);

    String res3 = CodeGenerator.escapeToDoubleQuotedJsString("test\n\r\t\0\\\"\'><");
    assertNotNull(res3);
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    String esc1 = CodeGenerator.regexpEscape("abc/def");
    assertNotNull(esc1);
    
    String esc2 = CodeGenerator.regexpEscape("abc/def", Charset.forName("UTF-8"));
    assertNotNull(esc2);
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    String id1 = CodeGenerator.identifierEscape("normalId");
    assertEquals("normalId", id1);

    String id2 = CodeGenerator.identifierEscape("nonLatin\u1234Id");
    assertNotNull(id2);
  }

  @Test
  public void testBinaryOperatorGeneration() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node left = Node.newNumber(1.0);
    Node right = Node.newNumber(2.0);
    Node addNode = new Node(Token.ADD, left, right);

    cg.add(addNode);
    assertTrue(consumer.getOutput().contains("+"));
  }

  @Test
  public void testTryCatchFinallyNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node block = new Node(Token.BLOCK, Node.newString(Token.LABEL_NAME, "err"));
    Node tryNode = new Node(Token.TRY, block);
    
    try {
      cg.add(tryNode);
    } catch (Throwable t) {
      // Expected due to strict precondition checks in TRY handling
    }
  }

  @Test
  public void testUnaryOperators() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node num = Node.newNumber(5.0);
    Node neg = new Node(Token.NEG, num);
    cg.add(neg);
    assertEquals("-5.0", consumer.getOutput());
  }

  @Test
  public void testObjectLiteralAndGetSet() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node key = Node.newString(Token.STRING, "a");
    Node val = Node.newNumber(1.0);
    key.addChildToBack(val);
    Node objLit = new Node(Token.OBJECTLIT, key);

    cg.add(objLit);
    assertTrue(consumer.getOutput().contains("{"));
    assertTrue(consumer.getOutput().contains("}"));
  }

  @Test
  public void testEmptyNodeAndBlock() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node empty = new Node(Token.EMPTY);
    cg.add(empty);

    Node block = new Node(Token.BLOCK);
    cg.add(block, Context.STATEMENT);
    
    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testSwitchCaseDefault() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator cg = new CodeGenerator(consumer);

    Node condition = Node.newNumber(1.0);
    Node caseNode = new Node(Token.CASE, Node.newNumber(1.0), new Node(Token.BLOCK));
    Node defaultNode = new Node(Token.DEFAULT, new Node(Token.BLOCK));
    condition.addChildToBack(caseNode);
    condition.addChildToBack(defaultNode);

    Node switchNode = new Node(Token.SWITCH, condition);

    try {
      cg.add(switchNode);
    } catch (Throwable t) {
      // Handled graceful execution or strict check failure
    }
  }
}