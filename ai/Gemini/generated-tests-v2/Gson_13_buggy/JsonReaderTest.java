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
  public void testBasicObjectParsing() throws Throwable {
    String json = "{\"a\": true, \"b\": 123, \"c\": 45.67, \"d\": \"hello\", \"e\": null}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertTrue(reader.nextBoolean());
    assertEquals("b", reader.nextName());
    assertEquals(123, reader.nextInt());
    assertEquals("c", reader.nextName());
    assertEquals(45.67, reader.nextDouble(), 0.0001);
    assertEquals("d", reader.nextName());
    assertEquals("hello", reader.nextString());
    assertEquals("e", reader.nextName());
    reader.nextNull();
    reader.endObject();
    assertFalse(reader.hasNext());
    reader.close();
  }

  @Test
  public void testBasicArrayParsing() throws Throwable {
    String json = "[1, -2, 3.14, \"test\", true, null]";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginArray();
    assertTrue(reader.hasNext());
    assertEquals(1, reader.nextInt());
    assertEquals(-2, reader.nextLong());
    assertEquals(3.14, reader.nextDouble(), 0.0001);
    assertEquals("test", reader.nextString());
    assertTrue(reader.nextBoolean());
    reader.nextNull();
    assertFalse(reader.hasNext());
    reader.endArray();
    reader.close();
  }

  @Test
  public void testNestedStructures() throws Throwable {
    String json = "{\"outer\": [{\"inner\": 1}]}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("outer", reader.nextName());
    reader.beginArray();
    reader.beginObject();
    assertEquals("inner", reader.nextName());
    assertEquals(1, reader.nextInt());
    reader.endObject();
    reader.endArray();
    reader.endObject();
    reader.close();
  }

  @Test
  public void testSkipValue() throws Throwable {
    String json = "{\"skipMe\": {\"a\": 1}, \"keepMe\": 2}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("skipMe", reader.nextName());
    reader.skipValue();
    assertEquals("keepMe", reader.nextName());
    assertEquals(2, reader.nextInt());
    reader.endObject();
    reader.close();
  }

  @Test
  public void testSkipValueArray() throws Throwable {
    String json = "{\"skipMe\": [1, 2, {\"a\": 3}], \"keepMe\": 2}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("skipMe", reader.nextName());
    reader.skipValue();
    assertEquals("keepMe", reader.nextName());
    assertEquals(2, reader.nextInt());
    reader.endObject();
    reader.close();
  }

  @Test
  public void testSkipValuePrimitives() throws Throwable {
    String json = "{\"a\": 123, \"b\": \"string\", \"c\": true, \"d\": null}";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    reader.skipValue();
    assertEquals("c", reader.nextName());
    reader.skipValue();
    assertEquals("d", reader.nextName());
    reader.skipValue();
    reader.endObject();
    reader.close();
  }

  @Test
  public void testCommentsAndLenientMode() throws Throwable {
    String json = "/* comment */ {\n" +
                  "  // line comment\n" +
                  "  # hash comment\n" +
                  "  unquoted: 'single',\n" +
                  "  semi;value\n" +
                  "}";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginObject();
    assertEquals("unquoted", reader.nextName());
    assertEquals("single", reader.nextString());
    assertEquals("semi", reader.nextName());
    assertEquals("value", reader.nextString());
    reader.endObject();
    reader.close();
  }

  @Test
  public void testNonExecutePrefix() throws Throwable {
    String json = ")]}'\n[1, 2]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginArray();
    assertEquals(1, reader.nextInt());
    assertEquals(2, reader.nextInt());
    reader.endArray();
    reader.close();
  }

  @Test
  public void testEscapeCharacters() throws Throwable {
    String json = "[\"\\t\\b\\n\\r\\f\\\\\\\"\\/\\u0041\"]";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginArray();
    assertEquals("\t\b\n\r\f\\\"/A", reader.nextString());
    reader.endArray();
    reader.close();
  }

  @Test
  public void testMalformedNumber() throws Throwable {
    String json = "[\"abc\"]";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginArray();
    try {
      reader.nextInt();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      // Expected
    }
    reader.endArray();
    reader.close();
  }

  @Test
  public void testDoubleParsingErrors() throws Throwable {
    String json = "[NaN]";
    JsonReader reader = new JsonReader(new StringReader(json));
    // Strict by default, NaN should fail
    reader.beginArray();
    try {
      reader.nextDouble();
      fail("Expected MalformedJsonException");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("JSON forbids NaN and infinities"));
    }
    reader.close();
  }

  @Test
  public void testDoubleParsingLenient() throws Throwable {
    String json = "[NaN, Infinity, -Infinity]";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginArray();
    assertTrue(Double.isNaN(reader.nextDouble()));
    assertTrue(Double.isInfinite(reader.nextDouble()));
    assertTrue(Double.isInfinite(reader.nextDouble()));
    reader.endArray();
    reader.close();
  }

  @Test
  public void testLongOverflowToInt() throws Throwable {
    String json = "[2147483650]";
    JsonReader reader = new JsonReader(new StringReader(json));

    reader.beginArray();
    try {
      reader.nextInt();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      assertTrue(e.getMessage().contains("Expected an int"));
    }
    reader.close();
  }

  @Test
  public void testInvalidSyntaxThrowsException() throws Throwable {
    String json = "{invalid}";
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);

    reader.beginObject();
    try {
      reader.nextName();
      fail("Expected IOException");
    } catch (IOException e) {
      // Expected
    }
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
      assertTrue(e.getMessage().contains("JsonReader is closed"));
    }
  }

  @Test
  public void testToString() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{}"));
    assertNotNull(reader.toString());
    assertTrue(reader.toString().contains("JsonReader"));
    reader.close();
  }
}