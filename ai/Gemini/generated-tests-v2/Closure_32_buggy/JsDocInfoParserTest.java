package com.google.javascript.jscomp.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.common.collect.Sets;

public class JsDocInfoParserTest {

  @Test
  public void testParseTypeStringValid() throws Throwable {
    String typeStr = "string";
    Node node = JsDocInfoParser.parseTypeString(typeStr);
    assertNotNull(node);
  }

  @Test
  public void testParseTypeStringNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("");
    assertNull(node);
  }

  @Test
  public void testParseTypeStringComplex() throws Throwable {
    String typeStr = "(string|number)";
    Node node = JsDocInfoParser.parseTypeString(typeStr);
    assertNotNull(node);
  }

  @Test
  public void testConfigCreation() throws Throwable {
    Config config = new Config(
        Sets.<String>newHashSet("custom"),
        Sets.<String>newHashSet("check"),
        false,
        LanguageMode.ECMASCRIPT3,
        false
    );
    assertNotNull(config);
  }
}