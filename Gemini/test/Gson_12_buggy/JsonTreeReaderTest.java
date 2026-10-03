package com.google.gson.internal.bind;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonToken;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonTreeTest {

  @Test
  public void testPrimitiveString() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive("hello");
    JsonTreeReader reader = new JsonTreeReader(primitive);

    assertEquals(JsonToken.STRING, reader.peek());
    assertTrue(reader.hasNext());
    assertEquals("hello", reader.nextString());
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    assertEquals("$.'", reader.getPath());
  }

  @Test
  public void testPrimitiveBoolean() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive(Boolean.TRUE);
    JsonTreeReader reader = new JsonTreeReader(primitive);

    assertEquals(JsonToken.BOOLEAN, reader.peek());
    assertTrue(reader.nextBoolean());
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testPrimitiveNumber() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive(123);
    JsonTreeReader reader = new JsonTreeReader(primitive);

    assertEquals(JsonToken.NUMBER, reader.peek());
    assertEquals(123, reader.nextInt());

    JsonPrimitive primitive2 = new JsonPrimitive(456L);
    JsonTreeReader reader2 = new JsonTreeReader(primitive2);
    assertEquals(456L, reader2.nextLong());

    JsonPrimitive primitive3 = new JsonPrimitive(78.9);
    JsonTreeReader reader3 = new JsonTreeReader(primitive3);
    assertEquals(78.9, reader3.nextDouble(), 0.0001);
  }

  @Test
  public void testNull() throws Throwable {
    JsonNull jsonNull = JsonNull.INSTANCE;
    JsonTreeReader reader = new JsonTreeReader(jsonNull);

    assertEquals(JsonToken.NULL, reader.peek());
    reader.nextNull();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testArray() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive("a"));
    array.add(new JsonPrimitive(10));
    array.add(JsonNull.INSTANCE);

    JsonTreeReader reader = new JsonTreeReader(array);
    assertEquals(JsonToken.BEGIN_ARRAY, reader.peek());
    reader.beginArray();

    assertEquals(JsonToken.STRING, reader.peek());
    assertEquals("$[0]", reader.getPath());
    assertEquals("a", reader.nextString());

    assertEquals(JsonToken.NUMBER, reader.peek());
    assertEquals("$[1]", reader.getPath());
    assertEquals(10, reader.nextInt());

    assertEquals(JsonToken.NULL, reader.peek());
    assertEquals("$[2]", reader.getPath());
    reader.nextNull();

    reader.endArray();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testObject() throws Throwable {
    JsonObject object = new JsonObject();
    object.addProperty("key1", "val1");
    object.addProperty("key2", 42);

    JsonTreeReader reader = new JsonTreeReader(object);
    assertEquals(JsonToken.BEGIN_OBJECT, reader.peek());
    reader.beginObject();

    assertTrue(reader.hasNext());
    assertEquals(JsonToken.NAME, reader.peek());
    assertEquals("key1", reader.nextName());
    assertEquals("$.key1", reader.getPath());
    assertEquals("val1", reader.nextString());

    assertTrue(reader.hasNext());
    assertEquals(JsonToken.NAME, reader.peek());
    assertEquals("key2", reader.nextName());
    assertEquals("$.key2", reader.getPath());
    assertEquals(42, reader.nextInt());

    assertFalse(reader.hasNext());
    reader.endObject();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  @Test
  public void testSkipValue() throws Throwable {
    JsonObject object = new JsonObject();
    object.addProperty("skipMe", "secret");
    object.addProperty("keepMe", "visible");

    JsonTreeReader reader = new JsonTreeReader(object);
    reader.beginObject();

    assertEquals("skipMe", reader.nextName());
    reader.skipValue();

    assertEquals("keepMe", reader.nextName());
    assertEquals("visible", reader.nextString());
    reader.endObject();
  }

  @Test
  public void testPromoteNameToValue() throws Throwable {
    JsonObject object = new JsonObject();
    object.addProperty("myKey", "myVal");

    JsonTreeReader reader = new JsonTreeReader(object);
    reader.beginObject();
    assertEquals(JsonToken.NAME, reader.peek());
    reader.promoteNameToValue();

    assertEquals(JsonToken.STRING, reader.peek());
    assertEquals("myKey", reader.nextString());

    assertEquals(JsonToken.STRING, reader.peek());
    assertEquals("myVal", reader.nextString());
    reader.endObject();
  }

  @Test
  public void testClose() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive("test");
    JsonTreeReader reader = new JsonTreeReader(primitive);
    reader.close();

    try {
      reader.peek();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("closed"));
    }
  }

  @Test
  public void testStackResize() throws Throwable {
    JsonArray current = new JsonArray();
    JsonArray root = current;
    for (int i = 0; i < 40; i++) {
      JsonArray next = new JsonArray();
      current.add(next);
      current = next;
    }
    JsonTreeReader reader = new JsonTreeReader(root);
    assertNotNull(reader.getPath());
    assertEquals(JsonToken.BEGIN_ARRAY, reader.peek());
  }

  @Test
  public void testLenientNanInfinity() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive(Double.NaN);
    JsonTreeReader reader = new JsonTreeReader(primitive);
    reader.setLenient(true);
    assertTrue(Double.isNaN(reader.nextDouble()));

    JsonPrimitive primitiveInf = new JsonPrimitive(Double.POSITIVE_INFINITY);
    JsonTreeReader readerInf = new JsonTreeReader(primitiveInf);
    readerInf.setLenient(true);
    assertTrue(Double.isInfinite(readerInf.nextDouble()));
  }

  @Test
  public void testStrictNanInfinityThrows() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive(Double.NaN);
    JsonTreeReader reader = new JsonTreeReader(primitive);
    reader.setLenient(false);
    try {
      reader.nextDouble();
      fail("Expected NumberFormatException");
    } catch (NumberFormatException e) {
      assertTrue(e.getMessage().contains("NaN"));
    }
  }

  @Test
  public void testToString() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("a"));
    assertEquals("JsonTreeReader", reader.toString());
  }

  @Test
  public void testUnexpectedTokenThrows() throws Throwable {
    JsonPrimitive primitive = new JsonPrimitive("notANumber");
    JsonTreeReader reader = new JsonTreeReader(primitive);
    try {
      reader.nextInt();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Expected NUMBER"));
    }
  }
}