package org.apache.commons.lang3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

public class LocaleUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        assertNotNull(new LocaleUtils());
        Constructor<LocaleUtils>[] constructors = (Constructor<LocaleUtils>[]) LocaleUtils.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
    }

    @Test
    public void testToLocale_Null() throws Throwable {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_Valid() throws Throwable {
        Locale loc = LocaleUtils.toLocale("en");
        assertNotNull(loc);
        assertEquals("en", loc.getLanguage());
        assertEquals("", loc.getCountry());
        assertEquals("", loc.getVariant());

        loc = LocaleUtils.toLocale("en_GB");
        assertNotNull(loc);
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("", loc.getVariant());

        loc = LocaleUtils.toLocale("en_GB_xxx");
        assertNotNull(loc);
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("xxx", loc.getVariant());

        loc = LocaleUtils.toLocale("en__xxx");
        assertNotNull(loc);
        assertEquals("en", loc.getLanguage());
        assertEquals("", loc.getCountry());
        assertEquals("xxx", loc.getVariant());
    }

    @Test
    public void testToLocale_InvalidLength() throws Throwable {
        try {
            LocaleUtils.toLocale("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        try {
            LocaleUtils.toLocale("a");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        try {
            LocaleUtils.toLocale("e");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        try {
            LocaleUtils.toLocale("e_");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        try {
            LocaleUtils.toLocale("en_G");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        try {
            LocaleUtils.toLocale("en_GBX");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testToLocale_InvalidCases() throws Throwable {
        // Upper case language
        try {
            LocaleUtils.toLocale("EN");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        // Missing underscore at index 2
        try {
            LocaleUtils.toLocale("enXGB");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        // Lower case country
        try {
            LocaleUtils.toLocale("en_gb");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }

        // Missing underscore at index 5 when len >= 7
        try {
            LocaleUtils.toLocale("en_GB-xxx");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid locale format"));
        }
    }

    @Test
    public void testLocaleLookupList_Single() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDefault() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en");
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_Null() throws Throwable {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());

        List<Locale> listWithDefault = LocaleUtils.localeLookupList(null, new Locale("en"));
        assertNotNull(listWithDefault);
        assertTrue(listWithDefault.isEmpty());
    }

    @Test
    public void testLocaleLookupList_AlreadyContainsDefault() throws Throwable {
        Locale locale = new Locale("fr");
        List<Locale> list = LocaleUtils.localeLookupList(locale, new Locale("fr"));
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(locale, list.get(0));
    }

    @Test
    public void testAvailableLocaleList() throws Throwable {
        List<Locale> list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        
        try {
            list.add(Locale.ENGLISH);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAvailableLocaleSet() throws Throwable {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertNotNull(set);
        assertFalse(set.isEmpty());

        try {
            set.add(Locale.ENGLISH);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testIsAvailableLocale() throws Throwable {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
        assertFalse(LocaleUtils.isAvailableLocale(new Locale("zz", "ZZ", "ZZZ")));
        assertFalse(LocaleUtils.isAvailableLocale(null));
    }

    @Test
    public void testLanguagesByCountry() throws Throwable {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());

        List<Locale> usLangs = LocaleUtils.languagesByCountry("US");
        assertNotNull(usLangs);
        // US should at least have English
        boolean foundEnglish = false;
        for (int i = 0; i < usLangs.size(); i++) {
            if ("en".equals(usLangs.get(i).getLanguage())) {
                foundEnglish = true;
                break;
            }
        }
        assertTrue(foundEnglish);

        // Test caching / repeated calls
        List<Locale> usLangsAgain = LocaleUtils.languagesByCountry("US");
        assertNotNull(usLangsAgain);
        assertEquals(usLangs.size(), usLangsAgain.size());

        // Test non-existent country
        List<Locale> none = LocaleUtils.languagesByCountry("XX");
        assertNotNull(none);
        assertTrue(none.isEmpty());
    }

    @Test
    public void testCountriesByLanguage() throws Throwable {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());

        List<Locale> enCountries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(enCountries);
        boolean foundUS = false;
        for (int i = 0; i < enCountries.size(); i++) {
            if ("US".equals(enCountries.get(i).getCountry())) {
                foundUS = true;
                break;
            }
        }
        assertTrue(foundUS);

        // Test caching / repeated calls
        List<Locale> enCountriesAgain = LocaleUtils.countriesByLanguage("en");
        assertNotNull(enCountriesAgain);
        assertEquals(enCountries.size(), enCountriesAgain.size());

        // Test non-existent language
        List<Locale> none = LocaleUtils.countriesByLanguage("xx");
        assertNotNull(none);
        assertTrue(none.isEmpty());
    }
}