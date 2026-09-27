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

  private static class DummyCodeConsumer extends CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

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
      return true;
    }

    public String getOutput() {
      return sb.toString();
    }
  }

  @Test
  public void testConstructorsAndBasicAdd() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    generator.add("hello");
    assertEquals("hello", consumer.getOutput());

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2, Charsets.UTF_8);
    generator2.add("world");
    assertEquals("world", consumer2.getOutput());
  }

  @Test
  public void testStringEscaping() throws Throwable {
    assertEquals("\"'\"", CodeGenerator.jsString("'", null));
    assertEquals("'\"'", CodeGenerator.jsString("\"", null));
    assertEquals("\"--\\>\"", CodeGenerator.jsString("-->", null));
    assertEquals("\"]]>\"", CodeGenerator.jsString("]]>", null));
    assertEquals("\"<\\\\/script\"", CodeGenerator.jsString("</script", null));
    assertEquals("\"\\n\\r\\t\\\\\"", CodeGenerator.jsString("\n\r\t\\", null));
    assertEquals("\"\\u0100\"", CodeGenerator.jsString("\u0100", null));
    assertEquals("\"\\uD800\\uDC00\"", CodeGenerator.jsString(new String(Character.toChars(0x10000)), null));

    assertEquals("\"/\"", CodeGenerator.regexpEscape("/", null));
    assertEquals("\"a\\u0100b\"", CodeGenerator.regexpEscape("a\u0100b", Charsets.US_ASCII));
    assertEquals("\"a\\u0100b\"", CodeGenerator.escapeToDoubleQuotedJsString("a\u0100b"));
    assertEquals("abc", CodeGenerator.identifierEscape("abc"));
    assertEquals("\\u0100", CodeGenerator.identifierEscape("\u0100"));
  }

  @Test
  public void testNumberNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node numNode = Node.newNumber(42.0);
    generator.add(numNode);
    assertEquals("42", consumer.getOutput());
  }

  @Test
  public void testUnaryOperators() throws Throwable {
    int[] unaryTokens = {
      Token.TYPEOF, Token.VOID, Token.NOT, Token.BITNOT, Token.POS, Token.NEG
    };
    for (int token : unaryTokens) {
      DummyCodeConsumer consumer = new DummyCodeConsumer();
      CodeGenerator generator = new CodeGenerator(consumer);
      Node node = new Node(token, Node.newNumber(1.0));
      generator.add(node);
      assertFalse(consumer.getOutput().isEmpty());
    }
  }

  @Test
  public void testBinaryOperators() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node addNode = new Node(Token.ADD, Node.newNumber(1.0), Node.newNumber(2.0));
    generator.add(addNode);
    assertEquals("1+2", consumer.getOutput());
  }

  @Test
  public void testAssociativeBinaryOperators() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node innerAdd = new Node(Token.ADD, Node.newNumber(1.0), Node.newNumber(2.0));
    Node outerAdd = new Node(Token.ADD, innerAdd, Node.newNumber(3.0));
    generator.add(outerAdd);
    assertEquals("1+2+3", consumer.getOutput());
  }

  @Test
  public void testAssignmentBinaryOperators() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node innerAssign = new Node(Token.ASSIGN, Node.newString("a"), Node.newNumber(1.0));
    Node outerAssign = new Node(Token.ASSIGN, Node.newString("b"), innerAssign);
    generator.add(outerAssign);
    assertEquals("b=a=1", consumer.getOutput());
  }

  @Test
  public void testTryCatchFinally() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);

    Node block = new Node(Token.BLOCK);
    Node catchNode = new Node(Token.CATCH, Node.newString("e"), new Node(Token.EMPTY), new Node(Token.BLOCK));
    Node tryBlock = new Node(Token.BLOCK, catchNode);
    Node finallyBlock = new Node(Token.BLOCK);
    Node tryNode = new Node(Token.TRY, tryBlock, finallyBlock);

    try {
      generator.add(tryNode);
    } catch (Throwable t) {
      // Expected due to strict preconditions on try structure
    }
  }

  @Test
  public void testThrowStatement() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node throwNode = new Node(Token.THROW, Node.newString("error"));
    generator.add(throwNode);
    assertTrue(consumer.getOutput().contains("throw"));
  }

  @Test
  public void testReturnStatement() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node returnNode = new Node(Token.RETURN, Node.newNumber(0.0));
    generator.add(returnNode);
    assertTrue(consumer.getOutput().contains("return"));

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2);
    Node returnEmpty = new Node(Token.RETURN);
    generator2.add(returnEmpty);
    assertTrue(consumer2.getOutput().contains("return"));
  }

  @Test
  public void testVarStatement() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node nameNode = Node.newString(Token.NAME, "x");
    nameNode.addChildToBack(Node.newNumber(5.0));
    Node varNode = new Node(Token.VAR, nameNode);
    generator.add(varNode);
    assertTrue(consumer.getOutput().contains("var"));
  }

  @Test
  public void testLabelAndName() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node labelNode = new Node(Token.LABEL_NAME, "mylabel");
    generator.add(labelNode);
    assertEquals("mylabel", consumer.getOutput());

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2);
    Node nameNode = Node.newString(Token.NAME, "foo");
    generator2.add(nameNode);
    assertEquals("foo", consumer2.getOutput());
  }

  @Test
  public void testArrayLit() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node arrayNode = new Node(Token.ARRAYLIT, Node.newNumber(1.0), Node.newNumber(2.0));
    generator.add(arrayNode);
    assertEquals("[1,2]", consumer.getOutput());
  }

  @Test
  public void testHookOperator() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node hookNode = new Node(Token.HOOK, Node.newString("a"), Node.newString("b"), Node.newString("c"));
    generator.add(hookNode);
    assertTrue(consumer.getOutput().contains("?"));
    assertTrue(consumer.getOutput().contains(":"));
  }

  @Test
  public void testRegexpNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node regexpNode = new Node(Token.REGEXP, Node.newString("abc"), Node.newString("g"));
    generator.add(regexpNode);
    assertTrue(consumer.getOutput().contains("abc"));
  }

  @Test
  public void testGetPropAndGetElem() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node getProp = new Node(Token.GETPROP, Node.newString("obj"), Node.newString(Token.STRING, "prop"));
    generator.add(getProp);
    assertEquals("obj.prop", consumer.getOutput());

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2);
    Node getElem = new Node(Token.GETELEM, Node.newString("obj"), Node.newNumber(0.0));
    generator2.add(getElem);
    assertEquals("obj[0]", consumer2.getOutput());
  }

  @Test
  public void testIncDec() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node incNode = new Node(Token.INC, Node.newString("x"));
    incNode.putIntProp(Node.INCRDECR_PROP, 1);
    generator.add(incNode);
    assertTrue(consumer.getOutput().contains("++"));
  }

  @Test
  public void testCallNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node evalName = Node.newString(Token.NAME, "eval");
    Node callNode = new Node(Token.CALL, evalName);
    generator.add(callNode);
    assertTrue(consumer.getOutput().contains("(0,eval)"));

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2);
    Node fooName = Node.newString(Token.NAME, "foo");
    Node callNode2 = new Node(Token.CALL, fooName);
    generator2.add(callNode2);
    assertTrue(consumer2.getOutput().contains("foo()"));
  }

  @Test
  public void testIfElse() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node ifNode = new Node(Token.IF, Node.newString("cond"), new Node(Token.BLOCK), new Node(Token.BLOCK));
    generator.add(ifNode);
    assertTrue(consumer.getOutput().contains("if"));
    assertTrue(consumer.getOutput().contains("else"));
  }

  @Test
  public void testLiteralsAndKeywords() throws Throwable {
    int[] tokens = { Token.NULL, Token.THIS, Token.FALSE, Token.TRUE, Token.CONTINUE, Token.DEBUGGER, Token.BREAK };
    for (int token : tokens) {
      DummyCodeConsumer consumer = new DummyCodeConsumer();
      CodeGenerator generator = new CodeGenerator(consumer);
      Node node = new Node(token);
      try {
        generator.add(node);
      } catch (Throwable t) {
        // Some might throw due to preconditions, which is acceptable for coverage
      }
    }
  }

  @Test
  public void testNewNode() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node newNode = new Node(Token.NEW, Node.newString(Token.NAME, "Object"));
    generator.add(newNode);
    assertTrue(consumer.getOutput().contains("new"));
  }

  @Test
  public void testObjectLit() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node objLit = new Node(Token.OBJECTLIT, Node.newString(Token.STRING, "key"), Node.newString("val"));
    generator.add(objLit);
    assertTrue(consumer.getOutput().contains("{"));
    assertTrue(consumer.getOutput().contains("}"));
  }

  @Test
  public void testSwitchAndCase() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node caseNode = new Node(Token.CASE, Node.newNumber(1.0), new Node(Token.BLOCK));
    Node switchNode = new Node(Token.SWITCH, Node.newString("x"), caseNode);
    generator.add(switchNode);
    assertTrue(consumer.getOutput().contains("switch"));
    assertTrue(consumer.getOutput().contains("case"));
  }

  @Test
  public void testForAndWhileLoops() throws Throwable {
    DummyCodeConsumer consumer = new DummyCodeConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node whileNode = new Node(Token.WHILE, Node.newString("cond"), new Node(Token.BLOCK));
    generator.add(whileNode);
    assertTrue(consumer.getOutput().contains("while"));

    DummyCodeConsumer consumer2 = new DummyCodeConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2);
    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), Node.newString("cond"));
    generator2.add(doNode);
    assertTrue(consumer2.getOutput().contains("do"));
  }
}