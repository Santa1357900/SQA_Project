package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.*;

public class TypeAdaptersTest {

  @Test
  public void testClassAdapter() throws Throwable {
    TypeAdapter<Class> adapter = TypeAdapters.CLASS;
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    adapter.write(jw, null);
    assertEquals("null", sw.toString());

    try {
      StringWriter sw2 = new StringWriter();
      JsonWriter jw2 = new JsonWriter(sw2);
      adapter.write(jw2, String.class);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("Attempted to serialize java.lang.Class"));
    }

    StringReader sr = new StringReader("null");
    JsonReader jr = new JsonReader(sr);
    assertNull(adapter.read(jr));

    try {
      StringReader sr2 = new StringReader("{}");
      JsonReader jr2 = new JsonReader(sr2);
      adapter.read(jr2);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("Attempted to deserialize a java.lang.Class"));
    }

    assertNotNull(TypeAdapters.CLASS_FACTORY.create(new Gson(), TypeToken.get(String.class)));
    assertNull(TypeAdapters.CLASS_FACTORY.create(new Gson(), TypeToken.get(Integer.class)));
  }

  @Test
  public void testBitSetAdapter() throws Throwable {
    TypeAdapter<BitSet> adapter = TypeAdapters.BIT_SET;
    
    // Write null
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    adapter.write(jw, null);
    assertEquals("null", sw.toString());

    // Write BitSet
    BitSet bs = new BitSet();
    bs.set(0);
    bs.set(2);
    StringWriter sw2 = new StringWriter();
    JsonWriter jw2 = new JsonWriter(sw2);
    adapter.write(jw2, bs);
    assertEquals("[1,0,1]", sw2.toString());

    // Read null
    StringReader sr = new StringReader("null");
    JsonReader jr = new JsonReader(sr);
    assertNull(adapter.read(jr));

    // Read valid array (numbers, booleans, strings)
    StringReader sr2 = new StringReader("[1, false, \"1\", 0]");
    JsonReader jr2 = new JsonReader(sr2);
    BitSet readBs = adapter.read(jr2);
    assertTrue(readBs.get(0));
    assertFalse(readBs.get(1));
    assertTrue(readBs.get(2));
    assertFalse(readBs.get(3));

    // Read invalid string number format
    try {
      StringReader sr3 = new StringReader("[\"invalid\"]");
      JsonReader jr3 = new JsonReader(sr3);
      adapter.read(jr3);
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("bitset number value"));
    }

    // Read invalid type
    try {
      StringReader sr4 = new StringReader("[{}]");
      JsonReader jr4 = new JsonReader(sr4);
      adapter.read(jr4);
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Invalid bitset value type"));
    }
  }

  @Test
  public void testBooleanAdapter() throws Throwable {
    TypeAdapter<Boolean> adapter = TypeAdapters.BOOLEAN;
    
    assertNull(adapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Boolean.TRUE, adapter.read(new JsonReader(new StringReader("true"))));
    assertEquals(Boolean.TRUE, adapter.read(new JsonReader(new StringReader("\"true\""))));
    assertEquals(Boolean.FALSE, adapter.read(new JsonReader(new StringReader("\"false\""))));

    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    adapter.write(jw, null);
    assertEquals("null", sw.toString());

    StringWriter sw2 = new StringWriter();
    JsonWriter jw2 = new JsonWriter(sw2);
    adapter.write(jw2, Boolean.TRUE);
    assertEquals("true", sw2.toString());
  }

  @Test
  public void testBooleanAsStringAdapter() throws Throwable {
    TypeAdapter<Boolean> adapter = TypeAdapters.BOOLEAN_AS_STRING;

    assertNull(adapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Boolean.TRUE, adapter.read(new JsonReader(new StringReader("\"true\""))));

    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    adapter.write(jw, null);
    assertEquals("\"null\"", sw.toString());

    StringWriter sw2 = new StringWriter();
    JsonWriter jw2 = new JsonWriter(sw2);
    adapter.write(jw2, Boolean.TRUE);
    assertEquals("\"true\"", sw2.toString());
  }

  @Test
  public void testNumericAdapters() throws Throwable {
    // Byte
    TypeAdapter<Number> byteAdapter = TypeAdapters.BYTE;
    assertNull(byteAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Byte.valueOf((byte) 5), byteAdapter.read(new JsonReader(new StringReader("5"))));
    try {
      byteAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }
    StringWriter swByte = new StringWriter();
    byteAdapter.write(new JsonWriter(swByte), (byte) 5);
    assertEquals("5", swByte.toString());

    // Short
    TypeAdapter<Number> shortAdapter = TypeAdapters.SHORT;
    assertNull(shortAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Short.valueOf((short) 10), shortAdapter.read(new JsonReader(new StringReader("10"))));
    try {
      shortAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }

    // Integer
    TypeAdapter<Number> intAdapter = TypeAdapters.INTEGER;
    assertNull(intAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Integer.valueOf(20), intAdapter.read(new JsonReader(new StringReader("20"))));
    try {
      intAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }

    // Long
    TypeAdapter<Number> longAdapter = TypeAdapters.LONG;
    assertNull(longAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Long.valueOf(30L), longAdapter.read(new JsonReader(new StringReader("30"))));
    try {
      longAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }

    // Float & Double
    TypeAdapter<Number> floatAdapter = TypeAdapters.FLOAT;
    assertNull(floatAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Float.valueOf(5.5f), floatAdapter.read(new JsonReader(new StringReader("5.5"))));

    TypeAdapter<Number> doubleAdapter = TypeAdapters.DOUBLE;
    assertNull(doubleAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Double.valueOf(10.5), doubleAdapter.read(new JsonReader(new StringReader("10.5"))));

    // Number
    TypeAdapter<Number> numberAdapter = TypeAdapters.NUMBER;
    assertNull(numberAdapter.read(new JsonReader(new StringReader("null"))));
    assertNotNull(numberAdapter.read(new JsonReader(new StringReader("123"))));
    try {
      numberAdapter.read(new JsonReader(new StringReader("true")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Expecting number"));
    }
  }

  @Test
  public void testCharacterAdapter() throws Throwable {
    TypeAdapter<Character> adapter = TypeAdapters.CHARACTER;
    assertNull(adapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Character.valueOf('A'), adapter.read(new JsonReader(new StringReader("\"A\""))));

    try {
      adapter.read(new JsonReader(new StringReader("\"AB\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Expecting character"));
    }

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    StringWriter sw2 = new StringWriter();
    adapter.write(new JsonWriter(sw2), Character.valueOf('Z'));
    assertEquals("\"Z\"", sw2.toString());
  }

  @Test
  public void testStringAndBigAdapters() throws Throwable {
    TypeAdapter<String> strAdapter = TypeAdapters.STRING;
    assertNull(strAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("true", strAdapter.read(new JsonReader(new StringReader("true"))));
    assertEquals("hello", strAdapter.read(new JsonReader(new StringReader("\"hello\""))));

    TypeAdapter<BigDecimal> bdAdapter = TypeAdapters.BIG_DECIMAL;
    assertNull(bdAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(new BigDecimal("123.45"), bdAdapter.read(new JsonReader(new StringReader("123.45"))));
    try {
      bdAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }

    TypeAdapter<BigInteger> biAdapter = TypeAdapters.BIG_INTEGER;
    assertNull(biAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(new BigInteger("12345"), biAdapter.read(new JsonReader(new StringReader("12345"))));
    try {
      biAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // Expected
    }
  }

  @Test
  public void testStringBuilderAndBuffer() throws Throwable {
    TypeAdapter<StringBuilder> sbAdapter = TypeAdapters.STRING_BUILDER;
    assertNull(sbAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("test", sbAdapter.read(new JsonReader(new StringReader("\"test\""))).toString());
    
    StringWriter sw = new StringWriter();
    sbAdapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    TypeAdapter<StringBuffer> sbufAdapter = TypeAdapters.STRING_BUFFER;
    assertNull(sbufAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("test", sbufAdapter.read(new JsonReader(new StringReader("\"test\""))).toString());

    StringWriter sw2 = new StringWriter();
    sbufAdapter.write(new JsonWriter(sw2), null);
    assertEquals("null", sw2.toString());
  }

  @Test
  public void testUrlAndUriAdapters() throws Throwable {
    TypeAdapter<URL> urlAdapter = TypeAdapters.URL;
    assertNull(urlAdapter.read(new JsonReader(new StringReader("null"))));
    assertNull(urlAdapter.read(new JsonReader(new StringReader("\"null\""))));
    URL url = urlAdapter.read(new JsonReader(new StringReader("\"http://google.com\"")));
    assertNotNull(url);

    StringWriter sw = new StringWriter();
    urlAdapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    TypeAdapter<URI> uriAdapter = TypeAdapters.URI;
    assertNull(uriAdapter.read(new JsonReader(new StringReader("null"))));
    assertNull(uriAdapter.read(new JsonReader(new StringReader("\"null\""))));
    URI uri = uriAdapter.read(new JsonReader(new StringReader("\"http://google.com\"")));
    assertNotNull(uri);

    try {
      uriAdapter.read(new JsonReader(new StringReader("\"%xx\"")));
      fail("Expected JsonIOException");
    } catch (JsonIOException e) {
      // Expected
    }

    StringWriter sw2 = new StringWriter();
    uriAdapter.write(new JsonWriter(sw2), null);
    assertEquals("null", sw2.toString());
  }

  @Test
  public void testInetAddressAndUuidAdapters() throws Throwable {
    TypeAdapter<InetAddress> inetAdapter = TypeAdapters.INET_ADDRESS;
    assertNull(inetAdapter.read(new JsonReader(new StringReader("null"))));
    InetAddress addr = inetAdapter.read(new JsonReader(new StringReader("\"127.0.0.1\"")));
    assertNotNull(addr);

    StringWriter sw = new StringWriter();
    inetAdapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    TypeAdapter<UUID> uuidAdapter = TypeAdapters.UUID;
    assertNull(uuidAdapter.read(new JsonReader(new StringReader("null"))));
    UUID uuid = uuidAdapter.read(new JsonReader(new StringReader("\"12345678-1234-1234-1234-123456789abc\"")));
    assertNotNull(uuid);

    StringWriter sw2 = new StringWriter();
    uuidAdapter.write(new JsonWriter(sw2), null);
    assertEquals("null", sw2.toString());
  }

  @Test
  public void testTimestampFactory() throws Throwable {
    TypeAdapterFactory factory = TypeAdapters.TIMESTAMP_FACTORY;
    assertNull(factory.create(new Gson(), TypeToken.get(String.class)));
    TypeAdapter<Timestamp> tsAdapter = factory.create(new Gson(), TypeToken.get(Timestamp.class));
    assertNotNull(tsAdapter);

    StringWriter sw = new StringWriter();
    tsAdapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());
  }

  @Test
  public void testCalendarAdapter() throws Throwable {
    TypeAdapter<Calendar> adapter = TypeAdapters.CALENDAR;
    assertNull(adapter.read(new JsonReader(new StringReader("null"))));

    String json = "{\"year\":2012,\"month\":0,\"dayOfMonth\":1,\"hourOfDay\":0,\"minute\":0,\"second\":0}";
    Calendar cal = adapter.read(new JsonReader(new StringReader(json)));
    assertNotNull(cal);
    assertEquals(2012, cal.get(Calendar.YEAR));

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    StringWriter sw2 = new StringWriter();
    adapter.write(new JsonWriter(sw2), new GregorianCalendar(2012, 0, 1, 0, 0, 0));
    assertTrue(sw2.toString().contains("year"));
  }

  @Test
  public void testLocaleAdapter() throws Throwable {
    TypeAdapter<Locale> adapter = TypeAdapters.LOCALE;
    assertNull(adapter.read(new JsonReader(new StringReader("null"))));

    Locale loc1 = adapter.read(new JsonReader(new StringReader("\"en\"")));
    assertEquals("en", loc1.getLanguage());

    Locale loc2 = adapter.read(new JsonReader(new StringReader("\"en_US\"")));
    assertEquals("en", loc2.getLanguage());
    assertEquals("US", loc2.getCountry());

    Locale loc3 = adapter.read(new JsonReader(new StringReader("\"en_US_POSIX\"")));
    assertEquals("en", loc3.getLanguage());
    assertEquals("US", loc3.getCountry());
    assertEquals("POSIX", loc3.getVariant());

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());
  }

  @Test
  public void testJsonElementAdapter() throws Throwable {
    TypeAdapter<JsonElement> adapter = TypeAdapters.JSON_ELEMENT;

    JsonElement elString = adapter.read(new JsonReader(new StringReader("\"test\"")));
    assertTrue(elString.isJsonPrimitive());
    assertEquals("test", elString.getAsString());

    JsonElement elNum = adapter.read(new JsonReader(new StringReader("123")));
    assertTrue(elNum.isJsonPrimitive());
    assertEquals(123, elNum.getAsInt());

    JsonElement elBool = adapter.read(new JsonReader(new StringReader("true")));
    assertTrue(elBool.isJsonPrimitive());
    assertTrue(elBool.getAsBoolean());

    JsonElement elNull = adapter.read(new JsonReader(new StringReader("null")));
    assertTrue(elNull.isJsonNull());

    JsonElement elArr = adapter.read(new JsonReader(new StringReader("[1, 2]")));
    assertTrue(elArr.isJsonArray());

    JsonElement elObj = adapter.read(new JsonReader(new StringReader("{\"a\":1}")));
    assertTrue(elObj.isJsonObject());

    try {
      adapter.read(new JsonReader(new StringReader("}")));
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString());

    StringWriter sw2 = new StringWriter();
    adapter.write(new JsonWriter(sw2), new JsonPrimitive(Boolean.TRUE));
    assertEquals("true", sw2.toString());

    StringWriter sw3 = new StringWriter();
    adapter.write(new JsonWriter(sw3), new JsonPrimitive(Double.valueOf(1.1)));
    assertEquals("1.1", sw3.toString());

    StringWriter sw4 = new StringWriter();
    adapter.write(new JsonWriter(sw4), elArr);
    assertEquals("[1,2]", sw4.toString());

    StringWriter sw5 = new StringWriter();
    adapter.write(new JsonWriter(sw5), elObj);
    assertEquals("{\"a\":1}", sw5.toString());
  }

  @Test
  public void testEnumFactory() throws Throwable {
    TypeAdapterFactory factory = TypeAdapters.ENUM_FACTORY;
    assertNull(factory.create(new Gson(), TypeToken.get(String.class)));
    assertNull(factory.create(new Gson(), TypeToken.get(Enum.class)));

    TypeAdapter<Thread.State> stateAdapter = factory.create(new Gson(), TypeToken.get(Thread.State.class));
    assertNotNull(stateAdapter);

    StringWriter sw = new StringWriter();
    stateAdapter.write(new JsonWriter(sw), Thread.State.NEW);
    assertEquals("\"NEW\"", sw.toString());

    StringReader sr = new StringReader("\"NEW\"");
    assertEquals(Thread.State.NEW, stateAdapter.read(new JsonReader(sr)));

    StringReader srNull = new StringReader("null");
    assertNull(stateAdapter.read(new JsonReader(srNull)));
  }

  @Test
  public void testFactoriesToStringAndMisc() throws Throwable {
    TypeAdapter<String> dummyAdapter = TypeAdapters.STRING;
    TypeAdapterFactory f1 = TypeAdapters.newFactory(String.class, dummyAdapter);
    assertTrue(f1.toString().contains("Factory[type="));

    TypeAdapterFactory f2 = TypeAdapters.newFactory(String.class, String.class, dummyAdapter);
    assertTrue(f2.toString().contains("Factory[type="));

    TypeAdapterFactory f3 = TypeAdapters.newFactoryForMultipleTypes(String.class, String.class, dummyAdapter);
    assertTrue(f3.toString().contains("Factory[type="));

    TypeAdapterFactory f4 = TypeAdapters.newTypeHierarchyFactory(CharSequence.class, dummyAdapter);
    assertTrue(f4.toString().contains("Factory
[typeHierarchy="));
    assertNotNull(f4.create(new Gson(), TypeToken.get(String.class)));
    assertNull(f4.create(new Gson(), TypeToken.get(Integer.class)));
  }
}