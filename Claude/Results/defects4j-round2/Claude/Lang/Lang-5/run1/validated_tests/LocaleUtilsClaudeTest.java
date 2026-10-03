package org.apache.commons.lang3;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LocaleUtilsClaudeTest {

    // public no-arg constructor is callable
    @Test
    public void testConstructor_publicNoArg_instanceCreated() throws Throwable {
        LocaleUtils lu = new LocaleUtils();
        assertNotNull(lu);
    }

    // str == null -> return null (no exception)
    @Test
    public void testToLocale_null_returnsNull() throws Throwable {
        assertNull(LocaleUtils.toLocale(null));
    }

    // len == 0 < 2 -> throws
    @Test
    public void testToLocale_emptyString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // len == 1 < 2 -> throws
    @Test
    public void testToLocale_lengthOne_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("e");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // len == 2, both lowercase -> new Locale(str)
    @Test
    public void testToLocale_twoLetterLowercase_returnsLanguageOnlyLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en");
        assertEquals("en", result.getLanguage());
        assertEquals("", result.getCountry());
    }

    // ch0 not lowercase -> throws
    @Test
    public void testToLocale_firstCharUppercase_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("En");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ch1 not lowercase -> throws
    @Test
    public void testToLocale_secondCharUppercase_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("eN");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // len == 5, valid language_country -> new Locale(lang, country)
    @Test
    public void testToLocale_languageCountry_returnsCorrectLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en_GB");
        assertEquals("en", result.getLanguage());
        assertEquals("GB", result.getCountry());
    }

    // separator at index 2 must be '_' -> throws when it is not
    @Test
    public void testToLocale_wrongSeparatorAtIndex2_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en-GB");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ch3 and ch4 both lowercase -> throws (country must be uppercase)
    @Test
    public void testToLocale_countryLowercase_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_gb");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ch3 lowercase only -> throws
    @Test
    public void testToLocale_countryFirstCharLowercase_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_gB");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ch4 lowercase only -> throws
    @Test
    public void testToLocale_countrySecondCharLowercase_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_Gb");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // len == 3, 3 < 5 -> throws
    @Test
    public void testToLocale_lengthThree_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("eng");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // len == 4, 4 < 5 -> throws
    @Test
    public void testToLocale_lengthFour_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("engl");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // exact Javadoc example: en_GB_xxx -> new Locale("en","GB","xxx")
    @Test
    public void testToLocale_languageCountryVariant_matchesJavadocExample() throws Throwable {
        Locale result = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", result.getLanguage());
        assertEquals("GB", result.getCountry());
        assertEquals("xxx", result.getVariant());
    }

    // ch3 == '_' branch (variant without country) -> new Locale(lang, "", variant)
    @Test
    public void testToLocale_languageVariantNoCountry_returnsCorrectLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en__xxx");
        assertEquals("en", result.getLanguage());
        assertEquals("", result.getCountry());
        assertEquals("xxx", result.getVariant());
    }

    // len == 6, 6 < 7 -> throws (incomplete variant section)
    @Test
    public void testToLocale_lengthSix_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_GBx");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // separator at index 5 must be '_' -> throws when missing
    @Test
    public void testToLocale_missingSeparatorAtIndex5_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_GBxxx");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // variant content may contain digits -> final return statement
    @Test
    public void testToLocale_variantWithDigits_returnsCorrectLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en_GB_123");
        assertEquals("en", result.getLanguage());
        assertEquals("GB", result.getCountry());
        assertEquals("123", result.getVariant());
    }

    // single-arg overload, exact Javadoc example with variant+country
    @Test
    public void testLocaleLookupList_singleArg_matchesJavadocExample() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr"), list.get(2));
    }

    // two-arg overload, exact Javadoc example with distinct default locale
    @Test
    public void testLocaleLookupList_twoArg_matchesJavadocExample() throws Throwable {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en");
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(new Locale("fr", "CA", "xxx"), list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr"), list.get(2));
        assertEquals(new Locale("en"), list.get(3));
    }

    // locale == null -> empty list, per Javadoc
    @Test
    public void testLocaleLookupList_nullLocale_returnsEmptyList() throws Throwable {
        List<Locale> list = LocaleUtils.localeLookupList(null, new Locale("en"));
        assertTrue(list.isEmpty());
    }

    // no country, no variant, default equals locale -> single element list
    @Test
    public void testLocaleLookupList_noCountryNoVariantSameAsDefault_singleElementList() throws Throwable {
        List<Locale> list = LocaleUtils.localeLookupList(new Locale("de"));
        assertEquals(1, list.size());
        assertEquals(new Locale("de"), list.get(0));
    }

    // country present, no variant, default equals locale -> two element list
    @Test
    public void testLocaleLookupList_countryNoVariant_returnsTwoElementList() throws Throwable {
        List<Locale> list = LocaleUtils.localeLookupList(Locale.GERMANY);
        assertEquals(2, list.size());
        assertEquals(Locale.GERMANY, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1));
    }

    // result list must be unmodifiable
    @Test
    public void testLocaleLookupList_resultIsUnmodifiable() throws Throwable {
        List<Locale> list = LocaleUtils.localeLookupList(new Locale("en"));
        try {
            list.add(Locale.FRANCE);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // wraps Locale.getAvailableLocales(), contains known locale, unmodifiable
    @Test
    public void testAvailableLocaleList_containsKnownLocale_andUnmodifiable() throws Throwable {
        List<Locale> list = LocaleUtils.availableLocaleList();
        assertTrue(list.contains(Locale.US));
        try {
            list.add(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // wraps Locale.getAvailableLocales() as set, contains known locale, unmodifiable
    @Test
    public void testAvailableLocaleSet_containsKnownLocale_andUnmodifiable() throws Throwable {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertTrue(set.contains(Locale.US));
        try {
            set.add(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // known available locale -> true
    @Test
    public void testIsAvailableLocale_knownLocale_returnsTrue() throws Throwable {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    // null input handled gracefully -> false, no exception
    @Test
    public void testIsAvailableLocale_null_returnsFalse() throws Throwable {
        assertFalse(LocaleUtils.isAvailableLocale(null));
    }

    // fictional locale not in available list -> false
    @Test
    public void testIsAvailableLocale_unknownLocale_returnsFalse() throws Throwable {
        assertFalse(LocaleUtils.isAvailableLocale(new Locale("xx", "YY")));
    }

    // null countryCode -> empty immutable list
    @Test
    public void testLanguagesByCountry_null_returnsEmptyImmutableList() throws Throwable {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertTrue(list.isEmpty());
        try {
            list.add(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // known country code -> contains matching locale, every entry filtered correctly
    @Test
    public void testLanguagesByCountry_knownCountry_containsExpectedLocaleAndFiltered() throws Throwable {
        List<Locale> list = LocaleUtils.languagesByCountry("US");
        assertTrue(list.contains(Locale.US));
        for (int i = 0; i < list.size(); i++) {
            Locale locale = list.get(i);
            assertEquals("US", locale.getCountry());
            assertEquals("", locale.getVariant());
        }
    }

    // null languageCode -> empty immutable list
    @Test
    public void testCountriesByLanguage_null_returnsEmptyImmutableList() throws Throwable {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertTrue(list.isEmpty());
        try {
            list.add(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // known language code -> contains matching locale, every entry filtered correctly
    @Test
    public void testCountriesByLanguage_knownLanguage_containsExpectedLocaleAndFiltered() throws Throwable {
        List<Locale> list = LocaleUtils.countriesByLanguage("en");
        assertTrue(list.contains(Locale.US));
        for (int i = 0; i < list.size(); i++) {
            Locale locale = list.get(i);
            assertEquals("en", locale.getLanguage());
            assertFalse(locale.getCountry().equals(""));
            assertEquals("", locale.getVariant());
        }
    }
}
