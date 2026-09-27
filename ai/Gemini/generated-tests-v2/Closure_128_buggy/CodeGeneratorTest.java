package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class CodeGeneratorTest {

  private static class DummyCodeConsumer extends CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

    @Override
    void add(String str) {
      sb.append(str);
    }

    @Override
    boolean continueProcessing() {
      return true;
    }

    @Override
    String getResult() {
      return sb.toString();
    }
  }

  @Test
  public void testIsSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("123"));
    assertFalse(CodeGenerator.isSimpleNumber("0123"));
    assertFalse(CodeGenerator.isSimpleNumber("123a"));
    assertFalse(CodeGenerator.isSimpleNumber(""));
    assertFalse(CodeGenerator.isSimpleNumber("-123"));
  }

  @Test
  public void testGetSimpleNumber() throws Throwable {
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.0);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("0123")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("invalid")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("9999999999999999999999")));
  }

  @Test
  public void testForCostEstimation() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    assertNotNull(cg);
  }

  @Test
  public void testCompilerOptionsConstructor() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CompilerOptions options = new CompilerOptions();
    options.setOutputCharset(Charset.forName("UTF-8"));
    options.preferSingleQuotes = true;
    options.trustedStrings = false;

    CodeGenerator cg = new CodeGenerator(consumer, options);
    assertNotNull(cg);

    options.setOutputCharset(null);
    CodeGenerator cg2 = new CodeGenerator(consumer, options);
    assertNotNull(cg2);
  }

  @Test
  public void testTagAsStrict() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    cg.tagAsStrict();
    assertEquals("'use strict';", consumer.getResult());
  }

  @Test
  public void testAddStringAndNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    cg.add("hello");
    assertEquals("hello", consumer.getResult());

    Node numNode = IR.number(42);
    cg.add(numNode);
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    String escaped = cg.escapeToDoubleQuotedJsString("test\n\"quote\"");
    assertTrue(escaped.contains("\\n"));
    assertTrue(escaped.contains("\\\""));
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    String escaped = cg.regexpEscape("abc/def");
    assertTrue(escaped.startsWith("/"));
    assertTrue(escaped.endsWith("/"));
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    String escapedLatin = CodeGenerator.identifierEscape("myVar");
    assertEquals("myVar", escapedLatin);

    String escapedNonLatin = CodeGenerator.identifierEscape("a\u0100b");
    assertTrue(escapedNonLatin.contains("\\u0100"));
  }

  @Test
  public void testAddList() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node list = IR.block(IR.number(1), IR.number(2));
    cg.addList(list.getFirstChild());
    assertTrue(consumer.getResult().length() > 0);
  }

  @Test
  public void testAddArrayList() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node list = IR.block(IR.number(1), IR.empty(), IR.number(2));
    cg.addArrayList(list.getFirstChild());
    assertTrue(consumer.getResult().length() > 0);
  }

  @Test
  public void testAddAllSiblings() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);
    Node list = IR.block(IR.number(1), IR.number(2));
    cg.addAllSiblings(list.getFirstChild());
    assertTrue(consumer.getResult().length() > 0);
  }

  @Test
  public void testCodeGeneratorEdgeCases() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CompilerOptions options = new CompilerOptions();
    CodeGenerator cg = new CodeGenerator(consumer, options);

    Node stringNode = IR.string("test<script> & =--");
    cg.add(stringNode);
    assertTrue(consumer.getResult().length() > 0);
  }

  @Test
  public void testUnaryAndBinaryOperators() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator cg = CodeGenerator.forCostEstimation(consumer);

    Node negNode = IR.neg(IR.number(5));
    cg.add(negNode);

    Node notNode = IR.not(IR.trueNode());
    cg.add(notNode);
  }
}