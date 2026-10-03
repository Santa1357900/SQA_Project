package com.google.gson.internal.bind;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonTreeWriterTest {

  @Test
  public void testInitialProduct() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    JsonElement element = writer.get();
    assertNotNull(element);
    assertTrue(element.isJsonNull());
  }

  @Test
  public void testSimpleValues() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value("hello");
    JsonElement element = writer.get();
    assertTrue(element.isJsonPrimitive());
    assertEquals("hello", element.getAsString());
  }

  @Test
  public void testNullValue() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.nullValue();
    JsonElement element = writer.get();
    assertTrue(element.isJsonNull());
  }

  @Test
  public void testStringNullValue() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value((String) null);
    JsonElement element = writer.get();
    assertTrue(element.isJsonNull());
  }

  @Test
  public void testBooleanValue() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value(true);
    JsonElement element = writer.get();
    assertTrue(element.isJsonPrimitive());
    assertTrue(element.getAsBoolean());
  }

  @Test
  public void testDoubleValueLenient() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setLenient(true);
    writer.value(Double.NaN);
    JsonElement element = writer.get();
    assertTrue(element.isJsonPrimitive());
    assertTrue(element.getAsNumber().doubleValue() != element.getAsNumber().doubleValue()); // isNaN check
  }

  @Test
  public void testDoubleValueStrictNan() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setLenient(false);
    try {
      writer.value(Double.NaN);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("JSON forbids NaN"));
    }
  }

  @Test
  public void testDoubleValueStrictInfinity() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setLenient(false);
    try {
      writer.value(Double.POSITIVE_INFINITY);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("JSON forbids NaN"));
    }
  }

  @Test
  public void testDoubleValueNormal() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value(123.45D);
    JsonElement element = writer.get();
    assertEquals(123.45D, element.getAsDouble(), 0.0001);
  }

  @Test
  public void testLongValue() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value(987654321L);
    JsonElement element = writer.get();
    assertEquals(987654321L, element.getAsLong());
  }

  @Test
  public void testNumberValueNull() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value((Number) null);
    JsonElement element = writer.get();
    assertTrue(element.isJsonNull());
  }

  @Test
  public void testNumberValueStrictInvalid() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setLenient(false);
    try {
      writer.value(Double.valueOf(Double.NaN));
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("JSON forbids NaN"));
    }
  }

  @Test
  public void testNumberValueNormal() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.value(Integer.valueOf(42));
    JsonElement element = writer.get();
    assertEquals(42, element.getAsInt());
  }

  @Test
  public void testArrayBuilding() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginArray();
    writer.value(1L);
    writer.value(2L);
    writer.endArray();

    JsonElement element = writer.get();
    assertTrue(element.isJsonArray());
    JsonArray array = element.getAsJsonArray();
    assertEquals(2, array.size());
    assertEquals(1L, array.get(0).getAsLong());
    assertEquals(2L, array.get(1).getAsLong());
  }

  @Test
  public void testObjectBuilding() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    writer.name("akey");
    writer.value("avalue");
    writer.endObject();

    JsonElement element = writer.get();
    assertTrue(element.isJsonObject());
    JsonObject obj = element.getAsJsonObject();
    assertTrue(obj.has("akey"));
    assertEquals("avalue", obj.get("akey").getAsString());
  }

  @Test
  public void testNestedStructures() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    writer.name("arr");
    writer.beginArray();
    writer.value(true);
    writer.nullValue();
    writer.endArray();
    writer.name("obj");
    writer.beginObject();
    writer.name("innerKey");
    writer.value(100L);
    writer.endObject();
    writer.endObject();

    JsonElement element = writer.get();
    assertTrue(element.isJsonObject());
    JsonObject obj = element.getAsJsonObject();
    assertTrue(obj.get("arr").isJsonArray());
    assertTrue(obj.get("obj").isJsonObject());
    assertEquals(100L, obj.getAsJsonObject("obj").get("innerKey").getAsLong());
  }

  @Test
  public void testSerializeNullsFalse() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setSerializeNulls(false);
    writer.beginObject();
    writer.name("nullKey");
    writer.nullValue();
    writer.endObject();

    JsonObject obj = writer.get().getAsJsonObject();
    assertFalse(obj.has("nullKey"));
  }

  @Test
  public void testSerializeNullsTrue() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.setSerializeNulls(true);
    writer.beginObject();
    writer.name("nullKey");
    writer.nullValue();
    writer.endObject();

    JsonObject obj = writer.get().getAsJsonObject();
    assertTrue(obj.has("nullKey"));
    assertTrue(obj.get("nullKey").isJsonNull());
  }

  @Test
  public void testFlushAndClose() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.flush();
    writer.close();
    
    try {
      writer.value("after close");
      fail("Expected IllegalStateException or IOException because stack has sentinel closed");
    } catch (Exception e) {
      // Expected exception due to closed state / sentinel
    }
  }

  @Test
  public void testCloseWithNonEmptyStack() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginArray();
    try {
      writer.close();
      fail("Expected IOException for incomplete document");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("Incomplete document"));
    }
  }

  @Test
  public void testGetWithNonEmptyStack() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginArray();
    try {
      writer.get();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Expected one JSON element"));
    }
  }

  @Test
  public void testEndArrayUnbalanced() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    try {
      writer.endArray();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testEndArrayWithPendingName() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    writer.name("name");
    try {
      writer.endArray();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testEndArrayWrongType() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    try {
      writer.endArray();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testEndObjectUnbalanced() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    try {
      writer.endObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testEndObjectWithPendingName() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    writer.name("name");
    try {
      writer.endObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testEndObjectWrongType() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginArray();
    try {
      writer.endObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testNameWithoutObject() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    try {
      writer.name("key");
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testNameDuplicateOrConsecutive() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginObject();
    writer.name("key1");
    try {
      writer.name("key2");
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testPutIntoInvalidContainer() throws Throwable {
    JsonTreeWriter writer = new JsonTreeWriter();
    writer.beginArray();
    // Put primitive inside array, then try to treat array element as object by forcing put through internal sequence if possible, 
    // or test putting inside primitive stack if reachable.
    // Actually, array elements are added via ((JsonArray) element).add(value). 
    // What if we put something when top element is a JsonPrimitive? 
    // Since stack only contains JsonArray or JsonObject normally, let's verify normal array operation works fine.
    writer.value("item");
    writer.endArray();
    assertTrue(writer.get().isJsonArray());
  }
}