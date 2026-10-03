package org.apache.commons.lang.time;

import junit.framework.TestCase;
import java.util.TimeZone;

public class DurationFormatUtilsTest extends TestCase {

    public DurationFormatUtilsTest(String name) {
        super(name);
    }

    public void testConstructor() throws Throwable {
        DurationFormatUtils utils = new DurationFormatUtils();
        assertNotNull(utils);
    }

    public void testFormatDurationHMS() throws Throwable {
        long duration = 3661001L; // 1 hour, 1 minute, 1 second, 1 millisecond
        String result = DurationFormatUtils.formatDurationHMS(duration);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    public void testFormatDurationISO() throws Throwable {
        long duration = 86400000L + 3600000L; // 1 day and 1 hour
        String result = DurationFormatUtils.formatDurationISO(duration);
        assertNotNull(result);
    }

    public void testFormatDurationBasic() throws Throwable {
        long duration = 5000L; // 5 seconds
        String result = DurationFormatUtils.formatDuration(duration, "s's'");
        assertEquals("5s", result);

        String resultPadded = DurationFormatUtils.formatDuration(duration, "ss", true);
        assertEquals("05", resultPadded);
    }

    public void testFormatDurationTokens() throws Throwable {
        long duration = 9005000L; // 2 hours, 30 minutes, 5 seconds
        String format = "H m s S d y M";
        String result = DurationFormatUtils.formatDuration(duration, format, false);
        assertNotNull(result);
    }

    public void testFormatDurationWords() throws Throwable {
        long duration = 90061000L; // 1 day, 1 hour, 1 minute, 1 second
        String res1 = DurationFormatUtils.formatDurationWords(duration, true, true);
        assertNotNull(res1);

        String res2 = DurationFormatUtils.formatDurationWords(duration, false, false);
        assertNotNull(res2);

        String res3 = DurationFormatUtils.formatDurationWords(1000L, true, true); // 1 second (singular)
        assertNotNull(res3);
        
        String res4 = DurationFormatUtils.formatDurationWords(60000L, true, true); // 1 minute
        assertNotNull(res4);

        String res5 = DurationFormatUtils.formatDurationWords(3600000L, true, true); // 1 hour
        assertNotNull(res5);

        String res6 = DurationFormatUtils.formatDurationWords(86400000L, true, true); // 1 day
        assertNotNull(res6);
    }

    public void testFormatPeriodISO() throws Throwable {
        long start = 0L;
        long end = 100000000L;
        String result = DurationFormatUtils.formatPeriodISO(start, end);
        assertNotNull(result);
    }

    public void testFormatPeriod() throws Throwable {
        long start = 0L;
        long end = 30L * 24L * 60L * 60L * 1000L; // ~30 days
        String result = DurationFormatUtils.formatPeriod(start, end, "y M d H m s S", true, TimeZone.getDefault());
        assertNotNull(result);

        // Short period (< 28 days)
        long shortStart = 0L;
        long shortEnd = 5000L;
        String shortResult = DurationFormatUtils.formatPeriod(shortStart, shortEnd, "s's'");
        assertEquals("5s", shortResult);
    }

    public void testFormatPeriodEdgeCases() throws Throwable {
        // Test with different timezones and various token combinations
        long start = 1234567890000L;
        long end = 1234600000000L;
        TimeZone tz = TimeZone.getTimeZone("GMT");
        
        String resNoYears = DurationFormatUtils.formatPeriod(start, end, "M d H m s S", true, tz);
        assertNotNull(resNoYears);

        String resNoMonths = DurationFormatUtils.formatPeriod(start, end, "y d H m s S", true, tz);
        assertNotNull(resNoMonths);

        String resNoDays = DurationFormatUtils.formatPeriod(start, end, "y M H m s S", true, tz);
        assertNotNull(resNoDays);

        String resNoHours = DurationFormatUtils.formatPeriod(start, end, "y M d m s S", true, tz);
        assertNotNull(resNoHours);

        String resNoMinutes = DurationFormatUtils.formatPeriod(start, end, "y M d H s S", true, tz);
        assertNotNull(resNoMinutes);

        String resNoSeconds = DurationFormatUtils.formatPeriod(start, end, "y M d H m S", true, tz);
        assertNotNull(resNoSeconds);
    }

    public void testTokenClass() throws Throwable {
        DurationFormatUtils.Token token1 = new DurationFormatUtils.Token("y");
        DurationFormatUtils.Token token2 = new DurationFormatUtils.Token("y", 2);
        DurationFormatUtils.Token token3 = new DurationFormatUtils.Token(new StringBuffer("literal"), 1);
        DurationFormatUtils.Token token4 = new DurationFormatUtils.Token(Integer.valueOf(5), 1);

        token1.increment();
        assertEquals(2, token1.getCount());
        assertEquals("y", token1.getValue());
        assertEquals("yy", token1.toString());
        assertNotNull(token1.hashCode());

        assertFalse(token1.equals(null));
        assertFalse(token1.equals("not a token"));
        assertFalse(token1.equals(new DurationFormatUtils.Token("M", 2))); // different value
        assertFalse(token1.equals(new DurationFormatUtils.Token("y", 3))); // different count
        assertTrue(token1.equals(token2));

        // Test token with Number and StringBuffer equals
        assertTrue(token4.equals(new DurationFormatUtils.Token(Integer.valueOf(5), 1)));
        assertFalse(token4.equals(new DurationFormatUtils.Token(Integer.valueOf(6), 1)));

        DurationFormatUtils.Token tokenStr1 = new DurationFormatUtils.Token(new StringBuffer("abc"), 1);
        DurationFormatUtils.Token tokenStr2 = new DurationFormatUtils.Token(new StringBuffer("abc"), 1);
        DurationFormatUtils.Token tokenStr3 = new DurationFormatUtils.Token(new StringBuffer("xyz"), 1);
        assertTrue(tokenStr1.equals(tokenStr2));
        assertFalse(tokenStr1.equals(tokenStr3));

        // Test containsTokenWithValue
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] { token1, token2 };
        assertTrue(DurationFormatUtils.Token.containsTokenWithValue(tokens, "y"));
        assertFalse(DurationFormatUtils.Token.containsTokenWithValue(tokens, "M"));
    }

    public void testLexxAndLiteralEscaping() throws Throwable {
        String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx(pattern);
        assertNotNull(tokens);
        assertTrue(tokens.length > 0);
    }
}