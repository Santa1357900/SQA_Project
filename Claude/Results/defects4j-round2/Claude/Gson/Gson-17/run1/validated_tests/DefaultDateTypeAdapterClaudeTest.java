package com.google.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class DefaultDateTypeAdapterClaudeTest {

  // Date subclass unsupported by the adapter, used to trigger the constructor validation branch.
  private static class CustomDate extends Date {
  }

  private TimeZone originalTimeZone;
  private Locale originalLocale;

  @Before
  public void setUp() throws Throwable {
    originalTimeZone = TimeZone.getDefault();
    originalLocale = Locale.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Locale.setDefault(Locale.US);
  }

  @After
  public void tearDown() throws Throwable {
    TimeZone.setDefault(originalTimeZone);
    Locale.setDefault(originalLocale);
  }

  // Constructor(Class) with Date.class must not throw and must build a usable adapter.
  @Test
  public void testConstructor_dateTypeDateClass_doesNotThrow() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class);
    assertTrue(adapter.toString().startsWith("DefaultDateTypeAdapter("));
  }

  // Constructor(Class) with Timestamp.class must not throw.
  @Test
  public void testConstructor_dateTypeTimestampClass_doesNotThrow() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Timestamp.class);
    assertTrue(adapter.toString().startsWith("DefaultDateTypeAdapter("));
  }

  // Constructor(Class) with java.sql.Date.class must not throw.
  @Test
  public void testConstructor_dateTypeSqlDateClass_doesNotThrow() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(java.sql.Date.class);
    assertTrue(adapter.toString().startsWith("DefaultDateTypeAdapter("));
  }

  // Unsupported Date subclass must trigger the validation branch and throw IllegalArgumentException.
  @Test
  public void testConstructor_invalidDateType_throwsIllegalArgumentException() throws Throwable {
    try {
      new DefaultDateTypeAdapter(CustomDate.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Pattern based constructor must not throw and must expose SimpleDateFormat in toString().
  @Test
  public void testConstructor_datePattern_createsUsableAdapter() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    assertTrue(adapter.toString().contains("SimpleDateFormat"));
  }

  // Style based package-private constructor must produce a working adapter (round trip works).
  @Test
  public void testConstructor_dateStyle_roundTripWorks() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, DateFormat.SHORT);
    Date date = new GregorianCalendar(2017, Calendar.FEBRUARY, 1).getTime();
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, date);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertTrue(result instanceof Date);
  }

  // Public constructor(dateStyle,timeStyle) defaults dateType to Date.class and works end to end.
  @Test
  public void testConstructor_dateAndTimeStyles_defaultsToDateType() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(DateFormat.SHORT, DateFormat.SHORT);
    Date now = new Date(0L);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, now);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertTrue(result instanceof Date);
  }

  // Public constructor(Class,dateStyle,timeStyle) must not throw for a valid type.
  @Test
  public void testConstructor_dateTypeAndStyles_createsUsableAdapter() throws Throwable {
    DefaultDateTypeAdapter adapter =
        new DefaultDateTypeAdapter(Timestamp.class, DateFormat.MEDIUM, DateFormat.MEDIUM);
    assertTrue(adapter.toString().startsWith("DefaultDateTypeAdapter("));
  }

  // write(null) must emit the JSON null literal (value==null branch).
  @Test
  public void testWrite_nullValue_writesJsonNull() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, null);
    assertEquals("null", sw.toString());
  }

  // write(non-null) must produce the en-US formatted, quoted date string.
  @Test
  public void testWrite_nonNullValue_writesFormattedString() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    Date date = new GregorianCalendar(2013, Calendar.MARCH, 15).getTime();
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, date);
    assertEquals("\"2013-03-15\"", sw.toString());
  }

  // read() must succeed via the first (localFormat) parse attempt for a matching pattern string.
  @Test
  public void testRead_localFormatMatch_parsesDirectly() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new StringReader("\"2020-01-01\""));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    Date expected = new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime();
    assertEquals(expected.getTime(), result.getTime());
  }

  // read() must round-trip a value previously produced by write() for dateType==Date.class.
  @Test
  public void testRead_roundTripWithDateType_returnsEquivalentDate() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    Date date = new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime();
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, date);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertEquals(date.getTime(), result.getTime());
  }

  // read() with dateType==Timestamp.class must return a Timestamp instance (branch coverage).
  @Test
  public void testRead_dateTypeTimestamp_returnsTimestampInstance() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Timestamp.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new StringReader("\"2020-01-01\""));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertTrue(result instanceof Timestamp);
  }

  // read() with dateType==java.sql.Date.class must return a java.sql.Date instance (branch coverage).
  @Test
  public void testRead_dateTypeSqlDate_returnsSqlDateInstance() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(java.sql.Date.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new StringReader("\"2020-01-01\""));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertTrue(result instanceof java.sql.Date);
  }

  // read() must throw JsonParseException when the next token is not a string.
  @Test
  public void testRead_nonStringToken_throwsJsonParseException() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new StringReader("123"));
    reader.setLenient(true);
    try {
      adapter.read(reader);
      fail("expected JsonParseException");
    } catch (JsonParseException expected) {
    }
  }

  // read() must throw JsonSyntaxException when all three parse strategies fail.
  @Test
  public void testRead_unparseableString_throwsJsonSyntaxException() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    JsonReader reader = new JsonReader(new StringReader("\"not-a-date\""));
    reader.setLenient(true);
    try {
      adapter.read(reader);
      fail("expected JsonSyntaxException");
    } catch (JsonSyntaxException expected) {
    }
  }

  // read() must fall back to ISO8601Utils when both localFormat and enUsFormat fail to parse.
  @Test
  public void testRead_iso8601String_fallsBackAndParsesCorrectly() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "'NOTADATE'");
    JsonReader reader = new JsonReader(new StringReader("\"2014-10-23T00:00:00.000Z\""));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    Date expected = new GregorianCalendar(2014, Calendar.OCTOBER, 23).getTime();
    assertEquals(expected.getTime(), result.getTime());
  }

  // toString() must contain the simple class name of the localFormat's runtime type.
  @Test
  public void testToString_containsFormatClassSimpleName() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class, "yyyy-MM-dd");
    String result = adapter.toString();
    assertTrue(result.contains("SimpleDateFormat"));
  }

  // toString() must start with the literal adapter name "DefaultDateTypeAdapter(".
  @Test
  public void testToString_startsWithClassName() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Timestamp.class);
    String result = adapter.toString();
    assertEquals(0, result.indexOf("DefaultDateTypeAdapter("));
  }

  // write()/read() round trip for java.sql.Date must preserve the exact time value.
  @Test
  public void testReadWrite_sqlDateRoundTrip_preservesValue() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(java.sql.Date.class, "yyyy-MM-dd");
    java.sql.Date date =
        new java.sql.Date(new GregorianCalendar(2021, Calendar.JUNE, 10).getTimeInMillis());
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, date);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertEquals(date.getTime(), result.getTime());
  }

  // write()/read() round trip for Timestamp must preserve the exact second-precision value.
  @Test
  public void testReadWrite_timestampRoundTrip_preservesValue() throws Throwable {
    DefaultDateTypeAdapter adapter =
        new DefaultDateTypeAdapter(Timestamp.class, "yyyy-MM-dd'T'HH:mm:ss");
    Timestamp ts =
        new Timestamp(new GregorianCalendar(2019, Calendar.DECEMBER, 25, 10, 30, 0).getTimeInMillis());
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, ts);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    assertEquals(ts.getTime(), result.getTime());
  }

  // Default-format constructor (no pattern) must round-trip its own write() output closely.
  @Test
  public void testReadWrite_defaultFormatConstructor_roundTrips() throws Throwable {
    DefaultDateTypeAdapter adapter = new DefaultDateTypeAdapter(Date.class);
    Date date = new GregorianCalendar(2015, Calendar.MAY, 5, 12, 0, 0).getTime();
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, date);
    JsonReader reader = new JsonReader(new StringReader(sw.toString()));
    reader.setLenient(true);
    Date result = adapter.read(reader);
    long diff = Math.abs(date.getTime() - result.getTime());
    assertTrue(diff < 1000L);
  }
}
