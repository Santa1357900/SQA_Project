package com.google.gson.stream;

import java.io.StringReader;
import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonReaderClaudeTest {

  // Constructor: null Reader must throw NullPointerException as documented by the guard clause.
  @Test
  public void testConstructor_nullReader_throwsNpe() throws Throwable {
    try {
      new JsonReader(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // isLenient(): default value must be false (strict parsing by default).
  @Test
  public void testIsLenient_defaultFalse() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    assertFalse(reader.isLenient());
  }

  // setLenient(true) must be reflected by isLenient().
  @Test
  public void testSetLenient_trueReflectedInIsLenient() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.setLenient(true);
    assertTrue(reader.isLenient());
  }

  // beginArray() on an empty array; hasNext() must be false right after; peek() before consuming.
  @Test
  public void testBeginArray_emptyArray_hasNextFalse() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    assertEquals(JsonToken.BEGIN_ARRAY, reader.peek());
    reader.beginArray();
    assertFalse(reader.hasNext());
    reader.endArray();
  }

  // beginArray() called on an object token must throw IllegalStateException.
  @Test
  public void testBeginArray_notArray_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    try {
      reader.beginArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endArray() before array is actually finished must throw IllegalStateException.
  @Test
  public void testEndArray_notAtEnd_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    try {
      reader.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // beginObject()/endObject() for an empty object.
  @Test
  public void testBeginObject_emptyObject() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    reader.beginObject();
    assertFalse(reader.hasNext());
    reader.endObject();
  }

  // beginObject() called on an array token must throw IllegalStateException.
  @Test
  public void testBeginObject_notObject_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    try {
      reader.beginObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endObject() while a name/value still pending must throw IllegalStateException.
  @Test
  public void testEndObject_notAtEnd_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    reader.beginObject();
    try {
      reader.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // hasNext() true while elements remain, false at array end.
  @Test
  public void testHasNext_arrayWithElements() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,2]"));
    reader.beginArray();
    assertTrue(reader.hasNext());
    reader.nextInt();
    assertTrue(reader.hasNext());
    reader.nextInt();
    assertFalse(reader.hasNext());
    reader.endArray();
  }

  // hasNext() true while object members remain, false at object end.
  @Test
  public void testHasNext_objectWithMembers() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    reader.beginObject();
    assertTrue(reader.hasNext());
    reader.nextName();
    reader.nextInt();
    assertFalse(reader.hasNext());
    reader.endObject();
  }

  // peek() reports BEGIN_OBJECT then NAME for an object with a member.
  @Test
  public void testPeek_beginObjectAndName() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    assertEquals(JsonToken.BEGIN_OBJECT, reader.peek());
    reader.beginObject();
    assertEquals(JsonToken.NAME, reader.peek());
    assertEquals("a", reader.nextName());
  }

  // peek() reports STRING for a quoted literal.
  @Test
  public void testPeek_stringToken() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"hello\"]"));
    reader.beginArray();
    assertEquals(JsonToken.STRING, reader.peek());
    assertEquals("hello", reader.nextString());
    reader.endArray();
  }

  // peek() reports NUMBER for a numeric literal.
  @Test
  public void testPeek_numberToken() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[42]"));
    reader.beginArray();
    assertEquals(JsonToken.NUMBER, reader.peek());
    assertEquals(42, reader.nextInt());
    reader.endArray();
  }

  // peek() reports BOOLEAN for both true and false.
  @Test
  public void testPeek_booleanTrueFalse() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true,false]"));
    reader.beginArray();
    assertEquals(JsonToken.BOOLEAN, reader.peek());
    assertTrue(reader.nextBoolean());
    assertEquals(JsonToken.BOOLEAN, reader.peek());
    assertFalse(reader.nextBoolean());
    reader.endArray();
  }

  // peek() reports NULL for a null literal.
  @Test
  public void testPeek_nullToken() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[null]"));
    reader.beginArray();
    assertEquals(JsonToken.NULL, reader.peek());
    reader.nextNull();
    reader.endArray();
  }

  // peek() reports END_DOCUMENT once the single top-level value has been consumed (lenient top-level scalar).
  @Test
  public void testPeek_endDocument() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("true"));
    reader.setLenient(true);
    assertTrue(reader.nextBoolean());
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // nextName() returns a double-quoted property name and consumes it.
  @Test
  public void testNextName_quotedName() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"key\":1}"));
    reader.beginObject();
    assertEquals("key", reader.nextName());
  }

  // nextName() on a non-name token must throw IllegalStateException.
  @Test
  public void testNextName_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    try {
      reader.nextName();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nextString() from a quoted string literal.
  @Test
  public void testNextString_fromQuotedString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"abc\"]"));
    reader.beginArray();
    assertEquals("abc", reader.nextString());
    reader.endArray();
  }

  // nextString() from a number literal returns its raw string form.
  @Test
  public void testNextString_fromNumberLiteral() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1.50]"));
    reader.beginArray();
    assertEquals("1.50", reader.nextString());
    reader.endArray();
  }

  // nextString() on a boolean token must throw IllegalStateException.
  @Test
  public void testNextString_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true]"));
    reader.beginArray();
    try {
      reader.nextString();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nextBoolean() correctly reads both true and false literals.
  @Test
  public void testNextBoolean_trueAndFalse() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true,false]"));
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertFalse(reader.nextBoolean());
    reader.endArray();
  }

  // nextBoolean() on a numeric token must throw IllegalStateException.
  @Test
  public void testNextBoolean_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nextNull() consumes a null literal without error.
  @Test
  public void testNextNull_consumesNull() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[null]"));
    reader.beginArray();
    reader.nextNull();
    assertFalse(reader.hasNext());
    reader.endArray();
  }

  // nextNull() on a non-null token must throw IllegalStateException.
  @Test
  public void testNextNull_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    try {
      reader.nextNull();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nextDouble() parses a plain numeric literal.
  @Test
  public void testNextDouble_fromLiteral() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[3.14]"));
    reader.beginArray();
    assertEquals(3.14, reader.nextDouble(), 1e-9);
    reader.endArray();
  }

  // nextDouble() falls back to Double.parseDouble on a quoted string.
  @Test
  public void testNextDouble_fromQuotedString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"3.14\"]"));
    reader.beginArray();
    assertEquals(3.14, reader.nextDouble(), 1e-9);
    reader.endArray();
  }

  // nextDouble() on NaN in strict mode must throw MalformedJsonException.
  @Test
  public void testNextDouble_strictNaN_throwsMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"NaN\"]"));
    reader.beginArray();
    try {
      reader.nextDouble();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // nextDouble() on NaN in lenient mode must return NaN, not throw.
  @Test
  public void testNextDouble_lenientNaN_returnsNaN() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"NaN\"]"));
    reader.setLenient(true);
    reader.beginArray();
    double result = reader.nextDouble();
    assertTrue(Double.isNaN(result));
  }

  // nextLong() parses a long-fitting integer literal directly.
  @Test
  public void testNextLong_fromLiteral() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[123456789012]"));
    reader.beginArray();
    assertEquals(123456789012L, reader.nextLong());
    reader.endArray();
  }

  // nextLong() boundary: Long.MIN_VALUE literal must be parsed exactly.
  @Test
  public void testNextLong_minValueBoundary() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[-9223372036854775808]"));
    reader.beginArray();
    assertEquals(Long.MIN_VALUE, reader.nextLong());
    reader.endArray();
  }

  // nextLong() from a quoted numeric string.
  @Test
  public void testNextLong_fromQuotedString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"123\"]"));
    reader.beginArray();
    assertEquals(123L, reader.nextLong());
    reader.endArray();
  }

  // nextLong() on a non-numeric quoted string must throw NumberFormatException.
  @Test
  public void testNextLong_nonNumericString_throwsNumberFormatException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"abc\"]"));
    reader.beginArray();
    try {
      reader.nextLong();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  // nextInt() parses a plain integer literal.
  @Test
  public void testNextInt_fromLiteral() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[42]"));
    reader.beginArray();
    assertEquals(42, reader.nextInt());
    reader.endArray();
  }

  // nextInt() must throw NumberFormatException when the value overflows int.
  @Test
  public void testNextInt_overflow_throwsNumberFormatException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[99999999999]"));
    reader.beginArray();
    try {
      reader.nextInt();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  // skipValue() recursively skips a nested array, leaving subsequent tokens intact.
  @Test
  public void testSkipValue_skipsNestedArray() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[[1,2],3]"));
    reader.beginArray();
    reader.skipValue();
    assertEquals(3, reader.nextInt());
    reader.endArray();
  }

  // skipValue() recursively skips a nested object, leaving subsequent tokens intact.
  @Test
  public void testSkipValue_skipsNestedObject() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[{\"a\":1},2]"));
    reader.beginArray();
    reader.skipValue();
    assertEquals(2, reader.nextInt());
    reader.endArray();
  }

  // close() causes subsequent reads to throw IllegalStateException ("closed").
  @Test
  public void testClose_thenBeginArray_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.close();
    try {
      reader.beginArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // getPath() tracks object property name and array index as elements are consumed.
  @Test
  public void testGetPath_nestedArrayAndObject() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":[1,2]}"));
    reader.beginObject();
    reader.nextName();
    assertEquals("$.a", reader.getPath());
    reader.beginArray();
    reader.nextInt();
    assertEquals("$.a[1]", reader.getPath());
  }

  // Lenient mode: unquoted literal strings are accepted.
  @Test
  public void testLenient_unquotedLiteralString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[abc]"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals("abc", reader.nextString());
    reader.endArray();
  }

  // Lenient mode: single-quoted strings are accepted.
  @Test
  public void testLenient_singleQuotedString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("['abc']"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals("abc", reader.nextString());
    reader.endArray();
  }

  // Lenient mode: C-style comments between elements are skipped.
  @Test
  public void testLenient_commentsSkipped() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1 /* c */, 2]"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    assertEquals(2, reader.nextInt());
    reader.endArray();
  }

  // Lenient mode: consecutive commas in an array are treated as an omitted null.
  @Test
  public void testLenient_trailingCommaTreatedAsNull() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,,2]"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    assertEquals(JsonToken.NULL, reader.peek());
    reader.nextNull();
    assertEquals(2, reader.nextInt());
  }

  // Strict mode: a C-style comment must throw MalformedJsonException.
  @Test
  public void testStrict_commentsThrowMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1 /* c */]"));
    reader.beginArray();
    reader.nextInt();
    try {
      reader.endArray();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // Lenient mode: the non-execute security prefix is consumed transparently.
  @Test
  public void testNonExecutePrefix_lenientAccepted() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader(")]}'\n[1]"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    reader.endArray();
  }

  // Strict mode: the non-execute security prefix is not recognized and must throw.
  @Test
  public void testNonExecutePrefix_strictThrows() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader(")]}'\n[1]"));
    try {
      reader.beginArray();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // Large integer literal exceeding long range is read as a NUMBER token via nextDouble().
  @Test
  public void testNextDouble_largeLiteralExceedingLong() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[99999999999999999999]"));
    reader.beginArray();
    double result = reader.nextDouble();
    assertEquals(1.0E20, result, 1.0E13);
    reader.endArray();
  }

  // toString() reports the current line and column position.
  @Test
  public void testToString_reportsLineAndColumn() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("abc"));
    String s = reader.toString();
    assertTrue(s.contains("line 1"));
    assertTrue(s.contains("column 1"));
  }
}
