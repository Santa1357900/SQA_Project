package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import java.util.Locale;

public class LocaleUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        LocaleUtils utils = new LocaleUtils();
        assertNotNull(utils);
    }

    @Test
    public void testToLocaleNull() throws Throwable {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocaleLanguage() throws Throwable {
        Locale locale = LocaleUtils.toLocale("en");
        assertNotNull(locale);
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocaleLanguageCountry() throws Throwable {
        Locale locale = LocaleUtils.toLocale("en_GB");
        assertNotNull(locale);
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocaleLanguageCountryVariant() throws Throwable {
        Locale locale = LocaleUtils.toLocale("en_GB_xxx");
        assertNotNull(locale);
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("xxx", locale.getVariant());
    }

    @Test
    public void testToLocaleInvalidLength1() throws Throwable {
        try {
            LocaleUtils.toLocale("e");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidLength3() throws Throwable {
        try {
            LocaleUtils.toLocale("eng");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidLength6() throws Throwable {
        try {
            LocaleUtils.toLocale("en_G");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidCharLanguage() throws Throwable {
        try {
            LocaleUtils.toLocale("En");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidCharLanguage2() throws Throwable {
        try {
            LocaleUtils.toLocale("e1");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidSeparator1() throws Throwable {
        try {
            LocaleUtils.toLocale("en-GB");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidCharCountry() throws Throwable {
        try {
            LocaleUtils.toLocale("en_gb");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidCharCountry2() throws Throwable {
        try {
            LocaleUtils.toLocale("en_G9");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocaleInvalidSeparator2() throws Throwable {
        try {
            LocaleUtils.toLocale("en_GB-xxx");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testLocaleLookupListWithVariant() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        List list = LocaleUtils.localeLookupList(locale);
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr"), list.get(2));
    }

    @Test
    public void testLocaleLookupListWithDefault() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en");
        List list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr"), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupListNull() throws Throwable {
        List list = LocaleUtils.localeLookupList(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupListNullLocaleWithDefault() throws Throwable {
        Locale defaultLocale = new Locale("en");
        List list = LocaleUtils.localeLookupList(null, defaultLocale);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupListAlreadyContainsDefault() throws Throwable {
        Locale locale = new Locale("en");
        List list = LocaleUtils.localeLookupList(locale, locale);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(locale, list.get(0));
    }

    @Test
    public void testAvailableLocaleList() throws Throwable {
        List list1 = LocaleUtils.availableLocaleList();
        List list2 = LocaleUtils.availableLocaleList();
        assertNotNull(list1);
        assertSame(list1, list2);
        assertFalse(list1.isEmpty());
    }

    @Test
    public void testAvailableLocaleSet() throws Throwable {
        Set set1 = LocaleUtils.availableLocaleSet();
        Set set2 = LocaleUtils.availableLocaleSet();
        assertNotNull(set1);
        assertSame(set1, set2);
        assertFalse(set1.isEmpty());
    }

    @Test
    public void testIsAvailableLocale() throws Throwable {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.ENGLISH));
        assertFalse(LocaleUtils.isAvailableLocale(new Locale("xx", "YY", "ZZ")));
    }

    @Test
    public void testLanguagesByCountry() throws Throwable {
        List langsNull = LocaleUtils.languagesByCountry(null);
        assertNotNull(langsNull);
        assertTrue(langsNull.isEmpty());

        List langsUS = LocaleUtils.languagesByCountry("US");
        assertNotNull(langsUS);
        // Caching verification
        List langsUS2 = LocaleUtils.languagesByCountry("US");
        assertSame(langsUS, langsUS2);
    }

    @Test
    public void testCountriesByLanguage() throws Throwable {
        List countriesNull = LocaleUtils.countriesByLanguage(null);
        assertNotNull(countriesNull);
        assertTrue(countriesNull.isEmpty());

        List countriesEn = LocaleUtils.countriesByLanguage("en");
        assertNotNull(countriesEn);
        // Caching verification
        List countriesEn2 = LocaleUtils.countriesByLanguage("en");
        assertSame(countriesEn, countriesEn2);
    }
}