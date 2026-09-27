package com.google.javascript.jscomp;

import com.google.common.base.Charsets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CodeGeneratorTest {

  private static final class TestCodeConsumer extends CodeConsumer {
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

    public String getString() {
      return sb.toString();
    }
  }

  @Test
  public void testTagAsStrict() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    generator.tagAsStrict();
    assertEquals("'use strict';", consumer.getString());
  }

  @Test
  public void testSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("123"));
    assertFalse(CodeGenerator.isSimpleNumber("123a"));
    assertFalse(CodeGenerator.isSimpleNumber(""));
    
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("123a")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("9999999999999999999999")));
  }

  @Test
  public void testStringEscaping() throws Throwable {
    String escapedDouble = CodeGenerator.escapeToDoubleQuotedJsString("test\"s");
    assertEquals("\"test\\\"s\"", escapedDouble);

    String regEsc = CodeGenerator.regexpEscape("test/regexp");
    assertTrue(regEsc.contains("/regexp"));

    String regEscWithEncoder = CodeGenerator.regexpEscape("abc", Charsets.US_ASCII);
    assertNotNull(regEscWithEncoder);

    String jsStr = CodeGenerator.escapeToDoubleQuotedJsString("a\0\n\r\t\\\'\"><");
    assertNotNull(jsStr);
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    String latin = CodeGenerator.identifierEscape("abcXYZ123");
    assertEquals("abcXYZ123", latin);

    String nonLatin = CodeGenerator.identifierEscape("a\u0101b");
    assertTrue(nonLatin.contains("\\u0101"));
  }

  @Test
  public void testConstructorsAndCharsets() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator gen1 = new CodeGenerator(consumer, Charsets.US_ASCII);
    CodeGenerator gen2 = new CodeGenerator(consumer, Charsets.UTF_8);
    assertNotNull(gen1);
    assertNotNull(gen2);
  }

  @Test
  public void testAddNumberNode() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node numNode = IR.number(42.5);
    generator.add(numNode);
    assertEquals("42.5", consumer.getString());
  }

  @Test
  public void testAddStringNode() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node strNode = IR.string("hello");
    generator.add(strNode);
    assertEquals("\"hello\"", consumer.getString());
  }

  @Test
  public void testAddNameNode() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node nameNode = IR.name("x");
    generator.add(nameNode);
    assertEquals("x", consumer.getString());
  }

  @Test
  public void testAddBinaryOperator() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node addNode = new Node(Token.ADD, IR.name("a"), IR.name("b"));
    generator.add(addNode);
    assertEquals("a+b", consumer.getString());
  }

  @Test
  public void testUnaryOperator() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node notNode = new Node(Token.NOT, IR.name("x"));
    generator.add(notNode);
    assertEquals("!x", consumer.getString());
  }

  @Test
  public void testNegativeNumberHandling() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node negNode = new Node(Token.NEG, IR.number(5.0));
    generator.add(negNode);
    assertEquals("-5.0", consumer.getString());
  }

  @Test
  public void testArrayLiteral() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node arrayNode = new Node(Token.ARRAYLIT, IR.number(1), IR.number(2));
    generator.add(arrayNode);
    assertEquals("[1,2]", consumer.getString());
  }

  @Test
  public void testIfStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node ifNode = new Node(Token.IF, IR.name("cond"), IR.block(IR.exprResult(IR.name("x"))));
    generator.add(ifNode);
    assertTrue(consumer.getString().contains("if(cond)"));
  }

  @Test
  public void testWhileStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node whileNode = new Node(Token.WHILE, IR.name("cond"), IR.block(IR.exprResult(IR.name("x"))));
    generator.add(whileNode);
    assertTrue(consumer.getString().contains("while(cond)"));
  }

  @Test
  public void testDoWhileStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node doNode = new Node(Token.DO, IR.block(IR.exprResult(IR.name("x"))), IR.name("cond"));
    generator.add(doNode);
    assertTrue(consumer.getString().contains("do"));
    assertTrue(consumer.getString().contains("while(cond)"));
  }

  @Test
  public void testReturnStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node retNode = new Node(Token.RETURN, IR.number(1));
    generator.add(retNode);
    assertTrue(consumer.getString().contains("return"));
  }

  @Test
  public void testThrowStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node throwNode = new Node(Token.THROW, IR.name("e"));
    generator.add(throwNode);
    assertTrue(consumer.getString().contains("throw"));
  }

  @Test
  public void testTryCatchFinally() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node tryBody = IR.block(IR.exprResult(IR.name("a")));
    Node catchNode = new Node(Token.CATCH, IR.name("err"), IR.block(IR.exprResult(IR.name("b"))));
    Node catchBlock = IR.block(catchNode);
    Node finallyBody = IR.block(IR.exprResult(IR.name("c")));
    
    Node tryNode = new Node(Token.TRY, tryBody, catchBlock, finallyBody);
    generator.add(tryNode);
    assertTrue(consumer.getString().contains("try"));
    assertTrue(consumer.getString().contains("catch"));
    assertTrue(consumer.getString().contains("finally"));
  }

  @Test
  public void testSwitchStatement() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node caseNode = new Node(Token.CASE, IR.number(1), IR.block(IR.exprResult(IR.name("x"))));
    Node switchNode = new Node(Token.SWITCH, IR.name("val"), caseNode);
    generator.add(switchNode);
    assertTrue(consumer.getString().contains("switch(val)"));
    assertTrue(consumer.getString().contains("case 1"));
  }

  @Test
  public void testObjectLiteral() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node stringKey = IR.string("key");
    stringKey.addChildToBack(IR.number(1));
    Node objNode = new Node(Token.OBJECTLIT, stringKey);
    generator.add(objNode);
    assertTrue(consumer.getString().contains("{"));
    assertTrue(consumer.getString().contains("key"));
  }

  @Test
  public void testHookOperator() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node hookNode = new Node(Token.HOOK, IR.name("cond"), IR.number(1), IR.number(2));
    generator.add(hookNode);
    assertTrue(consumer.getString().contains("?"));
    assertTrue(consumer.getString().contains(":"));
  }

  @Test
  public void testContinueAndBreak() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node contNode = new Node(Token.CONTINUE);
    generator.add(contNode);
    assertTrue(consumer.getString().contains("continue"));

    Node breakNode = new Node(Token.BREAK);
    generator.add(breakNode);
    assertTrue(consumer.getString().contains("break"));
  }

  @Test
  public void testDebuggerNode() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node dbgNode = new Node(Token.DEBUGGER);
    generator.add(dbgNode);
    assertTrue(consumer.getString().contains("debugger"));
  }

  @Test
  public void testNullTrueFalseThisNodes() throws Throwable {
    TestCodeConsumer consumer = new TestCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    generator.add(new Node(Token.NULL));
    generator.add(new Node(Token.TRUE));
    generator.add(new Node(Token.FALSE));
    generator.add(new Node(Token.THIS));
    
    String res = consumer.getString();
    assertTrue(res.contains("null"));
    assertTrue(res.contains("true"));
    assertTrue(res.contains("false"));
    assertTrue(res.contains("this"));
  }
}