package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.jscomp.mozilla.rhino.EvaluatorException;
import com.google.javascript.jscomp.mozilla.rhino.tools.ToolErrorReporter;

public class JsDocInfoParserTest {

  @Test
  public void testParseTypeStringSimple() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("number");
    assertNotNull(node);
    assertEquals(Node.STRING, node.getType());
    assertEquals("number", node.getString());
  }

  @Test
  public void testParseTypeStringNullable() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("?number");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringFunction() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(number, string): boolean");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringRecord() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{a: number, b: string}");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringArray() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("[number]");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringUnion() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("(number|string)");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("null");
    assertNotNull(node);
    assertEquals("null", node.getString());
  }

  @Test
  public void testParseTypeStringUndefined() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("undefined");
    assertNotNull(node);
    assertEquals("undefined", node.getString());
  }

  @Test
  public void testParseTypeStringStar() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("*");
    assertNotNull(node);
    assertEquals(com.google.javascript.rhino.Token.STAR, node.getType());
  }

  @Test
  public void testParseTypeStringEmpty() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("");
    assertNull(node);
  }
}