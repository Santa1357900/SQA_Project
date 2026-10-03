package com.google.gson.internal.bind;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;

public class JsonTreeWriterClaudeTest {

  private JsonTreeWriter writer;

  @Before
  public void setUp() throws Throwable {
    writer = new JsonTreeWriter();
  }

  // Constructor default: product is JsonNull.INSTANCE, stack empty -> get() returns it
  @Test
  public void testConstructor_defaultProduct_isJsonNull() throws Throwable {
    assertEquals(JsonNull.INSTANCE, writer.get());
  }

  // get() throws when stack is non-empty (open array not closed)
  @Test
  public void testGet_openArray_throwsIllegalStateException() throws Throwable {
    writer.beginArray();
    try {
      writer.get();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // beginArray: puts an array at top level and pushes onto stack
  @Test
  public void testBeginArray_returnsThis_andProductIsJsonArray() throws Throwable {
    JsonWriterHolder h = new JsonWriterHolder(writer.beginArray());
    writer.endArray();
    assertSame(writer, h.w);
    assertTrue(writer.get().isJsonArray());
  }

  // beginArray nested inside another array: added to parent array
  @Test
  public void testBeginArray_nested_addsToParentArray() throws Throwable {
    writer.beginArray();
    writer.beginArray();
    writer.endArray();
    writer.endArray();
    JsonArray outer = writer.get().getAsJsonArray();
    assertEquals(1, outer.size());
    assertTrue(outer.get(0).isJsonArray());
  }

  // endArray on empty stack throws IllegalStateException
  @Test
  public void testEndArray_emptyStack_throwsIllegalStateException() throws Throwable {
    try {
      writer.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endArray when pendingName is set throws IllegalStateException
  @Test
  public void testEndArray_pendingName_throwsIllegalStateException() throws Throwable {
    writer.beginObject();
    writer.name("a");
    try {
      writer.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endArray when top of stack is not a JsonArray throws IllegalStateException
  @Test
  public void testEndArray_topNotArray_throwsIllegalStateException() throws Throwable {
    writer.beginObject();
    try {
      writer.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // beginObject: puts an object at top level and pushes onto stack
  @Test
  public void testBeginObject_returnsThis_andProductIsJsonObject() throws Throwable {
    writer.beginObject();
    writer.endObject();
    assertTrue(writer.get().isJsonObject());
  }

  // endObject on empty stack throws IllegalStateException
  @Test
  public void testEndObject_emptyStack_throwsIllegalStateException() throws Throwable {
    try {
      writer.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endObject when pendingName is set throws IllegalStateException
  @Test
  public void testEndObject_pendingName_throwsIllegalStateException() throws Throwable {
    writer.beginObject();
    writer.name("a");
    try {
      writer.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // endObject when top of stack is not a JsonObject throws IllegalStateException
  @Test
  public void testEndObject_topNotObject_throwsIllegalStateException() throws Throwable {
    writer.beginArray();
    try {
      writer.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // name() on empty stack throws IllegalStateException
  @Test
  public void testName_emptyStack_throwsIllegalStateException() throws Throwable {
    try {
      writer.name("a");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // name() when pendingName already set (double call) throws IllegalStateException
  @Test
  public void testName_pendingNameAlreadySet_throwsIllegalStateException() throws Throwable {
    writer.beginObject();
    writer.name("a");
    try {
      writer.name("b");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // name() when top of stack is not a JsonObject throws IllegalStateException
  @Test
  public void testName_topNotObject_throwsIllegalStateException() throws Throwable {
    writer.beginArray();
    try {
      writer.name("a");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }



  // value(String) with null delegates to nullValue(), producing JsonNull
  @Test
  public void testValueString_null_setsJsonNull() throws Throwable {
    writer.beginArray();
    writer.value((String) null);
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertTrue(arr.get(0).isJsonNull());
  }

  // value(String) inside an array adds a JsonPrimitive to that array
  @Test
  public void testValueString_inArray_addsPrimitive() throws Throwable {
    writer.beginArray();
    writer.value("x");
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertEquals(1, arr.size());
    assertEquals("x", arr.get(0).getAsString());
  }

  // value(String) with a pendingName adds it to the enclosing object under that key
  @Test
  public void testValueString_withPendingName_addsToObject() throws Throwable {
    writer.beginObject();
    writer.name("key");
    writer.value("val");
    writer.endObject();
    JsonObject obj = writer.get().getAsJsonObject();
    assertEquals("val", obj.get("key").getAsString());
  }



  // nullValue() with serializeNulls=true adds the JsonNull value to the object
  @Test
  public void testNullValue_serializeNullsTrue_addsNullToObject() throws Throwable {
    writer.setSerializeNulls(true);
    writer.beginObject();
    writer.name("key");
    writer.nullValue();
    writer.endObject();
    JsonObject obj = writer.get().getAsJsonObject();
    assertTrue(obj.has("key"));
    assertTrue(obj.get("key").isJsonNull());
  }

  // nullValue() inside an array always adds JsonNull (no pendingName restriction)
  @Test
  public void testNullValue_inArray_addsJsonNull() throws Throwable {
    writer.beginArray();
    writer.nullValue();
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertEquals(1, arr.size());
    assertTrue(arr.get(0).isJsonNull());
  }

  // value(boolean) sets a boolean JsonPrimitive
  @Test
  public void testValueBoolean_setsPrimitive() throws Throwable {
    writer.beginArray();
    writer.value(true);
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertTrue(arr.get(0).getAsBoolean());
  }

  // value(double) with a finite value sets a numeric JsonPrimitive
  @Test
  public void testValueDouble_normal_setsPrimitive() throws Throwable {
    writer.beginArray();
    writer.value(3.14);
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertEquals(3.14, arr.get(0).getAsDouble(), 1e-9);
  }

  // value(double) NaN when not lenient throws IllegalArgumentException
  @Test
  public void testValueDouble_NaN_notLenient_throwsIllegalArgumentException() throws Throwable {
    try {
      writer.value(Double.NaN);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // value(double) Infinity when not lenient throws IllegalArgumentException
  @Test
  public void testValueDouble_Infinite_notLenient_throwsIllegalArgumentException() throws Throwable {
    try {
      writer.value(Double.POSITIVE_INFINITY);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // value(double) NaN when lenient=true is accepted and stored
  @Test
  public void testValueDouble_NaN_lenient_succeeds() throws Throwable {
    writer.setLenient(true);
    writer.value(Double.NaN);
    JsonElement el = writer.get();
    assertTrue(Double.isNaN(el.getAsDouble()));
  }

  // value(long) sets a long JsonPrimitive
  @Test
  public void testValueLong_setsPrimitive() throws Throwable {
    writer.beginArray();
    writer.value(42L);
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertEquals(42L, arr.get(0).getAsLong());
  }

  // value(Number) with null delegates to nullValue()
  @Test
  public void testValueNumber_null_setsJsonNull() throws Throwable {
    writer.beginArray();
    writer.value((Number) null);
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertTrue(arr.get(0).isJsonNull());
  }

  // value(Number) NaN when not lenient throws IllegalArgumentException
  @Test
  public void testValueNumber_NaN_notLenient_throwsIllegalArgumentException() throws Throwable {
    Double nan = Double.valueOf(Double.NaN);
    try {
      writer.value(nan);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // value(Number) with a normal finite number sets a numeric JsonPrimitive
  @Test
  public void testValueNumber_normal_setsPrimitive() throws Throwable {
    writer.beginArray();
    writer.value(Integer.valueOf(7));
    writer.endArray();
    JsonArray arr = writer.get().getAsJsonArray();
    assertEquals(7, arr.get(0).getAsInt());
  }

  // flush() does not throw and writer remains usable afterwards
  @Test
  public void testFlush_doesNotThrow() throws Throwable {
    writer.flush();
    writer.beginArray();
    writer.endArray();
    assertTrue(writer.get().isJsonArray());
  }

  // close() with incomplete document (open array) throws IOException
  @Test
  public void testClose_incompleteDocument_throwsIOException() throws Throwable {
    writer.beginArray();
    try {
      writer.close();
      fail("expected IOException");
    } catch (IOException expected) {
    }
  }

  // close() with complete document succeeds and subsequently get() throws (sentinel pushed)
  @Test
  public void testClose_completeDocument_succeeds() throws Throwable {
    writer.beginArray();
    writer.endArray();
    writer.close();
    try {
      writer.get();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // close() when stack already empty at top level succeeds, then further writes are rejected
  @Test
  public void testClose_thenBeginArray_throwsIllegalStateException() throws Throwable {
    writer.close();
    try {
      writer.beginArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  private static class JsonWriterHolder {
    final com.google.gson.stream.JsonWriter w;
    JsonWriterHolder(com.google.gson.stream.JsonWriter w) {
      this.w = w;
    }
  }
}
