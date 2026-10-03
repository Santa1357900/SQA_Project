package com.google.gson.internal.bind;

import org.junit.Test;
import static org.junit.Assert.*;

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
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

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

public class TypeAdaptersTest {

  private enum SampleEnum {
    FIRST,
    SECOND
  }

  @Test
  public void testClassAdapter() throws Throwable {
    TypeAdapter<Class> adapter = TypeAdapters.CLASS;
    assertNotNull(adapter);

    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    try {
      adapter.write(jw, String.class);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("Attempted to serialize java.lang.Class"));
    }

    StringReader sr = new StringReader("null");
    JsonReader jr = new JsonReader(sr);
    assertNull(adapter.read(jr));

    StringReader sr2 = new StringReader("\"java.lang.String\"");
    JsonReader jr2 = new JsonReader(sr2);
    try {
      adapter.read(jr2);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("Attempted to deserialize a java.lang.Class"));
    }

    assertNotNull(TypeAdapters.CLASS_FACTORY);
    assertNull(TypeAdapters.CLASS_FACTORY.create(new Gson(), TypeToken.get(String.class)));
    assertNotNull(TypeAdapters.CLASS_FACTORY.create(new Gson(), TypeToken.get(Class.class)));
  }

  @Test
  public void testBitSetAdapter() throws Throwable {
    TypeAdapter<BitSet> adapter = TypeAdapters.BIT_SET;
    assertNotNull(adapter);

    // Write null
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    adapter.write(jw, null);
    assertEquals("null", sw.toString().trim());

    // Write BitSet
    BitSet bs = new BitSet();
    bs.set(0);
    bs.set(2);
    StringWriter sw2 = new StringWriter();
    JsonWriter jw2 = new JsonWriter(sw2);
    adapter.write(jw2, bs);
    assertTrue(sw2.toString().contains("1"));

    // Read BitSet
    StringReader sr = new StringReader("[1, 0, \"1\", true]");
    JsonReader jr = new JsonReader(sr);
    BitSet parsed = adapter.read(jr);
    assertNotNull(parsed);
    assertTrue(parsed.get(0));
    assertFalse(parsed.get(1));
    assertTrue(parsed.get(2));
    assertTrue(parsed.get(3));

    // Read BitSet with null
    StringReader srNull = new StringReader("null");
    JsonReader jrNull = new JsonReader(srNull);
    assertNull(adapter.read(jrNull));

    // Read BitSet invalid string number
    StringReader srInvalidStr = new StringReader("[\"abc\"]");
    JsonReader jrInvalidStr = new JsonReader(srInvalidStr);
    try {
      adapter.read(jrInvalidStr);
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("bitset number value"));
    }

    // Read BitSet invalid type
    StringReader srInvalidType = new StringReader("[{}]");
    JsonReader jrInvalidType = new JsonReader(srInvalidType);
    try {
      adapter.read(jrInvalidType);
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Invalid bitset value type"));
    }

    assertNotNull(TypeAdapters.BIT_SET_FACTORY);
  }

  @Test
  public void testBooleanAdapter() throws Throwable {
    TypeAdapter<Boolean> adapter = TypeAdapters.BOOLEAN;
    assertNotNull(adapter);

    StringReader srNull = new StringReader("null");
    assertNull(adapter.read(new JsonReader(srNull)));

    StringReader srStr = new StringReader("\"true\"");
    assertEquals(Boolean.TRUE, adapter.read(new JsonReader(srStr)));

    StringReader srBool = new StringReader("false");
    assertEquals(Boolean.FALSE, adapter.read(new JsonReader(srBool)));

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), Boolean.TRUE);
    assertEquals("true", sw.toString());

    // Boolean as string adapter
    TypeAdapter<Boolean> strAdapter = TypeAdapters.BOOLEAN_AS_STRING;
    StringReader srStr2 = new StringReader("null");
    assertNull(strAdapter.read(new JsonReader(srStr2)));

    StringReader srStr3 = new StringReader("\"true\"");
    assertEquals(Boolean.TRUE, strAdapter.read(new JsonReader(srStr3)));

    StringWriter sw2 = new StringWriter();
    strAdapter.write(new JsonWriter(sw2), null);
    assertEquals("\"null\"", sw2.toString());

    assertNotNull(TypeAdapters.BOOLEAN_FACTORY);
  }

  @Test
  public void testNumberAdapters() throws Throwable {
    // Byte
    TypeAdapter<Number> byteAdapter = TypeAdapters.BYTE;
    assertNull(byteAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Byte.valueOf((byte) 5), byteAdapter.read(new JsonReader(new StringReader("5"))));
    try {
      byteAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // Short
    TypeAdapter<Number> shortAdapter = TypeAdapters.SHORT;
    assertNull(shortAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Short.valueOf((short) 10), shortAdapter.read(new JsonReader(new StringReader("10"))));
    try {
      shortAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // Integer
    TypeAdapter<Number> intAdapter = TypeAdapters.INTEGER;
    assertNull(intAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Integer.valueOf(100), intAdapter.read(new JsonReader(new StringReader("100"))));
    try {
      intAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // Long
    TypeAdapter<Number> longAdapter = TypeAdapters.LONG;
    assertNull(longAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Long.valueOf(100L), longAdapter.read(new JsonReader(new StringReader("100"))));
    try {
      longAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // Float & Double
    TypeAdapter<Number> floatAdapter = TypeAdapters.FLOAT;
    assertNull(floatAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Float.valueOf(1.5f), floatAdapter.read(new JsonReader(new StringReader("1.5"))));

    TypeAdapter<Number> doubleAdapter = TypeAdapters.DOUBLE;
    assertNull(doubleAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Double.valueOf(2.5), doubleAdapter.read(new JsonReader(new StringReader("2.5"))));

    // Number general
    TypeAdapter<Number> numAdapter = TypeAdapters.NUMBER;
    assertNull(numAdapter.read(new JsonReader(new StringReader("null"))));
    assertNotNull(numAdapter.read(new JsonReader(new StringReader("123"))));
    try {
      numAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // Factories check
    assertNotNull(TypeAdapters.BYTE_FACTORY);
    assertNotNull(TypeAdapters.SHORT_FACTORY);
    assertNotNull(TypeAdapters.INTEGER_FACTORY);
    assertNotNull(TypeAdapters.NUMBER_FACTORY);
  }

  @Test
  public void testAtomicsAndOthers() throws Throwable {
    // AtomicInteger
    TypeAdapter<AtomicInteger> aiAdapter = TypeAdapters.ATOMIC_INTEGER;
    AtomicInteger ai = aiAdapter.read(new JsonReader(new StringReader("10")));
    assertEquals(10, ai.get());
    StringWriter swAI = new StringWriter();
    aiAdapter.write(new JsonWriter(swAI), new AtomicInteger(5));
    assertTrue(swAI.toString().contains("5"));

    try {
      aiAdapter.read(new JsonReader(new StringReader("\"abc\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    // AtomicBoolean
    TypeAdapter<AtomicBoolean> abAdapter = TypeAdapters.ATOMIC_BOOLEAN;
    AtomicBoolean ab = abAdapter.read(new JsonReader(new StringReader("true")));
    assertTrue(ab.get());
    StringWriter swAB = new StringWriter();
    abAdapter.write(new JsonWriter(swAB), new AtomicBoolean(false));

    // AtomicIntegerArray
    TypeAdapter<AtomicIntegerArray> aiaAdapter = TypeAdapters.ATOMIC_INTEGER_ARRAY;
    AtomicIntegerArray aia = aiaAdapter.read(new JsonReader(new StringReader("[1, 2, 3]")));
    assertEquals(3, aia.length());
    assertEquals(2, aia.get(1));
    StringWriter swAIA = new StringWriter();
    aiaAdapter.write(new JsonWriter(swAIA), aia);

    try {
      aiaAdapter.read(new JsonReader(new StringReader("[\"abc\"]")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }

    assertNotNull(TypeAdapters.ATOMIC_INTEGER_FACTORY);
    assertNotNull(TypeAdapters.ATOMIC_BOOLEAN_FACTORY);
    assertNotNull(TypeAdapters.ATOMIC_INTEGER_ARRAY_FACTORY);
  }

  @Test
  public void testCharacterAdapter() throws Throwable {
    TypeAdapter<Character> adapter = TypeAdapters.CHARACTER;
    assertNull(adapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(Character.valueOf('a'), adapter.read(new JsonReader(new StringReader("\"a\""))));

    try {
      adapter.read(new JsonReader(new StringReader("\"ab\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Expecting character"));
    }

    StringWriter sw = new StringWriter();
    adapter.write(new JsonWriter(sw), null);
    assertEquals("null", sw.toString().trim());

    assertNotNull(TypeAdapters.CHARACTER_FACTORY);
  }

  @Test
  public void testStringAndBigAdapters() throws Throwable {
    // String
    TypeAdapter<String> strAdapter = TypeAdapters.STRING;
    assertNull(strAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("true", strAdapter.read(new JsonReader(new StringReader("true"))));
    assertEquals("hello", strAdapter.read(new JsonReader(new StringReader("\"hello\""))));
    assertNotNull(TypeAdapters.STRING_FACTORY);

    // BigDecimal
    TypeAdapter<BigDecimal> bdAdapter = TypeAdapters.BIG_DECIMAL;
    assertNull(bdAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(new BigDecimal("123.45"), bdAdapter.read(new JsonReader(new StringReader("123.45"))));
    try {
      bdAdapter.read(new JsonReader(new StringReader("\"not-a-number\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }
    StringWriter swBD = new StringWriter();
    bdAdapter.write(new JsonWriter(swBD), new BigDecimal("1.0"));

    // BigInteger
    TypeAdapter<BigInteger> biAdapter = TypeAdapters.BIG_INTEGER;
    assertNull(biAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals(new BigInteger("12345"), biAdapter.read(new JsonReader(new StringReader("12345"))));
    try {
      biAdapter.read(new JsonReader(new StringReader("\"not-a-number\"")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      // expected
    }
    StringWriter swBI = new StringWriter();
    biAdapter.write(new JsonWriter(swBI), new BigInteger("10"));

    // StringBuilder
    TypeAdapter<StringBuilder> sbAdapter = TypeAdapters.STRING_BUILDER;
    assertNull(sbAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("test", sbAdapter.read(new JsonReader(new StringReader("\"test\""))).toString());
    StringWriter swSB = new StringWriter();
    sbAdapter.write(new JsonWriter(swSB), null);
    sbAdapter.write(new JsonWriter(new StringWriter()), new StringBuilder("abc"));
    assertNotNull(TypeAdapters.STRING_BUILDER_FACTORY);

    // StringBuffer
    TypeAdapter<StringBuffer> sbufAdapter = TypeAdapters.STRING_BUFFER;
    assertNull(sbufAdapter.read(new JsonReader(new StringReader("null"))));
    assertEquals("test", sbufAdapter.read(new JsonReader(new StringReader("\"test\""))).toString());
    StringWriter swSBuf = new StringWriter();
    sbufAdapter.write(new JsonWriter(swSBuf), null);
    sbufAdapter.write(new JsonWriter(new StringWriter()), new StringBuffer("abc"));
    assertNotNull(TypeAdapters.STRING_BUFFER_FACTORY);
  }

  @Test
  public void testNetworkAndIdAdapters() throws Throwable {
    // URL
    TypeAdapter<URL> urlAdapter = TypeAdapters.URL;
    assertNull(urlAdapter.read(new JsonReader(new StringReader("null"))));
    assertNull(urlAdapter.read(new JsonReader(new StringReader("\"null\""))));
    assertNotNull(urlAdapter.read(new JsonReader(new StringReader("\"http://google.com\""))));
    StringWriter swURL = new StringWriter();
    urlAdapter.write(new JsonWriter(swURL), null);
    urlAdapter.write(new JsonWriter(new StringWriter()), new URL("http://google.com"));
    assertNotNull(TypeAdapters.URL_FACTORY);

    // URI
    TypeAdapter<URI> uriAdapter = TypeAdapters.URI;
    assertNull(uriAdapter.read(new JsonReader(new StringReader("null"))));
    assertNull(uriAdapter.read(new JsonReader(new StringReader("\"null\""))));
    assertNotNull(uriAdapter.read(new JsonReader(new StringReader("\"http://google.com\""))));
    try {
      uriAdapter.read(new JsonReader(new StringReader("\"[invalid-uri]\"")));
      fail("Expected JsonIOException");
    } catch (JsonIOException e) {
      // expected
    }
    StringWriter swURI = new StringWriter();
    uriAdapter.write(new JsonWriter(swURI), null);
    uriAdapter.write(new JsonWriter(new StringWriter()), new URI("http://google.com"));
    assertNotNull(TypeAdapters.URI_FACTORY);

    // InetAddress
    TypeAdapter<InetAddress> inetAdapter = TypeAdapters.INET_ADDRESS;
    assertNull(inetAdapter.read(new JsonReader(new StringReader("null"))));
    assertNotNull(inetAdapter.read(new JsonReader(new StringReader("\"127.0.0.1\""))));
    StringWriter swInet = new StringWriter();
    inetAdapter.write(new JsonWriter(swInet), null);
    inetAdapter.write(new JsonWriter(new StringWriter()), InetAddress.getByName("127.0.0.1"));
    assertNotNull(TypeAdapters.INET_ADDRESS_FACTORY);

    // UUID
    TypeAdapter<UUID> uuidAdapter = TypeAdapters.UUID;
    assertNull(uuidAdapter.read(new JsonReader(new StringReader("null"))));
    UUID uuid = UUID.randomUUID();
    UUID parsedUuid = uuidAdapter.read(new JsonReader(new StringReader("\"" + uuid.toString() + "\"")));
    assertEquals(uuid, parsedUuid);
    StringWriter swUuid = new StringWriter();
    uuidAdapter.write(new JsonWriter(swUuid), null);
    uuidAdapter.write(new JsonWriter(new StringWriter()), uuid);
    assertNotNull(TypeAdapters.UUID_FACTORY);

    // Currency
    TypeAdapter<Currency> currAdapter = TypeAdapters.CURRENCY;
    Currency usd = Currency.getInstance("USD");
    StringWriter swCurr = new StringWriter();
    currAdapter.write(new JsonWriter(swCurr), usd);
    Currency parsedCurr = currAdapter.read(new JsonReader(new StringReader("\"USD\"")));
    assertEquals(usd, parsedCurr);
    assertNotNull(TypeAdapters.CURRENCY_FACTORY);
  }

  @Test
  public void testTimestampAndCalendarAndLocale() throws Throwable {
    // Timestamp Factory
    assertNotNull(TypeAdapters.TIMESTAMP_FACTORY);
    assertNull(TypeAdapters.TIMESTAMP_FACTORY.create(new Gson(), TypeToken.get(String.class)));
    assertNotNull(TypeAdapters.TIMESTAMP_FACTORY.create(new Gson(), TypeToken.get(Timestamp.class)));

    // Calendar
    TypeAdapter<Calendar> calAdapter = TypeAdapters.CALENDAR;
    assertNull(calAdapter.read(new JsonReader(new StringReader("null"))));
    String calJson = "{\"year\":2020,\"month\":0,\"dayOfMonth\":1,\"hourOfDay\":12,\"minute\":30,\"second\":0}";
    Calendar cal = calAdapter.read(new JsonReader(new StringReader(calJson)));
    assertNotNull(cal);
    assertEquals(2020, cal.get(Calendar.YEAR));

    StringWriter swCal = new StringWriter();
    calAdapter.write(new JsonWriter(swCal), null);
    calAdapter.write(new JsonWriter(new StringWriter()), new GregorianCalendar());
    assertNotNull(TypeAdapters.CALENDAR_FACTORY);

    // Locale
    TypeAdapter<Locale> locAdapter = TypeAdapters.LOCALE;
    assertNull(locAdapter.read(new JsonReader(new StringReader("null"))));
    Locale loc1 = locAdapter.read(new JsonReader(new StringReader("\"en\"")));
    assertEquals("en", loc1.getLanguage());

    Locale loc2 = locAdapter.read(new JsonReader(new StringReader("\"en_US\"")));
    assertEquals("en", loc2.getLanguage());
    assertEquals("US", loc2.getCountry());

    Locale loc3 = locAdapter.read(new JsonReader(new StringReader("\"en_US_VARIANT\"")));
    assertEquals("en", loc3.getLanguage());
    assertEquals("US", loc3.getCountry());
    assertEquals("VARIANT", loc3.getVariant());

    StringWriter swLoc = new StringWriter();
    locAdapter.write(new JsonWriter(swLoc), null);
    locAdapter.write(new JsonWriter(new StringWriter()), Locale.US);
    assertNotNull(TypeAdapters.LOCALE_FACTORY);
  }

  @Test
  public void testJsonElementAdapter() throws Throwable {
    TypeAdapter<JsonElement> jeAdapter = TypeAdapters.JSON_ELEMENT;
    assertNotNull(jeAdapter);

    // Read different primitives & structures
    assertEquals(new JsonPrimitive("hello"), jeAdapter.read(new JsonReader(new StringReader("\"hello\""))));
    assertEquals(new JsonPrimitive(true), jeAdapter.read(new JsonReader(new StringReader("true"))));
    assertEquals(JsonNull.INSTANCE, jeAdapter.read(new JsonReader(new StringReader("null"))));
    assertNotNull(jeAdapter.read(new JsonReader(new StringReader("[1, 2]"))));
    assertNotNull(jeAdapter.read(new JsonReader(new StringReader("{\"a\":1}"))));

    try {
      jeAdapter.read(new JsonReader(new StringReader("")));
      fail("Expected IllegalArgumentException or EOFException");
    } catch (Exception e) {
      // expected
    }

    // Write elements
    StringWriter sw = new StringWriter();
    jeAdapter.write(new JsonWriter(sw), null);
    jeAdapter.write(new JsonWriter(new StringWriter()), new JsonPrimitive(123));
    jeAdapter.write(new JsonWriter(new StringWriter()), new JsonPrimitive(true));
    jeAdapter.write(new JsonWriter(new StringWriter()), new JsonPrimitive("str"));
    jeAdapter.write(new JsonWriter(new StringWriter()), new JsonArray());
    jeAdapter.write(new JsonWriter(new StringWriter()), new JsonObject());

    try {
      jeAdapter.write(new JsonWriter(new StringWriter()), new JsonElement() {
        @Override
        public JsonElement deepCopy() { return this; }
      });
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Couldn't write"));
    }

    assertNotNull(TypeAdapters.JSON_ELEMENT_FACTORY);
  }

  @Test
  public void testEnumFactoryAndHelpers() throws Throwable {
    assertNotNull(TypeAdapters.ENUM_FACTORY);
    assertNull(TypeAdapters.ENUM_FACTORY.create(new Gson(), TypeToken.get(String.class)));
    assertNotNull(TypeAdapters.ENUM_FACTORY.create(new Gson(), TypeToken.get(SampleEnum.class)));

    // Test enum reading/writing via Gson or factory directly if possible, or create an instance
    TypeAdapter<SampleEnum> enumAdapter = TypeAdapters.ENUM_FACTORY.create(new Gson(), TypeToken.get(SampleEnum.class));
    assertNotNull(enumAdapter);

    StringWriter sw = new StringWriter();
    enumAdapter.write(new JsonWriter(sw), SampleEnum.FIRST);
    assertTrue(sw.toString().contains("FIRST"));

    SampleEnum val = enumAdapter.read(new JsonReader(new StringReader("\"FIRST\"")));
    assertEquals(SampleEnum.FIRST, val);

    assertNull(enumAdapter.read(new JsonReader(new StringReader("null"))));

    // Test newFactory methods
    assertNotNull(TypeAdapters.newFactory(String.class, TypeAdapters.STRING));
    assertNotNull(TypeAdapters.newFactory(TypeToken.get(String.class), TypeAdapters.STRING));
    assertNotNull(TypeAdapters.newFactory(String.class, String.class, TypeAdapters.STRING));
    assertNotNull(TypeAdapters.newFactoryForMultipleTypes(String.class, String.class, TypeAdapters.STRING));

    // TypeHierarchyFactory
    TypeAdapterFactory thFactory = TypeAdapters.newTypeHierarchyFactory(Number.class, TypeAdapters.INTEGER);
    assertNotNull(thFactory);
    assertNull(thFactory.create(new Gson(), TypeToken.get(String.class)));
    assertNotNull(thFactory.create(new Gson(), TypeToken.get(Integer.class)));

    TypeAdapter<Number> numHierarchyAdapter = thFactory.create(new Gson(), TypeToken.get(Number.class));
    assertNotNull(numHierarchyAdapter);
    
    // Test read with type mismatch
    TypeAdapterFactory strictThFactory = TypeAdapters.newTypeHierarchyFactory(Number.class, TypeAdapters.DOUBLE);
    TypeAdapter<Integer> intHierarchyAdapter = strictThFactory.create(new Gson(), TypeToken.get(Integer.class));
    try {
      intHierarchyAdapter.read(new JsonReader(new StringReader("1.5")));
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("Expected a"));
    }
  }
}