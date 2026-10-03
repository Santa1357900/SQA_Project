package org.apache.commons.lang;

import junit.framework.TestCase;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unit tests {@link LocaleUtils}.
 */
public class LocaleUtilsTest extends TestCase {

    public LocaleUtilsTest(String name) {
        super(name);
    }

    public void testConstructor() throws Throwable {
        LocaleUtils obj = new LocaleUtils();
        assertNotNull(obj);
    }

    public void testToLocale_Null() throws Throwable {
        assertNull(LocaleUtils.toLocale(null));
    }

    public void testToLocale_Valid() throws Throwable {
        assertEquals(new Locale("en", ""), LocaleUtils.toLocale("en"));
        assertEquals(new Locale("en", "GB"), LocaleUtils.toLocale("en_GB"));
        assertEquals(new Locale("en", "GB", "xxx"), LocaleUtils.toLocale("en_GB_xxx"));
    }

    public void testToLocale_InvalidLength() throws Throwable {
        try {
            LocaleUtils.toLocale("e");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }

        try {
            LocaleUtils.toLocale("eng");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }

        try {
            LocaleUtils.toLocale("en_G");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }
    }

    public void testToLocale_InvalidLanguage() throws Throwable {
        try {
            LocaleUtils.toLocale("EN");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }

        try {
            LocaleUtils.toLocale("e1_GB");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }
    }

    public void testToLocale_InvalidSeparator() throws Throwable {
        try {
            LocaleUtils.toLocale("en-GB");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }

        try {
            LocaleUtils.toLocale("en_gb");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }

        try {
            LocaleUtils.toLocale("en_GB-xxx");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Invalid locale format") >= 0);
        }
    }

    public void testLocaleLookupList_Single() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        List list = LocaleUtils.localeLookupList(locale);
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    public void testLocaleLookupList_WithDefault() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en");
        List list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(new Locale("en", ""), list.get(3));
    }

    public void testLocaleLookupList_Null() throws Throwable {
        List list = LocaleUtils.localeLookupList(null);
        assertNotNull(list);
        assertEquals(0, list.size());

        List listWithDefault = LocaleUtils.localeLookupList(null, new Locale("en"));
        assertNotNull(listWithDefault);
        assertEquals(0, listWithDefault.size());
    }

    public void testLocaleLookupList_NoVariantOrCountry() throws Throwable {
        Locale locale = new Locale("fr");
        List list = LocaleUtils.localeLookupList(locale);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(new Locale("fr"), list.get(0));
    }

    public void testAvailableLocaleList() throws Throwable {
        List list1 = LocaleUtils.availableLocaleList();
        List list2 = LocaleUtils.availableLocaleList();
        assertNotNull(list1);
        assertSame(list1, list2);
        assertFalse(list1.isEmpty());
    }

    public void testAvailableLocaleSet() throws Throwable {
        Set set1 = LocaleUtils.availableLocaleSet();
        Set set2 = LocaleUtils.availableLocaleSet();
        assertNotNull(set1);
        assertSame(set1, set2);
        assertFalse(set1.isEmpty());
    }

    public void testIsAvailableLocale() throws Throwable {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.ENGLISH));
        assertFalse(LocaleUtils.isAvailableLocale(new Locale("xx", "YY", "ZZ")));
    }

    public void testLanguagesByCountry() throws Throwable {
        List langs = LocaleUtils.languagesByCountry("GB");
        assertNotNull(langs);
        assertTrue(langs.contains(Locale.UK));

        List emptyLangs = LocaleUtils.languagesByCountry(null);
        assertNotNull(emptyLangs);
        assertTrue(emptyLangs.isEmpty());
        assertSame(emptyLangs, LocaleUtils.languagesByCountry(null));
    }

    public void testCountriesByLanguage() throws Throwable {
        List countries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(countries);
        assertTrue(countries.contains(Locale.UK));
        assertTrue(countries.contains(Locale.US));

        List emptyCountries = LocaleUtils.countriesByLanguage(null);
        assertNotNull(emptyCountries);
        assertTrue(emptyCountries.isEmpty());
        assertSame(emptyCountries, LocaleUtils.countriesByLanguage(null));
    }
}