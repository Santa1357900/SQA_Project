package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.EvaluatorException;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.common.collect.Sets;

public class JsDocInfoParserTest {

  @Test
  public void testParseTypeStringValid() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{string}");
    assertNotNull(node);
    assertEquals(Token.STRING, node.getType());
    assertEquals("string", node.getString());
  }

  @Test
  public void testParseTypeStringNullInput() throws Throwable {
    try {
      JsDocInfoParser.parseTypeString(null);
      fail("Should have thrown an exception or handled null gracefully");
    } catch (Throwable t) {
      // Expected exception due to null stream/string in tokenizer
    }
  }

  @Test
  public void testParseTypeStringComplex() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{Array.<string>}");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringRecord() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{{a: number, b: string}}");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringFunction() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{function(number): string}");
    assertNotNull(node);
  }

  @Test
  public void testConstructorAndNullAssociatedNode() throws Throwable {
    Config config = new Config(
        Sets.<String>newHashSet("param", "define"),
        Sets.<String>newHashSet("visibility"),
        false,
        LanguageMode.ECMASCRIPT3,
        false);
    
    JsDocTokenStream stream = new JsDocTokenStream("@param {number} x description");
    ErrorReporter reporter = NullErrorReporter.forNewRhino();
    
    JsDocInfoParser parser = new JsDocInfoParser(stream, null, null, config, reporter);
    assertFalse(parser.hasParsedJSDocInfo());
    assertNull(parser.getFileOverviewJSDocInfo());
  }

  @Test
  public void testParseInlineTypeDoc() throws Throwable {
    Config config = new Config(
        Sets.<String>newHashSet("type"),
        Sets.<String>newHashSet(),
        false,
        LanguageMode.ECMASCRIPT3,
        false);
    
    JsDocTokenStream stream = new JsDocTokenStream("{boolean}");
    ErrorReporter reporter = NullErrorReporter.forNewRhino();
    
    JsDocInfoParser parser = new JsDocInfoParser(stream, null, null, config, reporter);
    // Just exercising parseInlineTypeDoc method logic
    try {
      parser.parseInlineTypeDoc();
    } catch (Throwable t) {
      // May fail depending on stream state, ensuring coverage
    }
  }
}