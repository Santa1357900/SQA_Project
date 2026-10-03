package com.google.gson;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class DefaultDateTypeAdapterTest {

  @Test
  public void testConstructorsAndArguments() throws Throwable {
    DefaultDateTypeAdapter adapter1 = new DefaultDateTypeAdapter(Date.class);
    assertNotNull(adapter1);

    DefaultDateTypeAdapter adapter2 = new DefaultDateTypeAdapter(java.sql.Date.class, "yyyy-MM-dd");
    assertNotNull(adapter2);

    DefaultDateTypeAdapter adapter3 = new DefaultDateTypeAdapter(Timestamp.class, DateFormat.SHORT);
    assertNotNull(adapter3);

    DefaultDateTypeAdapter adapter4 = new DefaultDateTypeAdapter(DateFormat.SHORT, DateFormat.SHORT);
    assertNotNull(adapter4);

    DefaultDateTypeAdapter adapter5 = new DefaultDateTypeAdapter(java.sql.Date.class, DateFormat.SHORT, DateFormat.SHORT);
    assertNotNull(adapter5);
  }

  @Test
  public void testInvalidDateTypeConstructor() throws Throwable {
    try {
      new DefaultDateTypeAdapter(java.util.Calendar.class);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Date type must be one of"));
    }
  }

  @Test
  public void testWriteNullValue() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class);
    CharArrayWriter writer = new CharArrayWriter();
    JsonWriter jsonWriter = new JsonWriter(writer);

    adapter.write(jsonWriter, null);
    assertEquals("null", writer.toString());
  }

  @Test
  public void testWriteDate() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    CharArrayWriter writer = new CharArrayWriter();
    JsonWriter jsonWriter = new JsonWriter(writer);

    Date date = new Date(0L); // 1970-01-01
    adapter.write(jsonWriter, date);
    assertTrue(writer.toString().contains("1970-01-01"));
  }

  @Test
  public void testReadNonStringThrowsException() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class);
    JsonReader reader = new JsonReader(new CharArrayReader("123".toCharArray()));

    try {
      adapter.read(reader);
      fail("Expected JsonParseException");
    } catch (JsonParseException e) {
      assertTrue(e.getMessage().contains("The date should be a string value"));
    }
  }

  @Test
  public void testReadValidDates() throws Throwable {
    // Test java.util.Date
    DefaultDateTypeAdapter adapterDate = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    JsonReader reader1 = new JsonReader(new CharArrayReader("\"1970-01-01\"".toCharArray()));
    Date d1 = adapterDate.read(reader1);
    assertNotNull(d1);
    assertTrue(d1 instanceof Date);
    // Ensure it's not strictly a Timestamp or java.sql.Date subclass unless expected
    assertEquals(Date.class, d1.getClass());

    // Test Timestamp
    DefaultDateTypeAdapter adapterTimestamp = new DefaultDateTypeAdapter(Timestamp.class, "yyyy-MM-dd");
    JsonReader reader2 = new JsonReader(new CharArrayReader("\"1970-01-01\"".toCharArray()));
    Date d2 = adapterTimestamp.read(reader2);
    assertNotNull(d2);
    assertTrue(d2 instanceof Timestamp);

    // Test java.sql.Date
    DefaultDateTypeAdapter adapterSqlDate = new DefaultDateTypeAdapter(java.sql.Date.class, "yyyy-MM-dd");
    JsonReader reader3 = new JsonReader(new CharArrayReader("\"1970-01-01\"".toCharArray()));
    Date d3 = adapterSqlDate.read(reader3);
    assertNotNull(d3);
    assertTrue(d3 instanceof java.sql.Date);
  }

  @Test
  public void testDeserializeDifferentFormats() throws Throwable {
    // 1. Local format or enUs format or ISO8601Utils fallback
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    
    // ISO8601 format parsing test
    JsonReader reader = new JsonReader(new CharArrayReader("\"1970-01-01T00:00:00Z\"".toCharArray()));
    Date parsed = adapter.read(reader);
    assertNotNull(parsed);
  }

  @Test
  public void testDeserializeSyntaxException() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new CharArrayReader("\"not-a-date-at-all-xyz\"".toCharArray()));

    try {
      adapter.read(reader);
      fail("Expected JsonSyntaxException");
    } catch (JsonSyntaxException e) {
      assertTrue(e.getMessage().contains("not-a-date-at-all-xyz"));
    }
  }

  @Test
  public void testToString() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    String str = adapter.toString();
    assertNotNull(str);
    assertTrue(str.startsWith("DefaultDateTypeAdapter"));
  }
}