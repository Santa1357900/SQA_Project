package com.google.javascript.jscomp;

import com.google.common.base.Charsets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CodeGeneratorTest {

  private static class TestCodeConsumer extends CodeConsumer {
    private final StringBuilder sb = new StringBuilder();
    private boolean continueProc = true;

    @Override
    void add(String str) {
      sb.append(str);
    }

    @Override
    void add(char c) {
      sb.append(c);
    }

    @Override
    boolean continueProcessing() {
      return continueProc;
    }

    public String getResult() {
      return sb.toString();
    }
  }

  @Test
  public void testConstructorsAndBasicAdd() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    generator.tagAsStrict();
    assertEquals("'use strict';", consumer.getResult());
  }

  @Test
  public void testConstructorsWithCharset() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generatorUtf8 = new CodeGenerator(consumer, Charsets.UTF_8);
    generatorUtf8.add("test");
    assertEquals("test", consumer.getResult());

    TestCodeConsumer consumerAscii = new TestCodeConsumer();
    CodeGenerator generatorAscii = new CodeGenerator(consumerAscii, Charsets.US_ASCII);
    generatorAscii.add("ascii");
    assertEquals("ascii", consumerAscii.getResult());
  }

  @Test
  public void testStringEscaping() throws Throwable {
    assertEquals("\"'\"", CodeGenerator.jsString("'", Charsets.US_ASCII.newEncoder()));
    assertEquals("'\"'", CodeGenerator.jsString("\"", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"--\\>\"", CodeGenerator.jsString("-->", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"]]>\"", CodeGenerator.jsString("]]>", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"<\\\\/script\"", CodeGenerator.jsString("</script", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"<\\\\!--\"", CodeGenerator.jsString("<!--", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"\\n\\r\\t\\\\\"", CodeGenerator.jsString("\n\r\t\\", Charsets.US_ASCII.newEncoder()));
    assertEquals("\"\\u0000\"", CodeGenerator.jsString("\u0000", Charsets.US_ASCII.newEncoder()));
    
    // Test supplementary code point in escaping
    String surrogateStr = new String(Character.toChars(0x1F000));
    String escaped = CodeGenerator.jsString(surrogateStr, null);
    assertNotNull(escaped);
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    String reg = CodeGenerator.regexpEscape("abc/def", Charsets.US_ASCII.newEncoder());
    assertEquals("/abc\\/def/", reg);

    String regSimple = CodeGenerator.regexpEscape("test");
    assertEquals("/test/", regSimple);
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    String res = CodeGenerator.escapeToDoubleQuotedJsString("hello\"world");
    assertEquals("\"hello\\\"world\"", res);
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    String latin = CodeGenerator.identifierEscape("validId");
    assertEquals("validId", latin);

    String nonLatin = CodeGenerator.identifierEscape("id\u1234");
    assertEquals("id\\u1234", nonLatin);
  }

  @Test
  public void testBinaryOperatorsAndAssociativity() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node addNode = IR.add(IR.name("a"), IR.name("b"));
    generator.add(addNode);
    assertEquals("a+b", consumer.getResult());
  }

  @Test
  public void testUnaryOperators() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node notNode = IR.not(IR.name("a"));
    generator.add(notNode);
    assertEquals("!a", consumer.getResult());

    TestCodeConsumer consumerNeg = new TestCodeConsumer();
    CodeGenerator generatorNeg = new CodeGenerator(consumerNeg);
    Node negNum = IR.neg(IR.number(5.0));
    generatorNeg.add(negNum);
    assertEquals("-5", consumerNeg.getResult());

    TestCodeConsumer consumerNegExpr = new TestCodeConsumer();
    CodeGenerator generatorNegExpr = new CodeGenerator(consumerNegExpr);
    Node negExpr = IR.neg(IR.name("a"));
    generatorNegExpr.add(negExpr);
    assertEquals("-a", consumerNegExpr.getResult());
  }

  @Test
  public void testLiteralNodes() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    generator.add(IR.nullNode());
    generator.add(IR.thisNode());
    generator.add(IR.trueNode());
    generator.add(IR.falseNode());
    assertEquals("nullthisसुद्धाtruefalse", consumer.getResult().substring(0, 4) + "this" + "truefalse" == null ? "" : consumer.getResult());
  }

  @Test
  public void testArrayLitAndObjectLit() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node arr = IR.arraylit(IR.number(1.0), IR.empty(), IR.number(2.0));
    generator.add(arr);
    assertEquals("[1,,2]", consumer.getResult());
  }

  @Test
  public void testIfElseStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node ifNode = new Node(Token.IF, IR.trueNode(), IR.block(IR.empty()), IR.block(IR.empty()));
    generator.add(ifNode);
    assertTrue(consumer.getResult().contains("if"));
  }

  @Test
  public void testTryCatchFinally() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node tryBody = IR.block();
    Node catchNode = new Node(Token.CATCH, IR.name("e"), IR.block());
    Node catchBlockContainer = IR.block(catchNode);
    Node finallyBody = IR.block();

    Node tryNode = new Node(Token.TRY, tryBody, catchBlockContainer, finallyBody);
    generator.add(tryNode);
    assertTrue(consumer.getResult().contains("try"));
    assertTrue(consumer.getResult().contains("catch"));
    assertTrue(consumer.getResult().contains("finally"));
  }

  @Test
  public void testSwitchCaseDefault() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node caseNode = new Node(Token.CASE, IR.number(1.0), IR.block(IR.breakNode()));
    Node defaultNode = new Node(Token.DEFAULT, IR.block(IR.breakNode()));
    Node switchNode = new Node(Token.SWITCH, IR.name("x"), caseNode, defaultNode);

    generator.add(switchNode);
    assertTrue(consumer.getResult().contains("switch"));
    assertTrue(consumer.getResult().contains("case"));
    assertTrue(consumer.getResult().contains("default"));
  }

  @Test
  public void testFunctionAndCall() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node funcNode = new Node(Token.FUNCTION, IR.name(""), IR.paramList(), IR.block());
    generator.add(funcNode);
    assertTrue(consumer.getResult().contains("function"));
  }

  @Test
  public void testHookOperator() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node hookNode = new Node(Token.HOOK, IR.trueNode(), IR.number(1.0), IR.number(2.0));
    generator.add(hookNode);
    assertEquals("true?1:2", consumer.getResult());
  }

  @Test
  public void testGetPropAndGetElem() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node getProp = IR.getprop(IR.name("a"), IR.string("b"));
    generator.add(getProp);
    assertEquals("a.b", consumer.getResult());

    TestCodeConsumer consumerElem = new TestCodeConsumer();
    CodeGenerator generatorElem = new CodeGenerator(consumerElem);
    Node getElem = IR.getelem(IR.name("a"), IR.string("b"));
    generatorElem.add(getElem);
    assertEquals("a[\"b\"]", consumerElem.getResult());
  }

  @Test
  public void testContinueAndBreakWithLabel() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node labelName = new Node(Token.LABEL_NAME, "lbl");
    Node breakNode = new Node(Token.BREAK, labelName);
    generator.add(breakNode);
    assertEquals("break lbl;", consumer.getResult());
  }

  @Test
  public void testContinueWithLabel() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node labelName = new Node(Token.LABEL_NAME, "lbl");
    Node continueNode = new Node(Token.CONTINUE, labelName);
    generator.add(continueNode);
    assertEquals("continue lbl;", consumer.getResult());
  }
}