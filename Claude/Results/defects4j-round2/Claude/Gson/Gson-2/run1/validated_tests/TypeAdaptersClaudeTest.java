package com.google.gson.internal.bind;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;

public class TypeAdaptersClaudeTest {

  private enum SampleEnum {
    @SerializedName("r") RED,
    GREEN
  }



  // CLASS.write: value != null -> throws UnsupportedOperationException
  @Test
  public void testClassWrite_nonNullClass_throwsUnsupportedOperationException() throws Throwable {
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    try {
      TypeAdapters.CLASS.write(writer, String.class);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }
  }





  // BIT_SET.read: NUMBER branch, non-zero sets bit
  @Test
  public void testBitSetRead_numberTokens_setsBitsForNonZero() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,0,2]"));
    BitSet result = TypeAdapters.BIT_SET.read(reader);
    assertTrue(result.get(0));
    assertFalse(result.get(1));
    assertTrue(result.get(2));
  }

  // BIT_SET.read: STRING branch invalid number -> JsonSyntaxException
  @Test
  public void testBitSetRead_invalidStringValue_throwsJsonSyntaxException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[\"abc\"]"));
    try {
      TypeAdapters.BIT_SET.read(reader);
      fail("expected JsonSyntaxException");
    } catch (JsonSyntaxException expected) {
    }
  }

  // BIT_SET.read: default branch on unsupported token type -> JsonSyntaxException
  @Test
  public void testBitSetRead_unsupportedTokenType_throwsJsonSyntaxException() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[null]"));
    try {
      TypeAdapters.BIT_SET.read(reader);
      fail("expected JsonSyntaxException");
    } catch (JsonSyntaxException expected) {
    }
  }



  // BIT_SET.write: writes 1/0 up to src.length()
  @Test
  public void testBitSetWrite_bitsSet_writesOnesAndZerosUpToLength() throws Throwable {
    BitSet bitSet = new BitSet();
    bitSet.set(0);
    bitSet.set(2);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    TypeAdapters.BIT_SET.write(writer, bitSet);
    writer.close();
    assertEquals("[1,0,1]", sw.toString());
  }





























































  // CALENDAR.read: parses all named fields into calendar
  @Test
  public void testCalendarRead_validObject_setsAllFields() throws Throwable {
    String json = "{\"year\":2020,\"month\":3,\"dayOfMonth\":15,\"hourOfDay\":8,\"minute\":5,\"second\":59}";
    JsonReader reader = new JsonReader(new StringReader(json));
    Calendar result = TypeAdapters.CALENDAR.read(reader);
    assertEquals(2020, result.get(Calendar.YEAR));
    assertEquals(3, result.get(Calendar.MONTH));
    assertEquals(15, result.get(Calendar.DAY_OF_MONTH));
    assertEquals(8, result.get(Calendar.HOUR_OF_DAY));
    assertEquals(5, result.get(Calendar.MINUTE));
    assertEquals(59, result.get(Calendar.SECOND));
  }



  // CALENDAR.write: writes fields in fixed order matching read() field names
  @Test
  public void testCalendarWrite_validCalendar_writesFieldsInOrder() throws Throwable {
    GregorianCalendar cal = new GregorianCalendar(2020, 3, 15, 8, 5, 59);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    TypeAdapters.CALENDAR.write(writer, cal);
    writer.close();
    String expected = "{\"year\":2020,\"month\":3,\"dayOfMonth\":15,\"hourOfDay\":8,\"minute\":5,\"second\":59}";
    assertEquals(expected, sw.toString());
  }







  // JSON_ELEMENT.read: BEGIN_ARRAY branch recursively builds JsonArray
  @Test
  public void testJsonElementRead_arrayToken_returnsJsonArrayWithElements() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("[1,\"two\",true]"));
    JsonElement result = TypeAdapters.JSON_ELEMENT.read(reader);
    JsonArray array = result.getAsJsonArray();
    assertEquals(3, array.size());
    assertEquals(1, array.get(0).getAsInt());
    assertEquals("two", array.get(1).getAsString());
    assertTrue(array.get(2).getAsBoolean());
  }

  // JSON_ELEMENT.read: BEGIN_OBJECT branch recursively builds JsonObject
  @Test
  public void testJsonElementRead_objectToken_returnsJsonObjectWithMembers() throws Throwable {
    JsonReader reader = new JsonReader(new StringReader("{\"a\":1,\"b\":\"x\"}"));
    JsonElement result = TypeAdapters.JSON_ELEMENT.read(reader);
    JsonObject obj = result.getAsJsonObject();
    assertEquals(1, obj.get("a").getAsInt());
    assertEquals("x", obj.get("b").getAsString());
  }







  // ENUM_FACTORY.create: non-enum raw type -> returns null
  @Test
  public void testEnumFactoryCreate_nonEnumType_returnsNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<String> adapter = TypeAdapters.ENUM_FACTORY.create(gson, new TypeToken<String>() {});
    assertNull(adapter);
  }









  // newFactory(TypeToken, TypeAdapter): matching type token returns same adapter instance
  @Test
  public void testNewFactoryTypeToken_matchingToken_returnsSameAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeToken<String> token = new TypeToken<String>() {};
    TypeAdapter<String> dummy = new TypeAdapter<String>() {
      public void write(JsonWriter out, String value) throws IOException {
        out.value(value);
      }
      public String read(JsonReader in) throws IOException {
        return in.nextString();
      }
    };
    TypeAdapterFactory factory = TypeAdapters.newFactory(token, dummy);
    TypeAdapter<String> result = factory.create(gson, new TypeToken<String>() {});
    assertSame(dummy, result);
  }

  // STRING_FACTORY (built via newFactory(Class,...)): matching raw type returns registered adapter
  @Test
  public void testStringFactoryCreate_matchingType_returnsStringAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<String> result = TypeAdapters.STRING_FACTORY.create(gson, new TypeToken<String>() {});
    assertSame(TypeAdapters.STRING, result);
  }

  // CLASS_FACTORY: non-matching raw type returns null
  @Test
  public void testClassFactoryCreate_nonMatchingType_returnsNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<Integer> result = TypeAdapters.CLASS_FACTORY.create(gson, new TypeToken<Integer>() {});
    assertNull(result);
  }

  // BOOLEAN_FACTORY (built via newFactory(unboxed,boxed,...)): boxed type returns registered adapter
  @Test
  public void testBooleanFactoryCreate_boxedType_returnsBooleanAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<Boolean> result = TypeAdapters.BOOLEAN_FACTORY.create(gson, new TypeToken<Boolean>() {});
    assertSame(TypeAdapters.BOOLEAN, result);
  }

  // CALENDAR_FACTORY (built via newFactoryForMultipleTypes): subtype GregorianCalendar matches
  @Test
  public void testCalendarFactoryCreate_subType_returnsAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<GregorianCalendar> result =
        TypeAdapters.CALENDAR_FACTORY.create(gson, new TypeToken<GregorianCalendar>() {});
    assertNotNull(result);
  }

  // CALENDAR_FACTORY: unrelated type returns null
  @Test
  public void testCalendarFactoryCreate_unrelatedType_returnsNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<Date> result = TypeAdapters.CALENDAR_FACTORY.create(gson, new TypeToken<Date>() {});
    assertNull(result);
  }

  // JSON_ELEMENT_FACTORY (built via newTypeHierarchyFactory): subtype JsonObject matches hierarchy
  @Test
  public void testJsonElementFactoryCreate_subType_returnsAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<JsonObject> result =
        TypeAdapters.JSON_ELEMENT_FACTORY.create(gson, new TypeToken<JsonObject>() {});
    assertNotNull(result);
  }

  // JSON_ELEMENT_FACTORY: unrelated type returns null
  @Test
  public void testJsonElementFactoryCreate_unrelatedType_returnsNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<String> result = TypeAdapters.JSON_ELEMENT_FACTORY.create(gson, new TypeToken<String>() {});
    assertNull(result);
  }
}
