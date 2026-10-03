package org.apache.commons.lang3.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.FieldPosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class FastDatePrinterTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullPattern() throws Throwable {
        new FastDatePrinter(null, TimeZone.getDefault(), Locale.US);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullTimeZone() throws Throwable {
        new FastDatePrinter("yyyy-MM-dd", null, Locale.US);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullLocale() throws Throwable {
        new FastDatePrinter("yyyy-MM-dd", TimeZone.getDefault(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPattern() throws Throwable {
        new FastDatePrinter("yyyy-MM-dd-X", TimeZone.getDefault(), Locale.US);
    }

    @Test
    public void testBasicFormatting() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("GMT"), Locale.US);
        
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.set(2023, Calendar.JANUARY, 15, 14, 30, 45);
        cal.set(Calendar.MILLISECOND, 123);

        String formatted = printer.format(cal);
        assertEquals("2023-01-15 14:30:45", formatted);

        Date date = cal.getTime();
        assertEquals("2023-01-15 14:30:45", printer.format(date));
        assertEquals("2023-01-15 14:30:45", printer.format(date.getTime()));

        StringBuffer buf = new StringBuffer();
        StringBuffer returnedBuf = printer.format(cal, buf);
        assertSame(buf, returnedBuf);
        assertEquals("2023-01-15 14:30:45", buf.toString());
    }

    @Test
    public void testAccessors() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale locale = Locale.UK;
        String pattern = "yyyy/MM/dd";
        
        FastDatePrinter printer = new FastDatePrinter(pattern, tz, locale);
        assertEquals(pattern, printer.getPattern());
        assertEquals(tz, printer.getTimeZone());
        assertEquals(locale, printer.getLocale());
        assertTrue(printer.getMaxLengthEstimate() > 0);
        assertNotNull(printer.toString());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        FastDatePrinter printer1 = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        FastDatePrinter printer2 = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        FastDatePrinter printer3 = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter printer4 = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.UK);
        FastDatePrinter printer5 = new FastDatePrinter("yyyy-MM", TimeZone.getTimeZone("UTC"), Locale.US);

        assertTrue(printer1.equals(printer1));
        assertTrue(printer1.equals(printer2));
        assertEquals(printer1.hashCode(), printer2.hashCode());

        assertFalse(printer1.equals(null));
        assertFalse(printer1.equals("NotAPrinter"));
        assertFalse(printer1.equals(printer3));
        assertFalse(printer1.equals(printer4));
        assertFalse(printer1.equals(printer5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnknownObject() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("yyyy-MM-dd", TimeZone.getDefault(), Locale.US);
        printer.format(new Object(), new StringBuffer(), new FieldPosition(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatNullObject() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("yyyy-MM-dd", TimeZone.getDefault(), Locale.US);
        printer.format(null, new StringBuffer(), new FieldPosition(0));
    }

    @Test
    public void testFormatLongObject() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        Long millis = Long.valueOf(0L); // 1970
        StringBuffer buf = new StringBuffer();
        printer.format(millis, buf, new FieldPosition(0));
        assertEquals("1970", buf.toString());
    }

    @Test
    public void testSerialization() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(printer);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FastDatePrinter deserialized = (FastDatePrinter) ois.readObject();
        ois.close();

        assertNotNull(deserialized);
        assertEquals(printer.getPattern(), deserialized.getPattern());
        assertEquals(printer.getTimeZone(), deserialized.getTimeZone());
        assertEquals(printer.getLocale(), deserialized.getLocale());
    }

    @Test
    public void testAllPatternComponents() throws Throwable {
        String pattern = "G y yy yyy yyyy M MM MMM MMMM d h H m s S E EE EEE EEEE D F w W a k K z Z ZZ '' 'lit'";
        FastDatePrinter printer = new FastDatePrinter(pattern, TimeZone.getTimeZone("GMT"), Locale.US);
        
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.set(2023, Calendar.JANUARY, 5, 13, 5, 9);
        cal.set(Calendar.MILLISECOND, 5);

        String result = printer.format(cal);
        assertNotNull(result);
        assertTrue(result.contains("2023"));
        assertTrue(result.contains("lit"));
    }

    @Test
    public void testSingleDigitAndPaddedFields() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("H m s S d M yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.set(2, Calendar.FEBRUARY, 3, 4, 5, 6);
        cal.set(Calendar.MILLISECOND, 7);

        String result = printer.format(cal);
        assertEquals("4 5 6 7 3 2 0002", result);
    }

    @Test
    public void testHourBoundariesAndTwelveTwentyFour() throws Throwable {
        FastDatePrinter printer = new FastDatePrinter("h H k K a", TimeZone.getTimeZone("GMT"), Locale.US);
        
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.set(2023, Calendar.JANUARY, 1, 0, 0, 0); // Midnight
        assertEquals("12 0 24 0 AM", printer.format(cal));

        cal.set(2023, Calendar.JANUARY, 1, 12, 0, 0); // Noon
        assertEquals("12 12 12 0 PM", printer.format(cal));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPaddedNumberFieldInvalidSize() throws Throwable {
        new FastDatePrinter("yyyyy", TimeZone.getDefault(), Locale.US);
    }

    @Test
    public void testTimeZoneDisplayCache() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        String name1 = FastDatePrinter.getTimeZoneDisplay(tz, false, TimeZone.SHORT, Locale.US);
        String name2 = FastDatePrinter.getTimeZoneDisplay(tz, false, TimeZone.SHORT, Locale.US);
        assertEquals(name1, name2);
        
        String nameDaylight1 = FastDatePrinter.getTimeZoneDisplay(tz, true, TimeZone.SHORT, Locale.US);
        String nameDaylight2 = FastDatePrinter.getTimeZoneDisplay(tz, true, TimeZone.SHORT, Locale.US);
        assertEquals(nameDaylight1, nameDaylight2);
    }
}