package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.FixedMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixedMethodOrder(MethodSorters.NAME_ASCENDING)
public class LocaleUtilsClaudeTest {

    // NOTE: method name is prefixed with "AAAA" so that, under NAME_ASCENDING
    // ordering, this test runs before any other test in this class that might
    // touch the lazily-initialized availableLocaleSet (via availableLocaleSet()
    // or isAvailableLocale()). This is required to reliably expose the bug
    // where isAvailableLocale() accesses the cache field directly instead of
    // going through availableLocaleSet(), which can throw NullPointerException
    // before the cache has ever been populated.
    // Covers: isAvailableLocale must never throw and must honor its contract.
    @Test
    public void testAAAAIsAvailableLocale_usLocale_noNPEReturnsTrue() throws Throwable {
        boolean result = LocaleUtils.isAvailableLocale(Locale.US);
        assertTrue(result);
    }

    // Covers: availableLocaleList returns a non-empty list containing a known locale.
    @Test
    public void testAvailableLocaleList_returnsNonEmptyListContainingUSLocale() throws Throwable {
        List list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.contains(Locale.US));
    }

    // Covers: availableLocaleList result must be unmodifiable.
    @Test
    public void testAvailableLocaleList_isUnmodifiable_throwsUnsupportedOperationException() throws Throwable {
        List list = LocaleUtils.availableLocaleList();
        try {
            list.add(new Locale("xx", "YY"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Covers: availableLocaleSet returns a non-empty set containing a known locale.
    @Test
    public void testAvailableLocaleSet_returnsNonEmptySetContainingUSLocale() throws Throwable {
        Set set = LocaleUtils.availableLocaleSet();
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertTrue(set.contains(Locale.US));
    }

    // Covers: availableLocaleSet result must be unmodifiable.
    @Test
    public void testAvailableLocaleSet_isUnmodifiable_throwsUnsupportedOperationException() throws Throwable {
        Set set = LocaleUtils.availableLocaleSet();
        try {
            set.add(new Locale("xx", "YY"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Covers: countriesByLanguage(null) -> empty list branch.
    @Test
    public void testCountriesByLanguage_nullLanguageCode_returnsEmptyList() throws Throwable {
        List result = LocaleUtils.countriesByLanguage(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // Covers: countriesByLanguage loop + filter conditions (language match, non-empty country, empty variant).
    @Test
    public void testCountriesByLanguage_en_allElementsHaveLanguageEnAndNonEmptyCountry() throws Throwable {
        List result = LocaleUtils.countriesByLanguage("en");
        assertNotNull(result);
        for (int i = 0; i < result.size(); i++) {
            Locale loc = (Locale) result.get(i);
            assertEquals("en", loc.getLanguage());
            assertTrue(loc.getCountry().length() != 0);
            assertTrue(loc.getVariant().length() == 0);
        }
    }

    // Covers: caching behaviour of countriesByLanguage (same key returns same cached instance).
    @Test
    public void testCountriesByLanguage_sameLanguageCodeTwice_returnsCachedSameInstance() throws Throwable {
        List first = LocaleUtils.countriesByLanguage("fr");
        List second = LocaleUtils.countriesByLanguage("fr");
        assertSame(first, second);
    }

    // Covers: isAvailableLocale with a locale that is not a known available locale.
    @Test
    public void testIsAvailableLocale_fakeLocale_returnsFalse() throws Throwable {
        Locale fake = new Locale("xx", "YY", "zzzzz");
        assertFalse(LocaleUtils.isAvailableLocale(fake));
    }

    // Covers: isAvailableLocale with null locale (HashSet.contains(null) is legal, no NPE).
    @Test
    public void testIsAvailableLocale_nullLocale_returnsFalse() throws Throwable {
        assertFalse(LocaleUtils.isAvailableLocale(null));
    }

    // Covers: languagesByCountry(null) -> empty list branch.
    @Test
    public void testLanguagesByCountry_nullCountryCode_returnsEmptyList() throws Throwable {
        List result = LocaleUtils.languagesByCountry(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // Covers: languagesByCountry loop + filter conditions (country match, empty variant).
    @Test
    public void testLanguagesByCountry_US_allElementsHaveCountryUSAndEmptyVariant() throws Throwable {
        List result = LocaleUtils.languagesByCountry("US");
        assertNotNull(result);
        for (int i = 0; i < result.size(); i++) {
            Locale loc = (Locale) result.get(i);
            assertEquals("US", loc.getCountry());
            assertTrue(loc.getVariant().length() == 0);
        }
    }

    // Covers: caching behaviour of languagesByCountry (same key returns same cached instance).
    @Test
    public void testLanguagesByCountry_sameCountryCodeTwice_returnsCachedSameInstance() throws Throwable {
        List first = LocaleUtils.languagesByCountry("US");
        List second = LocaleUtils.languagesByCountry("US");
        assertSame(first, second);
    }

    // Covers: localeLookupList(Locale) with no country/variant -> single-element list.
    @Test
    public void testLocaleLookupList_oneArgNoCountryNoVariant_returnsSingleLocale() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("en"));
        List expected = new ArrayList();
        expected.add(new Locale("en"));
        assertEquals(expected, result);
    }

    // Covers: localeLookupList(Locale) with country only, no duplicate with defaultLocale (which equals locale).
    @Test
    public void testLocaleLookupList_oneArgWithCountryOnly_returnsTwoLocalesNoDuplicate() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("en", "GB"));
        List expected = new ArrayList();
        expected.add(new Locale("en", "GB"));
        expected.add(new Locale("en", ""));
        assertEquals(expected, result);
    }

    // Covers: localeLookupList(Locale) with variant -> exact javadoc example.
    @Test
    public void testLocaleLookupList_oneArgWithVariant_matchesJavadocExample() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("fr", "CA", "xxx"));
        List expected = new ArrayList();
        expected.add(new Locale("fr", "CA", "xxx"));
        expected.add(new Locale("fr", "CA"));
        expected.add(new Locale("fr"));
        assertEquals(expected, result);
    }

    // Covers: localeLookupList(Locale) result must be unmodifiable.
    @Test
    public void testLocaleLookupList_oneArgResultIsUnmodifiable() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("en"));
        try {
            result.add(new Locale("fr"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Covers: localeLookupList(Locale, Locale) with null locale -> empty list regardless of defaultLocale.
    @Test
    public void testLocaleLookupList_twoArgNullLocale_returnsEmptyListRegardlessOfDefault() throws Throwable {
        List result = LocaleUtils.localeLookupList(null, new Locale("en"));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // Covers: localeLookupList(Locale, Locale) -> exact javadoc example with default locale appended.
    @Test
    public void testLocaleLookupList_twoArgMatchesJavadocExample() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("fr", "CA", "xxx"), new Locale("en"));
        List expected = new ArrayList();
        expected.add(new Locale("fr", "CA", "xxx"));
        expected.add(new Locale("fr", "CA"));
        expected.add(new Locale("fr"));
        expected.add(new Locale("en"));
        assertEquals(expected, result);
    }

    // Covers: localeLookupList(Locale, Locale) where defaultLocale already present -> not duplicated.
    @Test
    public void testLocaleLookupList_twoArgDefaultAlreadyPresent_noDuplicate() throws Throwable {
        List result = LocaleUtils.localeLookupList(new Locale("fr", "CA"), new Locale("fr"));
        List expected = new ArrayList();
        expected.add(new Locale("fr", "CA"));
        expected.add(new Locale("fr"));
        assertEquals(expected, result);
    }

    // Covers: toLocale(null) -> null input returns null.
    @Test
    public void testToLocale_nullInput_returnsNull() throws Throwable {
        assertNull(LocaleUtils.toLocale(null));
    }

    // Covers: toLocale length==2 branch.
    @Test
    public void testToLocale_twoCharLowercase_returnsLanguageOnlyLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en");
        assertEquals(new Locale("en", ""), result);
    }

    // Covers: toLocale length==5 branch.
    @Test
    public void testToLocale_fiveCharsLangCountry_returnsLocale() throws Throwable {
        Locale result = LocaleUtils.toLocale("en_GB");
        assertEquals(new Locale("en", "GB"), result);
    }

    // Covers: toLocale length>=7 variant branch -> exact javadoc example.
    @Test
    public void testToLocale_sevenPlusCharsWithVariant_matchesJavadocExample() throws Throwable {
        Locale result = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals(new Locale("en", "GB", "xxx"), result);
    }

    // Covers: toLocale invalid length 0 -> throws.
    @Test
    public void testToLocale_emptyString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale invalid length 1 -> throws.
    @Test
    public void testToLocale_lengthOneString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("e");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale invalid length 3 -> throws.
    @Test
    public void testToLocale_lengthThreeString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale invalid length 4 -> throws.
    @Test
    public void testToLocale_lengthFourString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_G");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale invalid length 6 -> throws (boundary just below minimum valid variant length 7).
    @Test
    public void testToLocale_lengthSixString_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_GBx");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale uppercase first two chars -> throws (language must be lowercase).
    @Test
    public void testToLocale_uppercaseLanguage_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("EN");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale missing underscore at index 2 (len==5 path) -> throws.
    @Test
    public void testToLocale_missingFirstUnderscore_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("enXGB");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale lowercase country code -> throws (country must be uppercase).
    @Test
    public void testToLocale_lowercaseCountry_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_gb");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: toLocale missing underscore at index 5 (len>=7 variant path) -> throws.
    @Test
    public void testToLocale_missingSecondUnderscore_throwsIllegalArgumentException() throws Throwable {
        try {
            LocaleUtils.toLocale("en_GBxx");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
