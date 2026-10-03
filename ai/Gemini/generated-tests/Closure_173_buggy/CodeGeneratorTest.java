package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeGeneratorTest {

  private static class DummyCodeConsumer extends CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

    @Override
    void add(String str) {
      sb.append(str);
    }

    @Override
    void addOd(String opStr, boolean rhs) {
      sb.append(opStr);
    }

    @Override
    void addOp(String opStr, boolean rhs) {
      sb.append(opStr);
    }

    @Override
    void addIdentifier(String identifier) {
      sb.append(identifier);
    }

    @Override
    void addNumber(double x) {
      sb.append(String.valueOf(x));
    }

    @Override
    void addConstant(String constant) {
      sb.append(constant);
    }

    @Override
    boolean continueProcessing() {
      return true;
    }
  }

  @Test
  public void testForCostEstimation() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    assertNotNull(generator);
  }

  @Test
  public void testTagAsStrict() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    generator.tagAsStrict();
    assertEquals("'use strict';", consumer.sb.toString());
  }

  @Test
  public void testIsSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("0"));
    assertTrue(CodeGenerator.isSimpleNumber("123"));
    assertFalse(CodeGenerator.isSimpleNumber(""));
    assertFalse(CodeGenerator.isSimpleNumber("01"));
    assertFalse(CodeGenerator.isSimpleNumber("12a"));
  }

  @Test
  public void testGetSimpleNumber() throws Throwable {
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("abc")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("999999999999999999999")));
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    String escaped = generator.escapeToDoubleQuotedJsString("hello \"world\"");
    assertEquals("\"hello \\\"world\\\"\"", escaped);
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    String escaped = generator.regexpEscape("abc/def");
    assertEquals("/abc\\/def/", escaped);
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    String escapedAscii = CodeGenerator.identifierEscape("abc");
    assertEquals("abc", escapedAscii);

    String escapedNonAscii = CodeGenerator.identifierEscape("\u0123");
    assertEquals("\\u0123", escapedNonAscii);
  }

  @Test
  public void testAddNumberNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    Node numberNode = IR.number(42.0);
    generator.add(numberNode);
    assertEquals("42.0", consumer.sb.toString());
  }

  @Test
  public void testAddStringNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);
    Node stringNode = IR.string("test");
    generator.add(stringNode);
    assertEquals("\"test\"", consumer.sb.toString());
  }

  @Test
  public void testAddTrueFalseNullThis() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);

    generator.add(new Node(Token.TRUE));
    generator.add(new Node(Token.FALSE));
    generator.add(new Node(Token.NULL));
    generator.add(new Node(Token.THIS));

    assertEquals("truefalsenullthis", consumer.sb.toString());
  }

  @Test
  public void testAddUnaryOperators() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);

    Node notNode = new Node(Token.NOT, IR.name("x"));
    generator.add(notNode);
    assertEquals("!x", consumer.sb.toString());
  }

  @Test
  public void testAddBinaryOperator() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = CodeGenerator.forCostEstimation(consumer);

    Node addNode = new Node(Token.ADD, IR.number(1.0), IR.number(2.0));
    generator.add(addNode);
    assertEquals("1.0+2.0", consumer.sb.toString());
  }
}