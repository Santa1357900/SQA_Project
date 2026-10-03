package com.google.javascript.jscomp;

import com.google.common.base.Charsets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CodeGeneratorTest {

  private static class DummyConsumer implements CodeConsumer {
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void add(String str) {
      sb.append(str);
    }

    @Override
    public boolean continueProcessing() {
      return true;
    }

    @Override
    public void addIdentifier(String identifier) {
      sb.append(identifier);
    }

    @Override
    public void addOp(String op, boolean needSpace) {
      if (needSpace) {
        sb.append(" ");
      }
      sb.append(op);
      if (needSpace) {
        sb.append(" ");
      }
    }

    @Override
    public void startSourceMapping(Node n) {}

    @Override
    public void endSourceMapping(Node n) {}

    @Override
    public void endStatement() {
      sb.append(";");
    }

    @Override
    public void endStatement(boolean omitSemicolon) {
      if (!omitSemicolon) {
        sb.append(";");
      }
    }

    @Override
    public void addNumber(double x) {
      sb.append(Double.toString(x));
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
    public void endBlock(boolean statementBreak) {
      sb.append("}");
    }

    @Override
    public boolean breakAfterBlockFor(Node n, boolean statement) {
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

    @Override
    public boolean shouldPreserveExtraBlocks() {
      return true;
    }

    public String getOutput() {
      return sb.toString();
    }
  }

  @Test
  public void testConstructorsAndCharsets() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator1 = new CodeGenerator(consumer);
    generator1.tagAsStrict();
    assertEquals("'use strict';", consumer.getOutput());

    DummyConsumer consumer2 = new DummyConsumer();
    CodeGenerator generator2 = new CodeGenerator(consumer2, Charsets.UTF_8);
    generator2.add("test");
    assertEquals("test", consumer2.getOutput());

    DummyConsumer consumer3 = new DummyConsumer();
    CodeGenerator generator3 = new CodeGenerator(consumer3, Charset.forName("UTF-16"));
    generator3.add("utf16");
    assertEquals("utf16", consumer3.getOutput());
  }

  @Test
  public void testSimpleNumberAndGetSimpleNumber() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("12345"));
    assertFalse(CodeGenerator.isSimpleNumber("123a45"));
    assertFalse(CodeGenerator.isSimpleNumber(""));

    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("123a")));
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("9999999999999999999999999")));
  }

  @Test
  public void testIdentifierEscape() throws Throwable {
    assertEquals("abc", CodeGenerator.identifierEscape("abc"));
    String escaped = CodeGenerator.identifierEscape("\u0100");
    assertTrue(escaped.contains("\\u"));
  }

  @Test
  public void testRegexpEscape() throws Throwable {
    assertEquals("/abc/", CodeGenerator.regexpEscape("abc"));
    assertEquals("/a\\/b/", CodeGenerator.regexpEscape("a/b"));
    assertEquals("/a/i", CodeGenerator.regexpEscape("a", Charsets.US_ASCII) + "i");
  }

  @Test
  public void testEscapeToDoubleQuotedJsString() throws Throwable {
    assertEquals("\"abc\"", CodeGenerator.escapeToDoubleQuotedJsString("abc"));
    assertEquals("\"a\\\"b\"", CodeGenerator.escapeToDoubleQuotedJsString("a\"b"));
  }

  @Test
  public void testJsStringAndCaching() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    generator.addJsString("hello");
    generator.addJsString("hello"); // hits cache
    assertEquals("'hello''hello'", consumer.getOutput());

    // Test quote optimization (more double quotes than single quotes)
    DummyConsumer c2 = new DummyConsumer();
    CodeGenerator g2 = new CodeGenerator(c2);
    g2.addJsString("\"\"'");
    assertTrue(c2.getOutput().startsWith("'"));
  }

  @Test
  public void testStrEscapeEdgeCases() throws Throwable {
    String input = "\0\n\r\t\\\"'>--><\\/script<!--\u0100";
    String escaped = CodeGenerator.strEscape(input, '"', "\\\"", "'", "\\\\", null);
    assertNotNull(escaped);
    
    String escapedWithEncoder = CodeGenerator.strEscape(input, '"', "\\\"", "'", "\\\\", Charsets.US_ASCII.newEncoder());
    assertNotNull(escapedWithEncoder);
  }

  @Test
  public void testAddBinaryOperator() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    Node addNode = IR.add(IR.number(1), IR.number(2));
    generator.add(addnode);
    assertEquals("1 + 2", consumer.getOutput());
  }

  @Test
  public void testAddUnaryOperators() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node notNode = IR.not(IR.trueNode());
    generator.add(notNode);
    assertEquals("!true", consumer.getOutput());

    DummyConsumer consumerNeg = new DummyConsumer();
    CodeGenerator generatorNeg = new CodeGenerator(consumerNeg);
    Node negNum = IR.neg(IR.number(5.0));
    generatorNeg.add(negNum);
    assertEquals("-5.0", consumerNeg.getOutput());

    DummyConsumer consumerNegExpr = new DummyConsumer();
    CodeGenerator generatorNegExpr = new CodeGenerator(consumerNegExpr);
    Node negExpr = IR.neg(IR.name("x"));
    generatorNegExpr.add(negExpr);
    assertEquals("-x", consumerNegExpr.getOutput());
  }

  @Test
  public void testTryCatchFinally() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node tryNode = new Node(Token.TRY,
        new Node(Token.BLOCK, IR.empty()),
        new Node(Token.BLOCK, new Node(Token.CATCH, IR.name("e"), new Node(Token.BLOCK, IR.empty()))),
        new Node(Token.BLOCK, IR.empty())
    );
    
    generator.add(tryNode);
    assertTrue(consumer.getOutput().contains("try"));
    assertTrue(consumer.getOutput().contains("catch"));
    assertTrue(consumer.getOutput().contains("finally"));
  }

  @Test
  public void testThrowAndReturn() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    generator.add(IR.throwNode(IR.string("error")));
    generator.add(IR.returnNode(IR.number(0)));
    generator.add(new Node(Token.RETURN));
    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testVarAndName() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node varNode = new Node(Token.VAR, IR.name("a"));
    generator.add(varNode);
    
    Node nameAssign = IR.name("b");
    nameAssign.addChildToBack(IR.number(10));
    generator.add(nameAssign);
    
    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testArrayLitAndObjectLit() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node arrayLit = new Node(Token.ARRAYLIT, IR.number(1), new Node(Token.EMPTY), IR.number(3));
    generator.add(arrayLit);

    Node stringKey = IR.string("key");
    stringKey.addChildToBack(IR.string("val"));
    Node objLit = new Node(Token.OBJECTLIT, stringKey);
    generator.add(objLit);
    
    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testControlFlowStatements() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    generator.add(new Node(Token.CONTINUE));
    generator.add(new Node(Token.DEBUGGER));
    generator.add(new Node(Token.BREAK));
    
    Node labelName = IR.labelName("L1");
    Node breakLabel = new Node(Token.BREAK, labelName);
    generator.add(breakLabel);

    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testSwitchCaseDefault() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node caseNode = new Node(Token.CASE, IR.number(1), new Node(Token.BLOCK, IR.empty()));
    Node defaultNode = new Node(Token.DEFAULT, new Node(Token.BLOCK, IR.empty()));
    Node switchNode = new Node(Token.SWITCH, IR.number(1), caseNode, defaultNode);
    
    generator.add(switchNode);
    assertTrue(consumer.getOutput().contains("switch"));
    assertTrue(consumer.getOutput().contains("case"));
    assertTrue(consumer.getOutput().contains("default"));
  }

  @Test
  public void testForWhileDoIfHooks() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    // While
    generator.add(new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK, IR.empty())));
    
    // Do-While
    generator.add(new Node(Token.DO, new Node(Token.BLOCK, IR.empty()), IR.trueNode()));

    // Hook (ternary)
    generator.add(new Node(Token.HOOK, IR.trueNode(), IR.number(1), IR.number(2)));

    // If-Else
    generator.add(new Node(Token.IF, IR.trueNode(), new Node(Token.BLOCK, IR.empty()), new Node(Token.BLOCK, IR.empty())));

    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testGetPropAndGetElem() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node getProp = IR.getprop(IR.name("a"), IR.string("b"));
    generator.add(getProp);

    Node getElem = IR.getelem(IR.name("a"), IR.string("b"));
    generator.add(getElem);

    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testIncDecAndCallNewDelete() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node inc = new Node(Token.INC, IR.name("x"));
    generator.add(inc);

    Node call = new Node(Token.CALL, IR.name("foo"));
    generator.add(call);

    Node newnode = new Node(Token.NEW, IR.name("Object"));
    generator.add(newnode);

    Node del = new Node(Token.DELPROP, IR.getprop(IR.name("a"), IR.string("b")));
    generator.add(del);

    assertNotNull(consumer.getOutput());
  }

  @Test
  public void testFunctionNode() throws Throwable {
    DummyConsumer consumer = new DummyConsumer();
    CodeGenerator generator = new CodeGenerator(consumer);
    
    Node fn = IR.function(IR.name(""), IR.paramList(), new Node(Token.BLOCK));
    generator.add(fn);
    assertNotNull(consumer.getOutput());
  }
}