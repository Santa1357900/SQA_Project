package org.apache.commons.lang3.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateParserTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale locale = Locale.US;
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", tz, locale);

        assertEquals("yyyy-MM-dd", parser.getPattern());
        assertEquals(tz, parser.getTimeZone());
        assertEquals(locale, parser.getLocale());
        assertNotNull(parser.toString());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale locale = Locale.US;
        FastDateParser parser1 = new FastDateParser("yyyy-MM-dd", tz, locale);
        FastDateParser parser2 = new FastDateParser("yyyy-MM-dd", tz, locale);
        FastDateParser parser3 = new FastDateParser("yyyy/MM/dd", tz, locale);
        FastDateParser parser4 = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("PST"), locale);
        FastDateParser parser5 = new FastDateParser("yyyy-MM-dd", tz, Locale.GERMAN);

        assertTrue(parser1.equals(parser1));
        assertTrue(parser1.equals(parser2));
        assertEquals(parser1.hashCode(), parser2.hashCode());

        assertFalse(parser1.equals(null));
        assertFalse(parser1.equals("SomeString"));
        assertFalse(parser1.equals(parser3));
        assertFalse(parser1.equals(parser4));
        assertFalse(parser1.equals(parser5));
    }

    @Test
    public void testInvalidPattern() throws Throwable {
        boolean thrown = false;
        try {
            new FastDateParser("", TimeZone.getDefault(), Locale.US);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseValidDate() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("2023-05-15");
        assertNotNull(date);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.setTime(date);
        assertEquals(2023, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testParseWithParsePosition() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Date date = parser.parse("2023-05-15 extra", pos);
        assertNotNull(date);
        assertEquals(10, pos.getIndex());

        ParsePosition invalidPos = new ParsePosition(0);
        Date invalidDate = parser.parse("invalid", invalidPos);
        assertNull(invalidDate);
        assertEquals(0, invalidPos.getIndex());
    }

    @Test
    public void testParseObject() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Object obj = parser.parseObject("2023-05-15");
        assertNotNull(obj);
        assertTrue(obj instanceof Date);

        ParsePosition pos = new ParsePosition(0);
        Object objPos = parser.parseObject("2023-05-15", pos);
        assertNotNull(objPos);
        assertEquals(10, pos.getIndex());
    }

    @Test
    public void testParseJapaneseImperialLocaleException() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), FastDateParser.JAPANESE_IMPERIAL);
        boolean thrown = false;
        try {
            parser.parse("invalid-date");
        } catch (ParseException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("locale does not support dates before 1868 AD"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseVariousStrategies() throws Throwable {
        // Testing various format components: D, E, F, G, H, K, M, S, W, a, d, h, k, m, s, w, y, z, quoted strings
        String pattern = "G yyyy-MM-dd D F H K M S W a d h k m s w z '' 'text'";
        FastDateParser parser = new FastDateParser(pattern, TimeZone.getTimeZone("UTC"), Locale.US);
        assertNotNull(parser.getParsePattern());
    }

    @Test
    public void testSerialization() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(parser);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FastDateParser deserialized = (FastDateParser) ois.readObject();
        ois.close();

        assertEquals(parser, deserialized);
        assertNotNull(deserialized.parse("2023-01-01"));
    }

    @Test
    public void testAbbreviatedYearAdjustment() throws Throwable {
        FastDateParser parser = new FastDateParser("yy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("20-01-01");
        assertNotNull(date);
    }

    @Test
    public void testTimeZoneStrategyParsing() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd z", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("2023-01-01 GMT");
        assertNotNull(date);

        Date dateOffset = parser.parse("2023-01-01 +02:00");
        assertNotNull(dateOffset);
        
        Date dateOffsetShort = parser.parse("2023-01-01 +0200");
        assertNotNull(dateOffsetShort);
    }

    @Test(expected = ParseException.class)
    public void testTimeZoneStrategyInvalid() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd z", TimeZone.getTimeZone("UTC"), Locale.US);
        parser.parse("2023-01-01 InvalidZoneName");
    }

    @Test
    public void testTextStrategyInvalidValue() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy MMMM", TimeZone.getTimeZone("UTC"), Locale.US);
        boolean thrown = false;
        try {
            parser.parse("2023 NotAMonth");
        } catch (ParseException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}