package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

public class JsDocInfoParserClaudeTest {

  private Config config;

  @Before
  public void setUp() throws Throwable {
    config = new Config(
        Sets.<String>newHashSet(),
        Sets.<String>newHashSet(),
        false,
        Config.LanguageMode.ECMASCRIPT3,
        false);
  }

  private JsDocInfoParser newParser(String source) {
    return new JsDocInfoParser(
        new JsDocTokenStream(source), null, null, config,
        NullErrorReporter.forNewRhino());
  }

  // parseInlineTypeDoc: valid type text must produce a non-null JSDocInfo
  @Test
  public void testParseInlineTypeDoc_validTypeString_returnsNonNullJSDocInfo() throws Throwable {
    JsDocInfoParser parser = newParser("number");
    JSDocInfo info = parser.parseInlineTypeDoc();
    assertNotNull(info);
  }

  // parseInlineTypeDoc: empty stream cannot parse a type, must return null
  @Test
  public void testParseInlineTypeDoc_emptyTypeString_returnsNull() throws Throwable {
    JsDocInfoParser parser = newParser("");
    JSDocInfo info = parser.parseInlineTypeDoc();
    assertNull(info);
  }

  // parseTypeString: plain type name (BasicTypeExpression -> TypeName) succeeds
  @Test
  public void testParseTypeString_simpleTypeName_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("number");
    assertNotNull(node);
  }

  // parseTypeString: no tokens at all must fail to parse
  @Test
  public void testParseTypeString_emptyString_returnsNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("");
    assertNull(node);
  }

  // parseTypeString: whitespace-only input has no type token, must fail
  @Test
  public void testParseTypeString_whitespaceOnly_returnsNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString(" ");
    assertNull(node);
  }

  // parseTypeString: '*' is the ALL type, a direct BasicTypeExpression branch
  @Test
  public void testParseTypeString_starType_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("*");
    assertNotNull(node);
  }

  // parseTypeString: 'null' literal is special-cased in parseBasicTypeExpression
  @Test
  public void testParseTypeString_nullLiteral_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("null");
    assertNotNull(node);
  }

  // parseTypeString: 'undefined' literal is special-cased too
  @Test
  public void testParseTypeString_undefinedLiteral_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("undefined");
    assertNotNull(node);
  }

  // parseTypeString: '!' prefix (non-nullable) branch in parseTypeExpression
  @Test
  public void testParseTypeString_bangPrefix_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("!Object");
    assertNotNull(node);
  }

  // parseTypeString: '!' suffix (non-nullable) branch after a basic type
  @Test
  public void testParseTypeString_bangSuffix_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("Object!");
    assertNotNull(node);
  }

  // parseTypeString: '?' suffix (nullable) branch after a basic type
  @Test
  public void testParseTypeString_qmarkSuffix_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("Object?");
    assertNotNull(node);
  }

  // parseTypeString: TypeApplication '.<...>' with a single type argument
  @Test
  public void testParseTypeString_genericSingleParam_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("Array.<string>");
    assertNotNull(node);
  }

  // parseTypeString: TypeApplication with a comma-separated argument list
  @Test
  public void testParseTypeString_genericMultipleParams_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("Object.<string,number>");
    assertNotNull(node);
  }

  // parseTypeString: '?' followed by '>' is in the unknown-type lookahead set
  @Test
  public void testParseTypeString_genericUnknownParam_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("Array.<?>");
    assertNotNull(node);
  }

  // parseTypeString: UnionType via '(' ... ')' (parseBasicTypeExpression LP branch)
  @Test
  public void testParseTypeString_unionInParens_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("(string|number)");
    assertNotNull(node);
  }

  // parseTypeString: top-level union without surrounding parens
  @Test
  public void testParseTypeString_topLevelUnion_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("string|number");
    assertNotNull(node);
  }

  // parseTypeString: RecordType with one field, FieldType ':' TypeExpression
  @Test
  public void testParseTypeString_recordTypeSingleField_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{myField:number}");
    assertNotNull(node);
  }

  // parseTypeString: RecordType with comma-separated FieldTypeList
  @Test
  public void testParseTypeString_recordTypeMultipleFields_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{a:string,b:number}");
    assertNotNull(node);
  }

  // parseTypeString: legacy ArrayType '[' ElementTypeList ']'
  @Test
  public void testParseTypeString_arrayTypeTuple_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("[string,number]");
    assertNotNull(node);
  }

  // parseTypeString: ArrayType with '...' ElementType (varargs) branch
  @Test
  public void testParseTypeString_arrayTypeVarArgs_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("[...string]");
    assertNotNull(node);
  }

  // parseTypeString: FunctionType with empty params and empty result type
  @Test
  public void testParseTypeString_functionNoParamsNoReturn_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function()");
    assertNotNull(node);
  }

  // parseTypeString: FunctionType with params and a non-void result type
  @Test
  public void testParseTypeString_functionWithParamsAndReturn_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(string,number):boolean");
    assertNotNull(node);
  }

  // parseTypeString: FunctionType 'this:' context branch and ':void' result
  @Test
  public void testParseTypeString_functionThisContext_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(this:Object):void");
    assertNotNull(node);
  }

  // parseTypeString: FunctionType 'new:' context branch
  @Test
  public void testParseTypeString_functionNewContext_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(new:Object):void");
    assertNotNull(node);
  }

  // parseTypeString: FunctionType rest-parameter '...[Type]' branch
  @Test
  public void testParseTypeString_functionVarArgsParam_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(...[string]):void");
    assertNotNull(node);
  }

  // parseTypeString: '?' parameter followed by ',' is unknown-type lookahead
  @Test
  public void testParseTypeString_functionUnknownParam_returnsNonNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("function(?,number):void");
    assertNotNull(node);
  }

  // parseTypeString: unterminated RecordType must fail (no field, no '}')
  @Test
  public void testParseTypeString_malformedRecordType_returnsNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("{");
    assertNull(node);
  }

  // parseTypeString: ArrayType missing closing ']' must fail
  @Test
  public void testParseTypeString_malformedArrayTypeMissingBracket_returnsNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("[string");
    assertNull(node);
  }

  // parseTypeString: UnionType missing closing ')' must fail
  @Test
  public void testParseTypeString_malformedUnionTypeMissingParen_returnsNull() throws Throwable {
    Node node = JsDocInfoParser.parseTypeString("(string|number");
    assertNull(node);
  }

  // createJSTypeExpression: javadoc says node 'May be null' -> result is null
  @Test
  public void testCreateJSTypeExpression_nullNode_returnsNull() throws Throwable {
    JsDocInfoParser parser = newParser("number");
    assertNull(parser.createJSTypeExpression((Node) null));
  }

  // createJSTypeExpression: a real node must yield a non-null JSTypeExpression
  @Test
  public void testCreateJSTypeExpression_nonNullNode_returnsNonNull() throws Throwable {
    JsDocInfoParser parser = newParser("number");
    Node n = IR.name("foo");
    assertNotNull(parser.createJSTypeExpression(n));
  }

  // hasParsedJSDocInfo: a freshly built parser has not recorded anything yet
  @Test
  public void testHasParsedJSDocInfo_freshParser_returnsFalse() throws Throwable {
    JsDocInfoParser parser = newParser("number");
    assertFalse(parser.hasParsedJSDocInfo());
  }

  // getFileOverviewJSDocInfo: field is initialized to null until @fileoverview seen
  @Test
  public void testGetFileOverviewJSDocInfo_freshParser_returnsNull() throws Throwable {
    JsDocInfoParser parser = newParser("number");
    assertNull(parser.getFileOverviewJSDocInfo());
  }

  // setFileOverviewJSDocInfo/getFileOverviewJSDocInfo: setter stores same reference
  @Test
  public void testSetFileOverviewJSDocInfo_thenGet_returnsSameInstance() throws Throwable {
    JsDocInfoParser srcParser = newParser("number");
    JSDocInfo info = srcParser.parseInlineTypeDoc();
    assertNotNull(info);
    JsDocInfoParser targetParser = newParser("string");
    targetParser.setFileOverviewJSDocInfo(info);
    assertSame(info, targetParser.getFileOverviewJSDocInfo());
  }
}
