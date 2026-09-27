package org.joda.time.tz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.junit.Test;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;

public class ZoneInfoCompilerTest {

    @Test
    public void testVerboseAndStartOfYear() throws Throwable {
        boolean verbose = ZoneInfoCompiler.verbose();
        assertNotNull(Boolean.valueOf(verbose));

        ZoneInfoCompiler.DateTimeOfYear doy1 = ZoneInfoCompiler.getStartOfYear();
        ZoneInfoCompiler.DateTimeOfYear doy2 = ZoneInfoCompiler.getStartOfYear();
        assertNotNull(doy1);
        assertEquals(doy1, doy2);

        assertNotNull(ZoneInfoCompiler.getLenientISOChronology());
    }

    @Test
    public void testParseYear() throws Throwable {
        assertEquals(Integer.MIN_VALUE, ZoneInfoCompiler.parseYear("minimum", 2000));
        assertEquals(Integer.MIN_VALUE, ZoneInfoCompiler.parseYear("MIN", 2000));
        assertEquals(Integer.MAX_VALUE, ZoneInfoCompiler.parseYear("maximum", 2000));
        assertEquals(Integer.MAX_VALUE, ZoneInfoCompiler.parseYear("MAX", 2000));
        assertEquals(1995, ZoneInfoCompiler.parseYear("only", 1995));
        assertEquals(2023, ZoneInfoCompiler.parseYear("2023", 2000));
    }

    @Test
    public void testParseMonth() throws Throwable {
        assertEquals(1, ZoneInfoCompiler.parseMonth("Jan"));
        assertEquals(2, ZoneInfoCompiler.parseMonth("Feb"));
        assertEquals(12, ZoneInfoCompiler.parseMonth("Dec"));
    }

    @Test
    public void testParseDayOfWeek() throws Throwable {
        assertEquals(1, ZoneInfoCompiler.parseDayOfWeek("Mon"));
        assertEquals(7, ZoneInfoCompiler.parseDayOfWeek("Sun"));
    }

    @Test
    public void testParseOptional() throws Throwable {
        assertNull(ZoneInfoCompiler.parseOptional("-"));
        assertEquals("EST", ZoneInfoCompiler.parseOptional("EST"));
    }

    @Test
    public void testParseTime() throws Throwable {
        assertEquals(0, ZoneInfoCompiler.parseTime("0"));
        assertEquals(3600000, ZoneInfoCompiler.parseTime("01:00"));
        assertEquals(-3600000, ZoneInfoCompiler.parseTime("-01:00"));
        
        try {
            ZoneInfoCompiler.parseTime("INVALID_TIME");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("INVALID_TIME"));
        }
    }

    @Test
    public void testParseZoneChar() throws Throwable {
        assertEquals('s', ZoneInfoCompiler.parseZoneChar('s'));
        assertEquals('s', ZoneInfoCompiler.parseZoneChar('S'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('u'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('U'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('g'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('G'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('z'));
        assertEquals('u', ZoneInfoCompiler.parseZoneChar('Z'));
        assertEquals('w', ZoneInfoCompiler.parseZoneChar('w'));
        assertEquals('w', ZoneInfoCompiler.parseZoneChar('W'));
        assertEquals('w', ZoneInfoCompiler.parseZoneChar('x'));
    }

    @Test
    public void testWriteZoneInfoMap() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(baos);
        Map<String, DateTimeZone> zimap = new HashMap<String, DateTimeZone>();
        DateTimeZone utc = DateTimeZone.UTC;
        zimap.put("UTC", utc);

        ZoneInfoCompiler.writeZoneInfoMap(dout, zimap);
        dout.flush();
        assertTrue(baos.size() > 0);
    }

    @Test
    public void testTestValidation() throws Throwable {
        boolean result = ZoneInfoCompiler.test("UTC", DateTimeZone.UTC);
        assertTrue(result);

        boolean mismatchResult = ZoneInfoCompiler.test("America/New_York", DateTimeZone.UTC);
        assertFalse(mismatchResult);
    }

    @Test
    public void testParseDataFile() throws Throwable {
        String data = 
            "# This is a comment\n" +
            "Rule US 1967 1973 - Oct lastSun 02:00 1s D\n" +
            "Zone America/New_York -5:00 US EST%s 1973\n" +
            "Link America/New_York US/Eastern\n";

        BufferedReader reader = new BufferedReader(new StringReader(data));
        ZoneInfoCompiler zic = new ZoneInfoCompiler();
        zic.parseDataFile(reader);
        
        Map<String, DateTimeZone> compiled = zic.compile(null, null);
        assertNotNull(compiled);
    }

    @Test
    public void testDateTimeOfYearParsingEdgeCases() throws Throwable {
        StringTokenizer st1 = new StringTokenizer("Mar lastSun 24:00");
        ZoneInfoCompiler.DateTimeOfYear doy1 = new ZoneInfoCompiler.DateTimeOfYear(st1);
        assertNotNull(doy1);

        StringTokenizer st2 = new StringTokenizer("Mar Sun>=8 01:00s");
        ZoneInfoCompiler.DateTimeOfYear doy2 = new ZoneInfoCompiler.DateTimeOfYear(st2);
        assertNotNull(doy2);

        StringTokenizer st3 = new StringTokenizer("Mar Sun<=15 01:00u");
        ZoneInfoCompiler.DateTimeOfYear doy3 = new ZoneInfoCompiler.DateTimeOfYear(st3);
        assertNotNull(doy3);

        StringTokenizer st4 = new StringTokenizer("Mar 15 01:00w");
        ZoneInfoCompiler.DateTimeOfYear doy4 = new ZoneInfoCompiler.DateTimeOfYear(st4);
        assertNotNull(doy4);
        
        assertEquals("MonthOfYear: 3\nDayOfMonth: 15\nDayOfWeek: 0\nAdvanceDayOfWeek: false\nMillisOfDay: 3600000\nZoneChar: w\n", doy4.toString());
    }

    @Test
    public void testDateTimeOfYearInvalidSyntax() throws Throwable {
        try {
            StringTokenizer st = new StringTokenizer("Mar BADSYNTAX 01:00");
            new ZoneInfoCompiler.DateTimeOfYear(st);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("BADSYNTAX"));
        }
    }

    @Test
    public void testRuleInvalidToYear() throws Throwable {
        try {
            StringTokenizer st = new StringTokenizer("US 1973 1967 - Oct lastSun 02:00 1s D");
            new ZoneInfoCompiler.Rule(st);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMainMethodUsage() throws Throwable {
        ZoneInfoCompiler.main(new String[0]);
        ZoneInfoCompiler.main(new String[] { "-?" });
        ZoneInfoCompiler.main(new String[] { "-src" });
    }

    @Test
    public void testCompileWithNonExistentOutputDir() throws Throwable {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "zic_test_dir_" + System.currentTimeMillis());
        if (tempDir.exists()) {
            tempDir.delete();
        }
        ZoneInfoCompiler zic = new ZoneInfoCompiler();
        Map<String, DateTimeZone> map = zic.compile(tempDir, null);
        assertNotNull(map);
        assertTrue(tempDir.exists());
        tempDir.delete();
    }
}