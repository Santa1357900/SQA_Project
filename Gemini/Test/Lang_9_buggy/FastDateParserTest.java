package org.apache.commons.lang3.time;

import org.junit.Test;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class FastDateParserTest {

    @Test
    public void testConstructorAndAccessors() throws Throwable {
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
        FastDateParser parser4 = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("EST"), locale);
        FastDateParser parser5 = new FastDateParser("yyyy-MM-dd", tz, Locale.FRANCE);

        assertTrue(parser1.equals(parser1));
        assertTrue(parser1.equals(parser2));
        assertEquals(parser1.hashCode(), parser2.hashCode());

        assertFalse(parser1.equals(parser3));
        assertFalse(parser1.equals(parser4));
        assertFalse(parser1.equals(parser5));
        assertFalse(parser1.equals("NotAParser"));
        assertFalse(parser1.equals(null));
    }

    @Test
    public void testInvalidPattern() throws Throwable {
        try {
            new FastDateParser("", TimeZone.getDefault(), Locale.US);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid pattern"));
        }
    }

    @Test
    public void testParseValidDate() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("2023-10-05");
        assertNotNull(date);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.setTime(date);
        assertEquals(2023, cal.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH));
        assertEquals(5, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testParseObjectWithString() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Object obj = parser.parseObject("2023-01-15");
        assertNotNull(obj);
        assertTrue(obj instanceof Date);
    }

    @Test
    public void testParseWithParsePosition() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Date date = parser.parse("2023-05-10 extra", pos);
        assertNotNull(date);
        assertEquals(10, pos.getIndex());

        ParsePosition posObj = new ParsePosition(0);
        Object obj = parser.parseObject("2023-05-10", posObj);
        assertNotNull(obj);
        assertEquals(10, posObj.getIndex());
    }

    @Test
    public void testParseFailureReturnsNull() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Date date = parser.parse("invalid", pos);
        assertNull(date);
        assertEquals(0, pos.getIndex());
    }

    @Test
    public void testParseThrowsParseException() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        try {
            parser.parse("invalid-date");
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Unparseable date"));
        }
    }

    @Test
    public void testJapaneseImperialLocaleParseException() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), FastDateParser.JAPANESE_IMPERIAL);
        try {
            parser.parse("invalid");
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("locale does not support dates before 1868"));
        }
    }

    @Test
    public void testAllStrategiesAndFields() throws Throwable {
        // Testing various patterns to trigger different strategies (D, E, F, G, H, K, M, S, W, Z, a, d, h, k, m, s, w, y, z, quotes)
        String pattern = "G yyyy MM dd HH k h m s S D F W E a z Z '' 'quoted' z";
        FastDateParser parser = new FastDateParser(pattern, TimeZone.getTimeZone("GMT"), Locale.US);
        assertNotNull(parser.getParsePattern());
    }

    @Test
    public void testNumberStrategiesAndModifiers() throws Throwable {
        FastDateParser parser = new FastDateParser("H K m s S y M d w W D F", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("13 1 30 45 500 2023 05 15 10 2 120 3");
        assertNotNull(date);
    }

    @Test
    public void testTwoDigitYearAdjustment() throws Throwable {
        FastDateParser parser = new FastDateParser("yy", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("15");
        assertNotNull(date);
    }

    @Test
    public void testTextStrategiesAndInvalidValues() throws Throwable {
        FastDateParser parser = new FastDateParser("E M G a", TimeZone.getTimeZone("UTC"), Locale.US);
        try {
            parser.parse("InvalidDay InvalidMonth AD PM");
            fail("Expected ParseException or IllegalArgumentException");
        } catch (Exception e) {
            // Expected either ParseException or nested IllegalArgumentException
            assertNotNull(e);
        }
    }

    @Test
    public void testTimeZoneStrategy() throws Throwable {
        FastDateParser parser = new FastDateParser("z Z", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("UTC +00:00");
        assertNotNull(date);

        Date dateGmt = parser.parse("GMT+0200 +0200");
        assertNotNull(dateGmt);

        try {
            parser.parse("UnknownTimeZone +0000");
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Unparseable date"));
        }
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
        assertNotNull(deserialized.parse("2023-12-31"));
    }

    @Test
    public void testGetDisplayNamesInvalidField() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        try {
            parser.getDisplayNames(999);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid field value"));
        }
    }

    @Test
    public void testCopyQuotedStrategyEdgeCases() throws Throwable {
        // Pattern with trailing single quote or empty quotes
        FastDateParser parser = new FastDateParser("yyyy''MM''dd", TimeZone.getTimeZone("UTC"), Locale.US);
        Date date = parser.parse("2023'01'15");
        assertNotNull(date);
    }
}