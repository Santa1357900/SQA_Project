package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class JsDocInfoParserClaudeTest {

  private Node parseType(String typeString) {
    return JsDocInfoParser.parseTypeString(typeString);
  }

  private Config newConfig() {
    return new Config(
        Sets.<String>newHashSet(),
        Sets.<String>newHashSet(),
        false,
        Config.LanguageMode.ECMASCRIPT3,
        false);
  }

  private JsDocInfoParser newParser(String commentBody) {
    return new JsDocInfoParser(
        new JsDocTokenStream(commentBody),
        null,
        null,
        newConfig(),
        NullErrorReporter.forNewRhino());
  }

  // parseTypeName: plain identifier becomes a string node with the same name
  @Test
  public void testParseTypeString_simpleName_returnsStringNode() throws Throwable {
    Node result = parseType("Foo");
    assertNotNull(result);
    assertEquals("Foo", result.getString());
  }

  // parseTypeName: dotted identifier is tokenized as a single name
  @Test
  public void testParseTypeString_dottedName_returnsFullString() throws Throwable {
    Node result = parseType("goog.string.Foo");
    assertNotNull(result);
    assertEquals("goog.string.Foo", result.getString());
  }

  // parseBasicTypeExpression: literal "null" keyword branch
  @Test
  public void testParseTypeString_nullKeyword_returnsStringNode() throws Throwable {
    Node result = parseType("null");
    assertNotNull(result);
    assertEquals("null", result.getString());
  }

  // parseBasicTypeExpression: literal "undefined" keyword branch
  @Test
  public void testParseTypeString_undefinedKeyword_returnsStringNode() throws Throwable {
    Node result = parseType("undefined");
    assertNotNull(result);
    assertEquals("undefined", result.getString());
  }

  // parseBasicTypeExpression: '*' produces a STAR node
  @Test
  public void testParseTypeString_star_returnsStarNode() throws Throwable {
    Node result = parseType("*");
    assertNotNull(result);
    assertEquals(Token.STAR, result.getType());
  }

  // parseTypeExpression: prefix '?' wraps in QMARK
  @Test
  public void testParseTypeString_prefixNullable_wrapsQmark() throws Throwable {
    Node result = parseType("?Foo");
    assertNotNull(result);
    assertEquals(Token.QMARK, result.getType());
    assertEquals("Foo", result.getFirstChild().getString());
  }

  // parseTypeExpression: prefix '!' wraps in BANG
  @Test
  public void testParseTypeString_prefixNonNullable_wrapsBang() throws Throwable {
    Node result = parseType("!Foo");
    assertNotNull(result);
    assertEquals(Token.BANG, result.getType());
    assertEquals("Foo", result.getFirstChild().getString());
  }

  // parseTypeExpression: postfix '?' wraps basic type in QMARK
  @Test
  public void testParseTypeString_postfixNullable_wrapsQmark() throws Throwable {
    Node result = parseType("Foo?");
    assertNotNull(result);
    assertEquals(Token.QMARK, result.getType());
    assertEquals("Foo", result.getFirstChild().getString());
  }

  // parseTypeExpression: postfix '!' wraps basic type in BANG
  @Test
  public void testParseTypeString_postfixNonNullable_wrapsBang() throws Throwable {
    Node result = parseType("Foo!");
    assertNotNull(result);
    assertEquals(Token.BANG, result.getType());
    assertEquals("Foo", result.getFirstChild().getString());
  }

  // parseTypeName: single-parameter generic type application
  @Test
  public void testParseTypeString_genericSingleParam_buildsTypeApplication() throws Throwable {
    Node result = parseType("Array.<string>");
    assertNotNull(result);
    assertEquals("Array", result.getString());
    Node list = result.getFirstChild();
    assertEquals(1, list.getChildCount());
    assertEquals("string", list.getFirstChild().getString());
  }

  // parseTypeExpressionList: multiple comma-separated generic params
  @Test
  public void testParseTypeString_genericMultipleParams_buildsTypeApplicationList() throws Throwable {
    Node result = parseType("Object.<string, number>");
    assertNotNull(result);
    Node list = result.getFirstChild();
    assertEquals(2, list.getChildCount());
    assertEquals("string", list.getFirstChild().getString());
    assertEquals("number", list.getLastChild().getString());
  }

  // parseTypeName: missing closing '>' reports syntax error -> null
  @Test
  public void testParseTypeString_genericMissingGt_returnsNull() throws Throwable {
    Node result = parseType("Array.<string");
    assertNull(result);
  }

  // parseUnionType: '(A|B)' builds a PIPE node with 2 children
  @Test
  public void testParseTypeString_unionParenSinglePipe_buildsPipeNode() throws Throwable {
    Node result = parseType("(string|number)");
    assertNotNull(result);
    assertEquals(Token.PIPE, result.getType());
    assertEquals(2, result.getChildCount());
    assertEquals("string", result.getFirstChild().getString());
    assertEquals("number", result.getLastChild().getString());
  }

  // parseUnionTypeWithAlternate: double pipe supported for backwards compatibility
  @Test
  public void testParseTypeString_unionParenDoublePipe_buildsPipeNode() throws Throwable {
    Node result = parseType("(string||number)");
    assertNotNull(result);
    assertEquals(Token.PIPE, result.getType());
    assertEquals(2, result.getChildCount());
  }

  // parseTopLevelTypeExpression: bare union without parens at the top level
  @Test
  public void testParseTypeString_topLevelUnion_buildsPipeNode() throws Throwable {
    Node result = parseType("string|number");
    assertNotNull(result);
    assertEquals(Token.PIPE, result.getType());
    assertEquals(2, result.getChildCount());
    assertEquals("string", result.getFirstChild().getString());
    assertEquals("number", result.getLastChild().getString());
  }

  // parseUnionTypeWithAlternate: missing ')' reports syntax error -> null
  @Test
  public void testParseTypeString_unionMissingRp_returnsNull() throws Throwable {
    Node result = parseType("(string|number");
    assertNull(result);
  }

  // parseArrayType: single element array type '[string]'
  @Test
  public void testParseTypeString_arraySingleElement_buildsLbNode() throws Throwable {
    Node result = parseType("[string]");
    assertNotNull(result);
    assertEquals(Token.LB, result.getType());
    assertEquals(1, result.getChildCount());
    assertEquals("string", result.getFirstChild().getString());
  }

  // parseArrayType: multiple comma separated elements
  @Test
  public void testParseTypeString_arrayMultipleElements_buildsLbNode() throws Throwable {
    Node result = parseType("[string,number]");
    assertNotNull(result);
    assertEquals(2, result.getChildCount());
    assertEquals("string", result.getFirstChild().getString());
    assertEquals("number", result.getLastChild().getString());
  }

  // parseArrayType: '...' rest element wraps child in ELLIPSIS
  @Test
  public void testParseTypeString_arrayVarArgs_buildsEllipsisChild() throws Throwable {
    Node result = parseType("[...string]");
    assertNotNull(result);
    assertEquals(1, result.getChildCount());
    Node child = result.getFirstChild();
    assertEquals(Token.ELLIPSIS, child.getType());
    assertEquals("string", child.getFirstChild().getString());
  }

  // parseArrayType: missing ']' reports syntax error -> null
  @Test
  public void testParseTypeString_arrayMissingRb_returnsNull() throws Throwable {
    Node result = parseType("[string");
    assertNull(result);
  }

  // parseRecordType/parseFieldType: record with typed fields
  @Test
  public void testParseTypeString_recordType_buildsFieldList() throws Throwable {
    Node result = parseType("{a:string,b:number}");
    assertNotNull(result);
    assertEquals(Token.LC, result.getType());
    Node fields = result.getFirstChild();
    assertEquals(2, fields.getChildCount());
    Node f1 = fields.getFirstChild();
    assertEquals(Token.COLON, f1.getType());
    assertEquals("a", f1.getFirstChild().getString());
    assertEquals("string", f1.getLastChild().getString());
  }

  // parseFieldType: field without ':' yields plain field name node
  @Test
  public void testParseTypeString_recordFieldWithoutType_returnsFieldNameOnly() throws Throwable {
    Node result = parseType("{a}");
    Node fields = result.getFirstChild();
    Node field = fields.getFirstChild();
    assertEquals("a", field.getString());
  }

  // parseRecordType: empty record "{}" has no valid field -> null
  @Test
  public void testParseTypeString_emptyRecordType_returnsNull() throws Throwable {
    Node result = parseType("{}");
    assertNull(result);
  }

  // parseRecordType: missing closing '}' reports syntax error -> null
  @Test
  public void testParseTypeString_recordMissingRc_returnsNull() throws Throwable {
    Node result = parseType("{a:string");
    assertNull(result);
  }

  // parseFunctionType: no params, no explicit return type -> EMPTY result
  @Test
  public void testParseTypeString_functionTypeEmpty_returnsEmptyResult() throws Throwable {
    Node result = parseType("function()");
    assertNotNull(result);
    assertEquals(Token.FUNCTION, result.getType());
    assertEquals(1, result.getChildCount());
    assertEquals(Token.EMPTY, result.getFirstChild().getType());
  }

  // parseFunctionType/parseParametersType: params plus explicit return type
  @Test
  public void testParseTypeString_functionTypeWithParamsAndReturn() throws Throwable {
    Node result = parseType("function(string, number): boolean");
    assertNotNull(result);
    Node params = result.getFirstChild();
    assertEquals(Token.PARAM_LIST, params.getType());
    assertEquals(2, params.getChildCount());
    assertEquals("string", params.getFirstChild().getString());
    assertEquals("number", params.getLastChild().getString());
    assertEquals("boolean", result.getLastChild().getString());
  }

  // parseResultType: explicit "void" return type
  @Test
  public void testParseTypeString_functionTypeVoidReturn() throws Throwable {
    Node result = parseType("function(): void");
    assertNotNull(result);
    assertEquals(Token.VOID, result.getLastChild().getType());
  }

  // parseFunctionType: 'this:Type' context wraps in THIS as first child
  @Test
  public void testParseTypeString_functionTypeThisContext() throws Throwable {
    Node result = parseType("function(this:Object): void");
    assertNotNull(result);
    Node context = result.getFirstChild();
    assertEquals(Token.THIS, context.getType());
    assertEquals("Object", context.getFirstChild().getString());
  }

  // parseFunctionType: 'new:Type' context wraps in NEW as first child
  @Test
  public void testParseTypeString_functionTypeNewContext() throws Throwable {
    Node result = parseType("function(new:Foo): Foo");
    assertNotNull(result);
    Node context = result.getFirstChild();
    assertEquals(Token.NEW, context.getType());
    assertEquals("Foo", context.getFirstChild().getString());
    assertEquals("Foo", result.getLastChild().getString());
  }

  // parseFunctionType: 'this' without following ':' reports syntax error -> null
  @Test
  public void testParseTypeString_functionTypeMissingColonAfterThis_returnsNull() throws Throwable {
    Node result = parseType("function(this)");
    assertNull(result);
  }

  // parseFunctionType: missing '(' after 'function' reports syntax error -> null
  @Test
  public void testParseTypeString_functionTypeMissingLp_returnsNull() throws Throwable {
    Node result = parseType("function");
    assertNull(result);
  }

  // parseParametersType: untyped rest arg "..." directly before ')'
  @Test
  public void testParseTypeString_functionTypeVarArgsUntyped() throws Throwable {
    Node result = parseType("function(...)");
    Node params = result.getFirstChild();
    Node rest = params.getFirstChild();
    assertEquals(Token.ELLIPSIS, rest.getType());
    assertEquals(0, rest.getChildCount());
  }

  // parseParametersType: typed rest arg using old bracket syntax "...[Type]"
  @Test
  public void testParseTypeString_functionTypeVarArgsTypedBracket() throws Throwable {
    Node result = parseType("function(...[string])");
    Node params = result.getFirstChild();
    Node rest = params.getFirstChild();
    assertEquals(Token.ELLIPSIS, rest.getType());
    assertEquals("string", rest.getFirstChild().getString());
  }

  // parseParametersType: optional parameter marked with trailing '='
  @Test
  public void testParseTypeString_functionTypeOptionalParam() throws Throwable {
    Node result = parseType("function(string=)");
    Node params = result.getFirstChild();
    Node optional = params.getFirstChild();
    assertEquals(Token.EQUALS, optional.getType());
    assertEquals("string", optional.getFirstChild().getString());
  }

  // parseBasicTypeExpression: no recognizable token at all -> null
  @Test
  public void testParseTypeString_emptyString_returnsNull() throws Throwable {
    Node result = parseType("");
    assertNull(result);
  }

  // parse(): @deprecated is recorded and parsing reaches EOC successfully
  @Test
  public void testParse_deprecatedAnnotation_recordsInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@deprecated\n*/");
    assertTrue(parser.parse());
    assertTrue(parser.hasParsedJSDocInfo());
  }

  // parse(): @const is recorded
  @Test
  public void testParse_constAnnotation_recordsInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@const\n*/");
    assertTrue(parser.parse());
    assertTrue(parser.hasParsedJSDocInfo());
  }

  // parse(): @private visibility annotation is recorded
  @Test
  public void testParse_privateVisibility_recordsInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@private\n*/");
    assertTrue(parser.parse());
    assertTrue(parser.hasParsedJSDocInfo());
  }

  // parse(): @type with a type expression is recorded
  @Test
  public void testParse_typeAnnotation_recordsInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@type {string}\n*/");
    assertTrue(parser.parse());
    assertTrue(parser.hasParsedJSDocInfo());
  }

  // parse(): @param with type and name is recorded
  @Test
  public void testParse_paramAnnotation_recordsInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@param {string} foo\n*/");
    assertTrue(parser.parse());
    assertTrue(parser.hasParsedJSDocInfo());
  }

  // parse(): unknown annotation name only warns, nothing gets recorded
  @Test
  public void testParse_unknownAnnotation_notPopulated() throws Throwable {
    JsDocInfoParser parser = newParser("@notarealtag\n*/");
    assertTrue(parser.parse());
    assertFalse(parser.hasParsedJSDocInfo());
  }

  // parse(): unterminated comment hits EOF, discards info and returns false
  @Test
  public void testParse_unterminatedComment_returnsFalse() throws Throwable {
    JsDocInfoParser parser = newParser("@deprecated\n");
    assertFalse(parser.parse());
    assertFalse(parser.hasParsedJSDocInfo());
  }

  // parse(): @fileoverview populates the file overview JSDocInfo on EOC
  @Test
  public void testParse_fileOverviewAnnotation_populatesFileOverviewInfo() throws Throwable {
    JsDocInfoParser parser = newParser("@fileoverview Overview text\n*/");
    assertTrue(parser.parse());
    JSDocInfo overview = parser.getFileOverviewJSDocInfo();
    assertNotNull(overview);
  }
}
