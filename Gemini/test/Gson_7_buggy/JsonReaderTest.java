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
  public void testLenientSetting() throws Throwable {
    StringReader reader = new StringReader("{}");
    JsonReader jsonReader = new JsonReader(reader);
    assertFalse(jsonReader.isLenient());
    jsonReader.setLenient(true);
    assertTrue(jsonReader.isLenient());
    jsonReader.close();
  }

  @Test
  public void testReadArray() throws Throwable {
    String json = "[1, true, \"hello\", null]";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginArray();
    assertEquals(JsonToken.NUMBER, reader.peek());
    assertEquals(1, reader.nextInt());

    assertEquals(JsonToken.BOOLEAN, reader.peek());
    assertTrue(reader.nextBoolean());

    assertEquals(JsonToken.STRING, reader.peek());
    assertEquals("hello", reader.nextString());

    assertEquals(JsonToken.NULL, reader.peek());
    reader.nextNull();

    reader.endArray();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    reader.close();
  }

  @Test
  public void testReadObject() throws Throwable {
    String json = "{\"a\": 100, \"b\": \"test\"}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals(JsonToken.NAME, reader.peek());
    assertEquals("a", reader.nextName());
    assertEquals(100, reader.nextInt());

    assertEquals(JsonToken.NAME, reader.peek());
    assertEquals("b", reader.nextName());
    assertEquals("test", reader.nextString());

    reader.endObject();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    reader.close();
  }

  @Test
  public void testNextLongAndDouble() throws Throwable {
    String json = "[\"1234567890123\", 45.67, 100]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.beginArray();

    assertEquals(1234567890123L, reader.nextLong());
    assertEquals(45.67, reader.nextDouble(), 0.0001);
    assertEquals(100.0, reader.nextDouble(), 0.0001);

    reader.endArray();
    reader.close();
  }

  @Test
  public void testSkipValue() throws Throwable {
    String json = "{\"a\": [1, 2, { \"nested\": true }], \"b\": 2}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();

    assertEquals("b", reader.nextName());
    assertEquals(2, reader.nextInt());

    reader.endObject();
    reader.close();
  }

  @Test
  public void testCommentsAndLenient() throws Throwable {
    String json = "{// comment\n \"a\": 1 /* block comment */, # hash comment\n}";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(1, reader.nextInt());
    reader.endObject();
    reader.close();
  }

  @Test
  public void testClosedReader() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    reader.close();
    try {
      reader.beginObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("closed"));
    }
  }

  @Test
  public void testToStringAndPath() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"x\": [1]}"));
    assertTrue(reader.toString().contains("JsonReader"));
    assertEquals("$", reader.getPath());

    reader.beginObject();
    assertEquals("x", reader.nextName());
    assertEquals("$.x", reader.getPath());

    reader.beginArray();
    assertEquals(JsonToken.NUMBER, reader.peek());
    assertEquals("$.x[0]", reader.getPath());

    reader.endArray();
    reader.endObject();
    reader.close();
  }

  @Test
  public void testEscapeCharacters() throws Throwable {
    String json = "\"\\t\\b\\n\\r\\f\\\\\\\"\\'\\u0041\"";
    JsonReader reader = new JsonReader(new StringReader(json));
    String val = reader.nextString();
    assertEquals("\t\b\n\r\f\\\"'A", val);
    reader.close();
  }

  @Test
  public void testNonExecutePrefix() throws Throwable {
    String json = ")]}'\n{\"a\":1}";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(1, reader.nextInt());
    reader.endObject();
    reader.close();
  }

  @Test
  public void testInvalidNumberFormat() throws Throwable {
    String json = "[\"notAnInt\"]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.beginArray();
    try {
      reader.nextInt();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      // Expected
    }
    reader.close();
  }
}