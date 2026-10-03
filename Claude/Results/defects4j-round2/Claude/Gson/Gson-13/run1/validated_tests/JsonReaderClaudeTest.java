package com.google.gson.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonReaderClaudeTest {

  // constructor: null Reader must throw NPE per contract
  @Test
  public void testConstructor_nullReader_throwsNullPointerException() throws Throwable {
    try {
      new JsonReader(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // setLenient/isLenient getter-setter branch
  @Test
  public void testSetLenient_defaultFalseThenTrue() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    assertFalse(reader.isLenient());
    reader.setLenient(true);
    assertTrue(reader.isLenient());
  }

  // beginArray success path (PEEKED_BEGIN_ARRAY) then endArray on empty array
  @Test
  public void testBeginArray_emptyArray_success() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.beginArray();
    assertFalse(reader.hasNext());
    reader.endArray();
  }

  // beginArray wrong token branch -> IllegalStateException
  @Test
  public void testBeginArray_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    try {
      reader.beginArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endArray wrong token branch -> IllegalStateException
  @Test
  public void testEndArray_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    try {
      reader.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // beginObject wrong token branch -> IllegalStateException
  @Test
  public void testBeginObject_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    try {
      reader.beginObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endObject wrong token branch -> IllegalStateException
  @Test
  public void testEndObject_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    reader.beginObject();
    try {
      reader.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // hasNext true/false branches for array
  @Test
  public void testHasNext_emptyAndNonEmptyArray() throws Throwable {
    JsonReader empty = new JsonReader(new StringReader("[]"));
    empty.beginArray();
    assertFalse(empty.hasNext());
    JsonReader nonEmpty = new JsonReader(new StringReader("[1]"));
    nonEmpty.beginArray();
    assertTrue(nonEmpty.hasNext());
  }

  // peek() switch covering BOOLEAN, NULL, NUMBER, STRING token mapping
  @Test
  public void testPeek_sequenceOfTokenTypes() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true,false,null,42,\"hi\"]"));
    reader.beginArray();
    assertEquals(JsonToken.BOOLEAN, reader.peek());
    reader.nextBoolean();
    assertEquals(JsonToken.BOOLEAN, reader.peek());
    reader.nextBoolean();
    assertEquals(JsonToken.NULL, reader.peek());
    reader.nextNull();
    assertEquals(JsonToken.NUMBER, reader.peek());
    reader.nextInt();
    assertEquals(JsonToken.STRING, reader.peek());
  }

  // peek() END_DOCUMENT branch after top-level array fully consumed
  @Test
  public void testPeek_endDocumentAfterTopLevelArray() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.beginArray();
    reader.endArray();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // peek() on completely empty input triggers EOFException via nextNonWhitespace(true)
  @Test
  public void testPeek_emptyInput_throwsEOFException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader(""));
    try {
      reader.peek();
      fail("expected EOFException");
    } catch (EOFException expected) {
    }
  }

  // nextName quoted name branch
  @Test
  public void testNextName_quoted_success() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    reader.beginObject();
    assertEquals("a", reader.nextName());
  }

  // nextName unquoted name branch requires lenient
  @Test
  public void testNextName_lenientUnquoted_success() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{a:1}"));
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
  }

  // nextName wrong token branch -> IllegalStateException
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

  // nextString from number and quoted string branches
  @Test
  public void testNextString_fromNumberAndQuoted() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[123,\"abc\"]"));
    reader.beginArray();
    assertEquals("123", reader.nextString());
    assertEquals("abc", reader.nextString());
  }

  // nextString wrong token branch -> IllegalStateException
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

  // nextBoolean true/false branches
  @Test
  public void testNextBoolean_trueAndFalse() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true,false]"));
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertFalse(reader.nextBoolean());
  }

  // nextBoolean wrong token branch -> IllegalStateException
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

  // nextNull success branch, verifies value consumed via hasNext
  @Test
  public void testNextNull_success() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[null]"));
    reader.beginArray();
    reader.nextNull();
    assertFalse(reader.hasNext());
  }

  // nextNull wrong token branch -> IllegalStateException
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

  // nextDouble from long, number literal, and quoted string branches
  @Test
  public void testNextDouble_variousSources() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,2.5,\"3.5\"]"));
    reader.beginArray();
    assertEquals(1.0, reader.nextDouble(), 1e-9);
    assertEquals(2.5, reader.nextDouble(), 1e-9);
    assertEquals(3.5, reader.nextDouble(), 1e-9);
  }

  // nextDouble strict non-finite rejection branch -> MalformedJsonException
  @Test
  public void testNextDouble_nonFiniteStrict_throwsMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"NaN\"]"));
    reader.beginArray();
    try {
      reader.nextDouble();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // nextLong from long and string-parseLong success branches
  @Test
  public void testNextLong_variousSources() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[123,\"456\"]"));
    reader.beginArray();
    assertEquals(123L, reader.nextLong());
    assertEquals(456L, reader.nextLong());
  }

  // nextLong precision-loss fallback-to-double branch -> NumberFormatException
  @Test
  public void testNextLong_precisionLoss_throwsNumberFormat() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"1.5\"]"));
    reader.beginArray();
    try {
      reader.nextLong();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  // nextLong wrong token branch -> IllegalStateException
  @Test
  public void testNextLong_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true]"));
    reader.beginArray();
    try {
      reader.nextLong();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // nextInt from long, string, and boundary MAX/MIN branches
  @Test
  public void testNextInt_variousSourcesAndBoundaries() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[42,\"43\",2147483647,-2147483648]"));
    reader.beginArray();
    assertEquals(42, reader.nextInt());
    assertEquals(43, reader.nextInt());
    assertEquals(Integer.MAX_VALUE, reader.nextInt());
    assertEquals(Integer.MIN_VALUE, reader.nextInt());
  }

  // nextInt overflow precision-loss branch -> NumberFormatException
  @Test
  public void testNextInt_overflow_throwsNumberFormat() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[3000000000]"));
    reader.beginArray();
    try {
      reader.nextInt();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  // nextInt wrong token branch -> IllegalStateException
  @Test
  public void testNextInt_wrongToken_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true]"));
    reader.beginArray();
    try {
      reader.nextInt();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // close() sets CLOSED scope, subsequent peek() must throw IllegalStateException
  @Test
  public void testClose_thenPeek_throwsIllegalState() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.close();
    try {
      reader.peek();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // skipValue over array, nested object, and name - verifies state continues correctly
  @Test
  public void testSkipValue_nestedStructures() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":[1,2,{\"b\":true}],\"c\":5}"));
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("c", reader.nextName());
    assertEquals(5, reader.nextInt());
    reader.endObject();
  }

  // getPath reflects nested object/array indices correctly as elements are consumed
  @Test
  public void testGetPath_nestedObjectArray() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":[1,2]}"));
    reader.beginObject();
    reader.nextName();
    reader.beginArray();
    assertEquals("$.a[0]", reader.getPath());
    reader.nextInt();
    assertEquals("$.a[1]", reader.getPath());
  }

  // non-execute prefix consumed when lenient is enabled
  @Test
  public void testNonExecutePrefix_lenientAccepted() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader(")]}'\n[]"));
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
  }

  // non-execute prefix rejected in strict mode -> MalformedJsonException
  @Test
  public void testNonExecutePrefix_strictRejected_throwsMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader(")]}'\n[]"));
    try {
      reader.beginArray();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // leading zero multi-digit number is rejected as literal, strict mode throws
  @Test
  public void testLeadingZero_strict_throwsMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[01]"));
    reader.beginArray();
    try {
      reader.peek();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // C-style and end-of-line comments are skipped only when lenient
  @Test
  public void testComments_lenientCStyleAndEndOfLine() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1/*c*/,2//eol\n,3]"));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    assertEquals(2, reader.nextInt());
    assertEquals(3, reader.nextInt());
  }

  // unterminated array at EOF while expecting comma throws EOFException
  @Test
  public void testUnterminatedArray_throwsEOFException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,2"));
    reader.beginArray();
    reader.nextInt();
    reader.nextInt();
    try {
      reader.hasNext();
      fail("expected EOFException");
    } catch (EOFException expected) {
    }
  }

  // push() stack-growth branch triggered by deep nesting beyond initial capacity
  @Test
  public void testDeepNesting_stackGrowth() throws Throwable {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 40; i++) sb.append('[');
    for (int i = 0; i < 40; i++) sb.append(']');
    JsonReader reader = new JsonReader(new StringReader(sb.toString()));
    for (int i = 0; i < 40; i++) reader.beginArray();
    for (int i = 0; i < 40; i++) reader.endArray();
    assertEquals("$", reader.getPath());
  }

  // lenient unquoted and single-quoted string value branches
  @Test
  public void testUnquotedAndSingleQuotedStrings_lenient() throws Throwable {
    JsonReader unquoted = new JsonReader(new StringReader("[abc]"));
    unquoted.setLenient(true);
    unquoted.beginArray();
    assertEquals("abc", unquoted.nextString());
    JsonReader singleQuoted = new JsonReader(new StringReader("['abc']"));
    singleQuoted.setLenient(true);
    singleQuoted.beginArray();
    assertEquals("abc", singleQuoted.nextString());
  }



  // readEscapeCharacter standard two-character and unicode escape branches
  @Test
  public void testReadEscapeCharacter_standardAndUnicodeEscapes() throws Throwable {
    JsonReader newline = new JsonReader(new StringReader("[\"a\\nb\"]"));
    newline.beginArray();
    assertEquals("a\nb", newline.nextString());
    JsonReader unicode = new JsonReader(new StringReader("[\"\\u0041\"]"));
    unicode.beginArray();
    assertEquals("A", unicode.nextString());
  }

  // readEscapeCharacter EOF-before-completion branch -> MalformedJsonException
  @Test
  public void testReadEscapeCharacter_unterminated_throwsMalformedJson() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"abc\\"));
    reader.beginArray();
    try {
      reader.nextString();
      fail("expected MalformedJsonException");
    } catch (MalformedJsonException expected) {
    }
  }

  // readEscapeCharacter malformed unicode hex digits branch -> NumberFormatException
  @Test
  public void testReadEscapeCharacter_invalidUnicode_throwsNumberFormat() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"\\uZZZZ\"]"));
    reader.beginArray();
    try {
      reader.nextString();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }
}
