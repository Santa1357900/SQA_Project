package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import org.junit.Test;
import static org.junit.Assert.*;

public class CodeGeneratorTest {

  private static class DummyCodeConsumer extends CodeConsumer {
    private final StringBuilder sb = new StringBuilder();
    private boolean continueProcessing = true;

    @Override
    void add(String str) {
      sb.append(str);
    }

    @Override
    boolean continueProcessing() {
      return continueProcessing;
    }

    String getResult() {
      return sb.toString();
    }
  }

  @Test
  public void testIsSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("0"));
    assertTrue(CodeGenerator.isSimpleNumber("123"));
    assertFalse(CodeGenerator.isSimpleNumber(""));
    assertFalse(CodeGenerator.isSimpleNumber("0123"));
    assertFalse(CodeGenerator.isSimpleNumber("123a"));
    assertFalse(CodeGenerator.isSimpleNumber("-5"));
  }

  @Test
  public void testGetSimpleNumber() throws Throwable {
    assertEquals(0.0, CodeGenerator.getSimpleNumber("0"), 0.001);
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("abc")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("999999999999999999999")));
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    assertEquals("abc", CodeGenerator.identifierEscape("abc"));
    String escaped = CodeGenerator.identifierEscape("\u0100");
    assertTrue(escaped.contains("\\u"));
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    String res = cg.escapeToDoubleQuotedJsString("hello \"world\"");
    assertTrue(res.contains("\\\""));
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    String res = cg.regexpEscape("abc/def");
    assertTrue(res.startsWith("/"));
    assertTrue(res.endsWith("/"));
  }

  @Test
  public void testTagAsStrict() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    cg.tagAsStrict();
    assertEquals("'use strict';", consumer.getResult());
  }

  @Test
  public void testAddNumberNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node numNode = IR.number(42.0);
    cg.add(numNode);
    assertEquals("42", consumer.getResult());
  }

  @Test
  public void testAddStringNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node strNode = IR.string("test");
    cg.add(strNode);
    assertTrue(consumer.getResult().contains("test"));
  }

  @Test
  public void testAddTrueFalseNull() throws Throwable {
    DummyCodeConsumer consumer1 = new DummyCodeConsumer();
    CodeGenerator cg1 = CodeGenerator.forCostEstimation(consumer1);
    cg1.add(IR.trueNode());
    assertEquals("true", consumer1.getResult());

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator cg2 = CodeGenerator.forCostEstimation(consumer2);
    cg2.add(IR.falseNode());
    assertEquals("false", consumer2.getResult());

    DummyCodeConsumer consumer3 = new DummyCodeConsumer();
    CodeGenerator cg3 = CodeGenerator.forCostEstimation(consumer3);
    cg3.add(IR.nullNode());
    assertEquals("null", consumer3.getResult());
  }

  @Test
  public void testAddThis() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    cg.add(new Node(com.google.javascript.rhino.Token.THIS));
    assertEquals("this", consumer.getResult());
  }

  @Test
  public void testAddDebugger() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    cg.add(new Node(com.google.javascript.rhino.Token.DEBUGGER));
    assertTrue(consumer.getResult().contains("debugger"));
  }

  @Test
  public void testAddCast() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node castNode = new Node(com.google.javascript.rhino.Token.CAST, IR.number(1.0));
    cg.add(castNode);
    assertEquals("(1)", consumer.getResult());
  }
}