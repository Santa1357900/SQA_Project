package com.google.gson.stream;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.EOFException;

public class JsonReaderTest {

  @Test
  public void testNullReader() throws Throwable {
    try {
      new JsonReader(null);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("in == null"));
    }
  }

  @Test
  public void testStrictParsingRejectMultipleTopLevelValues() throws Throwable {
    String json = "true false";
    JsonReader reader = new JsonReader(new StringReader(json));
    assertTrue(reader.nextBoolean());
    try {
      reader.hasNext();
      fail("Expected IOException due to multiple top-level values in strict mode");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("Use JsonReader.setLenient(true)"));
    }
  }

  @Test
  public void testLenientParsingAcceptMultipleTopLevelValues() throws Throwable {
    String json = "true false";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);
    assertTrue(reader.nextBoolean());
    assertTrue(reader.nextBoolean());
  }

  @Test
  public void testEmptyArray() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[]"));
    reader.beginArray();
    assertFalse(reader.hasNext());
    reader.endArray();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testArrayWithElements() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[true, false, null, 123, \"abc\"]"));
    reader.beginArray();
    
    assertTrue(reader.hasNext());
    assertTrue(reader.nextBoolean());

    assertTrue(reader.hasNext());
    assertFalse(reader.nextBoolean());

    assertTrue(reader.hasNext());
    reader.nextNull();

    assertTrue(reader.hasNext());
    assertEquals(123, reader.nextInt());

    assertTrue(reader.hasNext());
    assertEquals("abc", reader.nextString());

    assertFalse(reader.hasNext());
    reader.endArray();
  }

  @Test
  public void testEmptyObject() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    reader.beginObject();
    assertFalse(reader.hasNext());
    reader.endObject();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testObjectWithMembers() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\": 1, 'b': true, c: \"hello\"}}"));
    reader.setLenient(true);
    reader.beginObject();

    assertTrue(reader.hasNext());
    assertEquals("a", reader.nextName());
    assertEquals(1, reader.nextInt());

    assertTrue(reader.hasNext());
    assertEquals("b", reader.nextName());
    assertTrue(reader.nextBoolean());

    assertTrue(reader.hasNext());
    assertEquals("c", reader.nextName());
    assertEquals("hello", reader.nextString());

    assertFalse(reader.hasNext());
    reader.endObject();
  }

  @Test
  public void testNumbersParsing() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[0, -0, 123, -456, 1.23, 1e2, 1E-2, -9223372036854775808, 9223372036854775807]]"));
    reader.beginArray();

    assertEquals(0, reader.nextInt());
    assertEquals(0L, reader.nextLong());
    assertEquals(123, reader.nextInt());
    assertEquals(-456L, reader.nextLong());
    assertEquals(1.23, reader.nextDouble(), 0.0001);
    assertEquals(100.0, reader.nextDouble(), 0.0001);
    assertEquals(0.01, reader.nextDouble(), 0.0001);
    assertEquals(Long.MIN_VALUE, reader.nextLong());
    assertEquals(Long.MAX_VALUE, reader.nextLong());

    reader.endArray();
  }

  @Test
  public void testNumberOverflowToIntOrLong() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[922337203685477580799]]"));
    reader.beginArray();
    try {
      reader.nextLong();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      assertTrue(e.getMessage().contains("Expected a long"));
    }
  }

  @Test
  public void testSkipValue() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\": [1, 2, {\"nested\": true}], \"b\": 456}"));
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    assertEquals(456, reader.nextInt());
    reader.endObject();
  }

  @Test
  public void testLenientComments() throws Throwable {
    String json = "[\n" +
        "  // line comment\n" +
        "  /* block comment */\n" +
        "  # hash comment\n" +
        "  1\n" +
        "]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    reader.endArray();
  }

  @Test
  public void testNonExecutePrefix() throws Throwable {
    String json = ")]}'\n[1, 2, 3]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(1, reader.nextInt());
    assertEquals(2, reader.nextInt());
    assertEquals(3, reader.nextInt());
    reader.endArray();
  }

  @Test
  public void testClose() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1]"));
    reader.beginArray();
    reader.close();
    try {
      reader.hasNext();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("closed"));
    }
  }

  @Test
  public void testStringEscapes() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"\\t\\b\\n\\r\\f\\\\\\\"\\u0041\"]")) ;
    reader.beginArray();
    assertEquals("\t\b\n\r\f\\\"A", reader.nextString());
    reader.endArray();
  }

  @Test
  public void testMalformedUnicodeEscape() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"\\uZZZZ\"]"));
    reader.setLenient(true);
    reader.beginArray();
    try {
      reader.nextString();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      assertTrue(e.getMessage().contains("\\uZZZZ"));
    }
    reader.endArray();
  }

  @Test
  public void testPathAndLineColumn() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\n  {\n    \"x\": 10\n  }\n]"));
    assertEquals("$.", reader.getPath());
    reader.beginArray();
    assertEquals("$[0]", reader.getPath());
    reader.beginObject();
    assertEquals("$[0].", reader.getPath());
    assertEquals("x", reader.nextName());
    assertEquals("$[0].x", reader.getPath());
    assertEquals(10, reader.nextInt());
    reader.endObject();
    reader.endArray();
    assertEquals("$", reader.getPath());
  }

  @Test
  public void testInvalidSyntaxThrowsException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[invalid_literal]"));
    reader.setLenient(true);
    reader.beginArray();
    try {
      reader.nextInt();
      fail("Expected IOException / NumberFormatException");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void testStrictModeRejectNaNAndInfinities() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[NaN]"));
    reader.setLenient(true);
    reader.beginArray();
    try {
      reader.setLenient(false);
      reader.nextDouble();
      fail("Expected MalformedJsonException");
    } catch (MalformedJsonException e) {
      assertTrue(e.getMessage().contains("JSON forbids NaN and infinities"));
    }
  }

  @Test
  public void testPromoteNameToValueViaInternalAccess() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"name\": \"value\"}"));
    reader.beginObject();
    assertEquals("name", reader.nextName());
    
    com.google.gson.internal.JsonReaderInternalAccess.INSTANCE.promoteNameToValue(reader);
    assertEquals("name", reader.nextString());
    assertEquals("value", reader.nextString());
    reader.endObject();
  }

  @Test
  public void testToStringOutput() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1}"));
    String str = reader.toString();
    assertTrue(str.contains("JsonReader"));
  }
}