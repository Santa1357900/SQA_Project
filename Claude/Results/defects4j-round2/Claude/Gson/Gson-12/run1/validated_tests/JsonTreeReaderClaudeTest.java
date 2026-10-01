package com.google.gson.internal.bind;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonToken;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonTreeReaderClaudeTest {

  // covers: o instanceof JsonPrimitive -> primitive.isString() branch
  @Test
  public void testPeek_stringPrimitive_returnsStringToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("abc"));
    assertEquals(JsonToken.STRING, reader.peek());
  }

  // covers: primitive.isBoolean() branch
  @Test
  public void testPeek_booleanPrimitive_returnsBooleanToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.TRUE));
    assertEquals(JsonToken.BOOLEAN, reader.peek());
  }

  // covers: primitive.isNumber() branch
  @Test
  public void testPeek_numberPrimitive_returnsNumberToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(42));
    assertEquals(JsonToken.NUMBER, reader.peek());
  }

  // covers: o instanceof JsonNull branch
  @Test
  public void testPeek_jsonNull_returnsNullToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(JsonNull.INSTANCE);
    assertEquals(JsonToken.NULL, reader.peek());
  }

  // covers: o instanceof JsonObject branch
  @Test
  public void testPeek_jsonObject_returnsBeginObjectToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonObject());
    assertEquals(JsonToken.BEGIN_OBJECT, reader.peek());
  }

  // covers: o instanceof JsonArray branch
  @Test
  public void testPeek_jsonArray_returnsBeginArrayToken() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonArray());
    assertEquals(JsonToken.BEGIN_ARRAY, reader.peek());
  }

  // covers: stackSize == 0 branch
  @Test
  public void testPeek_emptyStackAfterConsumption_returnsEndDocument() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    reader.nextInt();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: o == SENTINEL_CLOSED branch
  @Test
  public void testPeek_closedReader_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    reader.close();
    try {
      reader.peek();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: iterator.hasNext() true && !isObject -> push element and recurse
  @Test
  public void testPeek_arrayIteratorHasNext_pushesNextElement() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive("x"));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    assertEquals(JsonToken.STRING, reader.peek());
  }

  // covers: iterator.hasNext() true && isObject -> return NAME
  @Test
  public void testPeek_objectIteratorHasNext_returnsNameToken() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("foo", new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    assertEquals(JsonToken.NAME, reader.peek());
  }

  // covers: beginArray success path, sets pathIndices for new array scope
  @Test
  public void testBeginArray_onJsonArray_pushesIteratorAndResetsPathIndex() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    assertEquals("$[0]", reader.getPath());
  }

  // covers: expect(BEGIN_ARRAY) failure path
  @Test
  public void testBeginArray_onNonArray_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    try {
      reader.beginArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: endArray success path with empty array
  @Test
  public void testEndArray_onEmptyArray_succeedsAndReachesEndDocument() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonArray());
    reader.beginArray();
    reader.endArray();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: expect(END_ARRAY) failure when array not fully consumed
  @Test
  public void testEndArray_beforeConsumingElements_throwsIllegalStateException() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    try {
      reader.endArray();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: endArray's stackSize > 0 branch incrementing outer pathIndices
  @Test
  public void testEndArray_nestedArray_incrementsOuterPathIndex() throws Throwable {
    JsonArray inner = new JsonArray();
    JsonArray outer = new JsonArray();
    outer.add(inner);
    JsonTreeReader reader = new JsonTreeReader(outer);
    reader.beginArray();
    reader.beginArray();
    reader.endArray();
    assertEquals("$[1]", reader.getPath());
  }

  // covers: beginObject success path
  @Test
  public void testBeginObject_onJsonObject_pushesIterator() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("a", new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    assertEquals(JsonToken.NAME, reader.peek());
  }

  // covers: expect(BEGIN_OBJECT) failure path
  @Test
  public void testBeginObject_onNonObject_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    try {
      reader.beginObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: endObject success path on empty object
  @Test
  public void testEndObject_onEmptyObject_succeedsAndReachesEndDocument() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonObject());
    reader.beginObject();
    reader.endObject();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: expect(END_OBJECT) failure when members remain
  @Test
  public void testEndObject_beforeConsumingMembers_throwsIllegalStateException() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("a", new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    try {
      reader.endObject();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: hasNext() true and false branches for array
  @Test
  public void testHasNext_array_trueThenFalseAfterFullyConsumed() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    assertTrue(reader.hasNext());
    reader.nextInt();
    assertFalse(reader.hasNext());
  }

  // covers: hasNext() false for END_OBJECT
  @Test
  public void testHasNext_emptyObject_returnsFalse() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonObject());
    reader.beginObject();
    assertFalse(reader.hasNext());
  }

  // covers: nextName success path, key retrieval and path update
  @Test
  public void testNextName_returnsKeyAndUpdatesPath() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("foo", new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    String name = reader.nextName();
    assertEquals("foo", name);
    assertEquals("$.foo", reader.getPath());
  }

  // covers: expect(NAME) failure path
  @Test
  public void testNextName_whenCurrentTokenNotName_throwsIllegalStateException() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive(1));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    try {
      reader.nextName();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextString STRING token branch
  @Test
  public void testNextString_onStringPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("hi"));
    assertEquals("hi", reader.nextString());
  }

  // covers: nextString NUMBER token branch
  @Test
  public void testNextString_onNumberPrimitive_returnsStringRepresentation() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(123));
    assertEquals("123", reader.nextString());
  }

  // covers: nextString failure path for unsupported token
  @Test
  public void testNextString_onBooleanPrimitive_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.TRUE));
    try {
      reader.nextString();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextBoolean success path
  @Test
  public void testNextBoolean_onBooleanPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.FALSE));
    assertFalse(reader.nextBoolean());
  }

  // covers: expect(BOOLEAN) failure path
  @Test
  public void testNextBoolean_onNonBooleanPrimitive_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    try {
      reader.nextBoolean();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextNull success path
  @Test
  public void testNextNull_onJsonNull_succeedsAndReachesEndDocument() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(JsonNull.INSTANCE);
    reader.nextNull();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: expect(NULL) failure path
  @Test
  public void testNextNull_onNonNullValue_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    try {
      reader.nextNull();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextDouble NUMBER token branch
  @Test
  public void testNextDouble_onNumberPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(3.5));
    assertEquals(3.5, reader.nextDouble(), 1e-9);
  }

  // covers: nextDouble STRING token branch
  @Test
  public void testNextDouble_onStringNumericPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("3.14"));
    assertEquals(3.14, reader.nextDouble(), 1e-9);
  }

  // covers: !isLenient() && Double.isNaN(result) branch
  @Test
  public void testNextDouble_nonLenientNaN_throwsNumberFormatException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("NaN"));
    try {
      reader.nextDouble();
      fail("expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  // covers: isLenient() true bypasses NaN check
  @Test
  public void testNextDouble_lenientNaN_returnsNaN() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("NaN"));
    reader.setLenient(true);
    assertTrue(Double.isNaN(reader.nextDouble()));
  }

  // covers: expect NUMBER/STRING failure path for nextDouble
  @Test
  public void testNextDouble_onBooleanPrimitive_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.TRUE));
    try {
      reader.nextDouble();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextLong success path
  @Test
  public void testNextLong_onNumberPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Long.MAX_VALUE));
    assertEquals(Long.MAX_VALUE, reader.nextLong());
  }

  // covers: expect NUMBER/STRING failure path for nextLong
  @Test
  public void testNextLong_onBooleanPrimitive_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.TRUE));
    try {
      reader.nextLong();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: nextInt success path
  @Test
  public void testNextInt_onNumberPrimitive_returnsValue() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Integer.MIN_VALUE));
    assertEquals(Integer.MIN_VALUE, reader.nextInt());
  }

  // covers: expect NUMBER/STRING failure path for nextInt
  @Test
  public void testNextInt_onBooleanPrimitive_throwsIllegalStateException() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(Boolean.TRUE));
    try {
      reader.nextInt();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // covers: skipValue on top-level value where stackSize becomes 0 (bug-sensitive path)
  @Test
  public void testSkipValue_topLevelValue_doesNotThrowAndReachesEndDocument() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive("hello"));
    reader.skipValue();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: skipValue NAME branch and popStack branch across full object traversal
  @Test
  public void testSkipValue_loopOverObjectMembers_allowsEndObject() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("a", new JsonPrimitive(1));
    obj.add("b", new JsonPrimitive(2));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    while (reader.hasNext()) {
      reader.skipValue();
    }
    reader.endObject();
    assertEquals(JsonToken.END_DOCUMENT, reader.peek());
  }

  // covers: skipValue else-branch (non-NAME) popping array element and advancing index
  @Test
  public void testSkipValue_onArrayElement_skipsAndAdvancesIndex() throws Throwable {
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive(1));
    array.add(new JsonPrimitive(2));
    JsonTreeReader reader = new JsonTreeReader(array);
    reader.beginArray();
    reader.skipValue();
    assertEquals(2, reader.nextInt());
  }

  // covers: toString() implementation
  @Test
  public void testToString_returnsSimpleClassName() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    assertEquals("JsonTreeReader", reader.toString());
  }

  // covers: promoteNameToValue success path
  @Test
  public void testPromoteNameToValue_thenNextString_returnsKeyAsString() throws Throwable {
    JsonObject obj = new JsonObject();
    obj.add("key", new JsonPrimitive("value"));
    JsonTreeReader reader = new JsonTreeReader(obj);
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals("key", reader.nextString());
  }

  // covers: getPath() with no array/object on stack
  @Test
  public void testGetPath_initialState_returnsDollarSign() throws Throwable {
    JsonTreeReader reader = new JsonTreeReader(new JsonPrimitive(1));
    assertEquals("$", reader.getPath());
  }
}
