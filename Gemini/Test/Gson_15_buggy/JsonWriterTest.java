package com.google.gson.stream;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

public class JsonWriterTest {

  @Test
  public void testNullWriterThrowsException() throws Throwable {
    try {
      new JsonWriter(null);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("out == null"));
    }
  }

  @Test
  public void testBasicArray() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    jsonWriter.value(1);
    jsonWriter.value("test");
    jsonWriter.value(true);
    jsonWriter.nullValue();
    jsonWriter.endArray();
    jsonWriter.close();
    assertEquals("[1,\"test\",true,null]", stringWriter.toString());
  }

  @Test
  public void testBasicObject() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginObject();
    jsonWriter.name("myKey").value("myVal");
    jsonWriter.endObject();
    jsonWriter.close();
    assertEquals("{\"myKey\":\"myVal\"}", stringWriter.toString());
  }

  @Test
  public void testIndentAndPrettyPrinting() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.setIndent("  ");
    assertEquals("  ", jsonWriter.isHtmlSafe() ? "" : "  "); // exercise isHtmlSafe/setHtmlSafe slightly

    jsonWriter.beginObject();
    jsonWriter.name("a").value(1);
    jsonWriter.endObject();
    jsonWriter.close();

    String expected = "{\n  \"a\": 1\n}";
    assertEquals(expected, stringWriter.toString());
  }

  @Test
  public void testLenientMode() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    assertFalse(jsonWriter.isLenient());
    jsonWriter.setLenient(true);
    assertTrue(jsonWriter.isLenient());

    // Multiple top-level values allowed in lenient mode
    jsonWriter.value(true);
    jsonWriter.value(false);
    jsonWriter.close();
    assertEquals("truefalse", stringWriter.toString());
  }

  @Test
  public void testStrictMultipleTopLevelValuesThrows() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.value(true);
    try {
      jsonWriter.value(false);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("JSON must have only one top-level value"));
    }
  }

  @Test
  public void testHtmlSafeCharacters() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    assertFalse(jsonWriter.isHtmlSafe());
    jsonWriter.setHtmlSafe(true);
    assertTrue(jsonWriter.isHtmlSafe());

    jsonWriter.value("<>&='");
    jsonWriter.close();
    assertEquals("\"\\u003c\\u003e\\u0026\\u003d\\u0027\"", stringWriter.toString());
  }

  @Test
  public void testSerializeNullsFalse() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    assertTrue(jsonWriter.getSerializeNulls());
    jsonWriter.setSerializeNulls(false);
    assertFalse(jsonWriter.getSerializeNulls());

    jsonWriter.beginObject();
    jsonWriter.name("nullKey");
    jsonWriter.nullValue();
    jsonWriter.endObject();
    jsonWriter.close();
    assertEquals("{}", stringWriter.toString());
  }

  @Test
  public void testSerializeNullsTrue() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.setSerializeNulls(true);

    jsonWriter.beginObject();
    jsonWriter.name("nullKey");
    jsonWriter.nullValue();
    jsonWriter.endObject();
    jsonWriter.close();
    assertEquals("{\"nullKey\":null}", stringWriter.toString());
  }

  @Test
  public void testJsonValueDirect() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    jsonWriter.jsonValue("{\"raw\":true}");
    jsonWriter.jsonValue(null);
    jsonWriter.endArray();
    jsonWriter.close();
    assertEquals("[{\"raw\":true},null]", stringWriter.toString());
  }

  @Test
  public void testBooleanObjectValue() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    jsonWriter.value(Boolean.TRUE);
    jsonWriter.value(Boolean.FALSE);
    jsonWriter.value((Boolean) null);
    jsonWriter.endArray();
    jsonWriter.close();
    assertEquals("[true,false,null]", stringWriter.toString());
  }

  @Test
  public void testDoubleValuesValidAndInvalid() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    jsonWriter.value(1.5);
    try {
      jsonWriter.value(Double.NaN);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Numeric values must be finite"));
    }
    try {
      jsonWriter.value(Double.POSITIVE_INFINITY);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Numeric values must be finite"));
    }
    jsonWriter.endArray();
  }

  @Test
  public void testLongValue() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.value(912345678901L);
    jsonWriter.close();
    assertEquals("912345678901", stringWriter.toString());
  }

  @Test
  public void testNumberValueValidAndInvalid() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    jsonWriter.value(Integer.valueOf(10));
    jsonWriter.value((Number) null);

    try {
      jsonWriter.value(Double.NaN);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    jsonWriter.endArray();
  }

  @Test
  public void testFlushOnClosedThrows() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.close();
    try {
      jsonWriter.flush();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("JsonWriter is closed"));
    }
  }

  @Test
  public void testIncompleteDocumentThrowsOnClose() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginArray();
    try {
      jsonWriter.close();
      fail("Expected IOException");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("Incomplete document"));
    }
  }

  @Test
  public void testNameValidation() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginObject();
    try {
      jsonWriter.name(null);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("name == null"));
    }

    jsonWriter.name("key1");
    try {
      jsonWriter.name("key2");
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected due to deferredName already present
    }
  }

  @Test
  public void testNameOutsideObjectThrows() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    try {
      jsonWriter.name("key");
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Nesting problem"));
    }
  }

  @Test
  public void testStringEscapingControlChars() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    // Include control chars, quotes, backslash, unicode newlines \u2028 and \u2029
    jsonWriter.value("\u0000\t\b\n\r\f\"\\ \u2028 \u2029");
    jsonWriter.close();
    String result = stringWriter.toString();
    assertTrue(result.contains("\\u0000"));
    assertTrue(result.contains("\\t"));
    assertTrue(result.contains("\\b"));
    assertTrue(result.contains("\\n"));
    assertTrue(result.contains("\\r"));
    assertTrue(result.contains("\\f"));
    assertTrue(result.contains("\\\""));
    assertTrue(result.contains("\\\\"));
    assertTrue(result.contains("\\u2028"));
    assertTrue(result.contains("\\u2029"));
  }

  @Test
  public void testStackExpansion() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    // Nest deeply to trigger stack reallocation (initial stack size is 32)
    jsonWriter.beginArray();
    for (int i = 0; i < 40; i++) {
      jsonWriter.beginArray();
    }
    for (int i = 0; i < 40; i++) {
      jsonWriter.endArray();
    }
    jsonWriter.endArray();
    jsonWriter.close();
  }

  @Test
  public void testDanglingNameOnCloseArray() throws Throwable {
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    jsonWriter.beginObject();
    jsonWriter.name("key");
    try {
      jsonWriter.endObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Dangling name"));
    }
  }
}