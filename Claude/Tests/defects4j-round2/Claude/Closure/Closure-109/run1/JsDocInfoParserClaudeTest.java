package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;
import org.junit.Test;
import com.google.javascript.rhino.Node;

public class JsDocInfoParserClaudeTest {

  // BasicTypeExpression: plain type name string resolves to a non-null name node
  @Test
  public void testParseTypeString_simpleTypeName_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("number");
    assertNotNull(result);
  }

  // BasicTypeExpression: literal "null" keyword resolves to a non-null node
  @Test
  public void testParseTypeString_nullKeyword_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("null");
    assertNotNull(result);
  }

  // BasicTypeExpression: literal "undefined" keyword resolves to a non-null node
  @Test
  public void testParseTypeString_undefinedKeyword_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("undefined");
    assertNotNull(result);
  }

  // BasicTypeExpression: '*' (ALL type) resolves to a non-null node
  @Test
  public void testParseTypeString_starAnyType_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("*");
    assertNotNull(result);
  }

  // TypeExpression: bare '?' followed by EOC lookahead returns the unknown-type node
  @Test
  public void testParseTypeString_bareQuestionMark_lookaheadEOC_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("?");
    assertNotNull(result);
  }

  // TypeExpression: '?' prefix (nullable) wraps a following basic type expression
  @Test
  public void testParseTypeString_nullablePrefixQuestionMark_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("?string");
    assertNotNull(result);
  }

  // TypeExpression: '!' prefix (non-null) wraps a following basic type expression
  @Test
  public void testParseTypeString_nonNullPrefixBang_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("!Object");
    assertNotNull(result);
  }

  // TypeExpression: trailing '!' suffix wraps the basic type expression
  @Test
  public void testParseTypeString_trailingBangSuffix_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("Object!");
    assertNotNull(result);
  }

  // TypeExpression: trailing '?' suffix wraps the basic type expression
  @Test
  public void testParseTypeString_trailingQuestionMarkSuffix_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("Object?");
    assertNotNull(result);
  }

  // BasicTypeExpression: an unexpected stray token (PIPE) with no leading type is a syntax error
  @Test
  public void testParseTypeString_invalidStrayPipe_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("|");
    assertNull(result);
  }

  // ArrayType: '[' TypeExpression ']' parses successfully
  @Test
  public void testParseTypeString_arrayType_valid_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("[string]");
    assertNotNull(result);
  }

  // ArrayType: '...' rest element inside array parses successfully
  @Test
  public void testParseTypeString_arrayTypeVarArgs_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("[...string]");
    assertNotNull(result);
  }

  // ArrayType: missing closing ']' is a syntax error
  @Test
  public void testParseTypeString_arrayTypeMissingClosingBracket_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("[string");
    assertNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by ']' (RB) yields unknown type
  @Test
  public void testParseTypeString_arrayTypeQuestionMarkElement_lookaheadRB_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("[?]");
    assertNotNull(result);
  }

  // RecordType: multiple typed fields parse successfully
  @Test
  public void testParseTypeString_recordType_valid_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{a: number, b: string}");
    assertNotNull(result);
  }

  // FieldType: a field without a colon type is still a valid field name
  @Test
  public void testParseTypeString_recordTypeFieldWithoutType_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{a}");
    assertNotNull(result);
  }

  // RecordType: missing closing '}' is a syntax error
  @Test
  public void testParseTypeString_recordTypeMissingClosingBrace_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{a: number");
    assertNull(result);
  }

  // FieldTypeList: an empty record type has no fields and is a syntax error
  @Test
  public void testParseTypeString_recordTypeEmptyFields_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{}");
    assertNull(result);
  }

  // RecordType: nested record type as a field's type parses successfully (recursive descent)
  @Test
  public void testParseTypeString_recordTypeNested_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{a: {b: number}}");
    assertNotNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by '}' (RC) yields unknown type
  @Test
  public void testParseTypeString_recordTypeFieldQuestionMark_lookaheadRC_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("{a:?}");
    assertNotNull(result);
  }

  // UnionType: parenthesized pipe-separated union parses successfully
  @Test
  public void testParseTypeString_unionTypeParens_valid_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("(number|string)");
    assertNotNull(result);
  }

  // UnionType: missing closing ')' is a syntax error
  @Test
  public void testParseTypeString_unionTypeMissingClosingParen_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("(number|string");
    assertNull(result);
  }

  // UnionType: double pipe '||' supported for backwards compatibility
  @Test
  public void testParseTypeString_unionTypeDoublePipeBackwardCompat_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("(number||string)");
    assertNotNull(result);
  }

  // UnionType: comma supported as an alternate union separator for backwards compatibility
  @Test
  public void testParseTypeString_unionTypeCommaBackwardCompat_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("(number,string)");
    assertNotNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by ')' (RP) yields unknown type
  @Test
  public void testParseTypeString_unionTypeSingleElementQuestionMark_lookaheadRP_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("(?)");
    assertNotNull(result);
  }

  // TopLevelTypeExpression: unparenthesized top-level union is allowed
  @Test
  public void testParseTypeString_topLevelUnionWithoutParens_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("number|string");
    assertNotNull(result);
  }

  // TopLevelTypeExpression: unparenthesized top-level union with double pipe
  @Test
  public void testParseTypeString_topLevelUnionDoublePipe_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("number||string");
    assertNotNull(result);
  }

  // TypeName TypeApplication: generic application '.<Type>' parses successfully
  @Test
  public void testParseTypeString_genericTypeApplication_valid_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("Array.<string>");
    assertNotNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by '>' (GT) yields unknown type
  @Test
  public void testParseTypeString_genericTypeApplicationQuestionMark_lookaheadGT_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("Array.<?>");
    assertNotNull(result);
  }

  // TypeApplication: missing closing '>' is a syntax error
  @Test
  public void testParseTypeString_genericTypeApplicationMissingGT_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("Array.<string");
    assertNull(result);
  }

  // FunctionType: parameters plus an explicit non-void result type parse successfully
  @Test
  public void testParseTypeString_functionTypeBasic_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(string, number): boolean");
    assertNotNull(result);
  }

  // ResultType: absent ':' result type defaults to EMPTY and still parses successfully
  @Test
  public void testParseTypeString_functionTypeNoResultColon_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(string)");
    assertNotNull(result);
  }

  // FunctionType: missing '(' after "function" is a syntax error
  @Test
  public void testParseTypeString_functionTypeMissingLP_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function");
    assertNull(result);
  }

  // FunctionType: missing closing ')' is a syntax error
  @Test
  public void testParseTypeString_functionTypeMissingRP_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(string");
    assertNull(result);
  }

  // ParametersType: optional '=' suffix plus explicit ': void' result type parse successfully
  @Test
  public void testParseTypeString_functionTypeOptionalParamVoidResult_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(string=): void");
    assertNotNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by '=' (EQUALS) yields unknown type
  @Test
  public void testParseTypeString_functionTypeQuestionMarkParamEquals_lookaheadEquals_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(?=): void");
    assertNotNull(result);
  }

  // TypeExpression QMARK lookahead: '?' immediately followed by ',' (COMMA) yields unknown type
  @Test
  public void testParseTypeString_functionTypeQuestionMarkParamComma_lookaheadComma_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(?, number): void");
    assertNotNull(result);
  }

  // ParametersType: rest parameter with bracketed type '...[Type]' parses successfully
  @Test
  public void testParseTypeString_functionTypeVarArgsWithBrackets_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(...[number]): void");
    assertNotNull(result);
  }

  // ParametersType: bare '...' immediately followed by ')' parses successfully
  @Test
  public void testParseTypeString_functionTypeVarArgsBare_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(...): void");
    assertNotNull(result);
  }

  // ParametersType: '...' not followed by '[' or ')' is a syntax error (missing '[')
  @Test
  public void testParseTypeString_functionTypeVarArgsMissingBrackets_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(...number): void");
    assertNull(result);
  }

  // FunctionSignatureType: 'this:TypeName' context type parses successfully
  @Test
  public void testParseTypeString_functionTypeThisContext_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(this:Object): void");
    assertNotNull(result);
  }

  // FunctionSignatureType: 'new:TypeName' context type parses successfully
  @Test
  public void testParseTypeString_functionTypeNewContext_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(new:Foo): void");
    assertNotNull(result);
  }

  // FunctionSignatureType: 'this' not followed by ':' is a syntax error (missing colon)
  @Test
  public void testParseTypeString_functionTypeMissingColonAfterThis_returnsNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(this Object): void");
    assertNull(result);
  }

  // ContextTypeExpression := BasicTypeExpression | '?' (per Javadoc); 'this:?' must parse successfully
  @Test
  public void testParseTypeString_functionTypeThisContextQuestionMark_bugRegression_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(this:?): void");
    assertNotNull(result);
  }

  // ContextTypeExpression := BasicTypeExpression | '?' (per Javadoc); 'new:?' must parse successfully
  @Test
  public void testParseTypeString_functionTypeNewContextQuestionMark_bugRegression_returnsNonNull() throws Throwable {
    Node result = JsDocInfoParser.parseTypeString("function(new:?): void");
    assertNotNull(result);
  }
}
