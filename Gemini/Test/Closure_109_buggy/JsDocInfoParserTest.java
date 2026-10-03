package com.google.javascript.jscomp.parsing;

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsDocInfoParserTest {

  @Test
  public void testParseTypeStringValid() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{string}");
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString(null);
    assertNull(node);
  }

  @Test
  public void testParseTypeStringEmpty() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("");
    assertNull(node);
  }

  @Test
  public void testParseTypeStringComplex() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{Array.<string>}");
    assertNotNull(node);
  }

  @Test
  public void testInlineTypeDoc() throws Throwable {
    Config config = new Config(
        Sets.<String>newHashSet(),
        Sets.<String>newHashSet(),
        false,
        LanguageMode.ECMASCRIPT3,
        false);
    JsDocInfoParser parser = new JsDocInfoParser(
        new JsDocTokenStream("{number}"),
        null,
        null,
        config,
        NullErrorReporter.forNewRhino());

    com.google.javascript.rhino.JSDocInfo info = parser.parseInlineTypeDoc();
    assertNotNull(info);
  }

  @Test
  public void testParseEmptyComment() throws Throwable {
    Config config = new Config(
        Sets.<String>newHashSet(),
        Sets.<String>newHashSet(),
        false,
        LanguageMode.ECMASCRIPT3,
        false);
    JsDocInfoParser parser = new JsDocInfoParser(
        new JsDocTokenStream(""),
        null,
        null,
        config,
        NullErrorReporter.forNewRhino());

    boolean result = parser.parse();
    assertTrue(result);
  }
}