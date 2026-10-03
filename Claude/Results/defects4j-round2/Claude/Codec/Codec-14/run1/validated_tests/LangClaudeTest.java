package org.apache.commons.codec.language.bm;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LangClaudeTest {

    private static final String LANG_RESOURCE = "org/apache/commons/codec/language/bm/lang.txt";

    private NameType[] types;
    private Languages languages0;
    private Lang lang0;

    @Before
    public void setUp() throws Throwable {
        types = NameType.values();
        languages0 = Languages.getInstance(types[0]);
        lang0 = Lang.instance(types[0]);
    }

    // Covers static factory returning a cached, non-null Lang for every NameType value
    @Test
    public void testInstance_nonNullForAllNameTypes() throws Throwable {
        for (int i = 0; i < types.length; i++) {
            assertNotNull(Lang.instance(types[i]));
        }
    }

    // Covers that instance() returns the identical cached Lang object, not a new one
    @Test
    public void testInstance_sameInstanceReturnedOnRepeatedCalls() throws Throwable {
        Lang a = Lang.instance(types[0]);
        Lang b = Lang.instance(types[0]);
        assertSame(a, b);
    }

    // Covers that different NameType keys map to different Lang instances in the cache
    @Test
    public void testInstance_distinctInstancesForDifferentNameTypes() throws Throwable {
        assertTrue(types.length > 0);
        if (types.length > 1) {
            assertNotSame(Lang.instance(types[0]), Lang.instance(types[1]));
        }
    }

    // Covers successful parsing path of loadFromResource with the real packaged resource
    @Test
    public void testLoadFromResource_validResource_returnsNonNullLang() throws Throwable {
        Lang lang = Lang.loadFromResource(LANG_RESOURCE, languages0);
        assertNotNull(lang);
    }

    // Covers the lRulesIS == null branch throwing IllegalStateException
    @Test
    public void testLoadFromResource_resourceNotFound_throwsIllegalStateException() throws Throwable {
        try {
            Lang.loadFromResource("org/apache/commons/codec/language/bm/no-such-file.txt", languages0);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }





    // Covers that loadFromResource does not eagerly dereference a null languages parameter
    @Test
    public void testLoadFromResource_nullLanguages_returnsNonNullLangObject() throws Throwable {
        Lang lang = Lang.loadFromResource(LANG_RESOURCE, null);
        assertNotNull(lang);
    }

    // Covers that each loadFromResource call builds a fresh, independent Lang instance
    @Test
    public void testLoadFromResource_sameResourceDifferentCalls_producesDistinctLangObjects() throws Throwable {
        Lang a = Lang.loadFromResource(LANG_RESOURCE, languages0);
        Lang b = Lang.loadFromResource(LANG_RESOURCE, languages0);
        assertNotSame(a, b);
    }

    // Covers the normal execution path of guessLanguage with ordinary alphabetic input
    @Test
    public void testGuessLanguage_returnsNonNullForTypicalWord() throws Throwable {
        String result = lang0.guessLanguage("abcdef");
        assertNotNull(result);
    }

    // Covers guessLanguage with zero-length text, still delegates to guessLanguages safely
    @Test
    public void testGuessLanguage_emptyStringInput_returnsNonNullResult() throws Throwable {
        String result = lang0.guessLanguage("");
        assertNotNull(result);
    }

    // Covers contract: guessLanguage must return a non-null, non-empty language code or Languages.ANY
    @Test
    public void testGuessLanguage_resultIsNonEmptyLanguageNameOrAny() throws Throwable {
        String result = lang0.guessLanguage("test");
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    // Covers guessLanguage working across every supported NameType's own Lang instance
    @Test
    public void testGuessLanguage_allNameTypes_returnNonNullForSameWord() throws Throwable {
        for (int i = 0; i < types.length; i++) {
            Lang l = Lang.instance(types[i]);
            assertNotNull(l.guessLanguage("test"));
        }
    }

    // Covers input.toLowerCase(Locale.ENGLISH) normalization: case must not change the guessed result
    @Test
    public void testGuessLanguage_caseInsensitive_consistentAcrossCases() throws Throwable {
        String lower = lang0.guessLanguage("mozart");
        String upper = lang0.guessLanguage("MOZART");
        String mixed = lang0.guessLanguage("MoZaRt");
        assertEquals(lower, upper);
        assertEquals(lower, mixed);
    }

    // Covers NPE when guessLanguage delegates to guessLanguages, which calls toLowerCase on null
    @Test
    public void testGuessLanguage_nullInput_throwsNullPointerException() throws Throwable {
        try {
            lang0.guessLanguage(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // Covers both branches of: isSingleton() ? getAny() : Languages.ANY inside guessLanguage
    @Test
    public void testGuessLanguage_consistentWithGuessLanguagesTernaryLogic() throws Throwable {
        String[] words = {"abc", "xyz", "mozart", "schwarz", "test", "qwertyuiop", "a"};
        for (int i = 0; i < words.length; i++) {
            Languages.LanguageSet ls = lang0.guessLanguages(words[i]);
            String g = lang0.guessLanguage(words[i]);
            if (ls.isSingleton()) {
                assertEquals(ls.getAny(), g);
            } else {
                assertEquals(Languages.ANY, g);
            }
        }
    }

    // Covers the normal execution path of guessLanguages with ordinary alphabetic input
    @Test
    public void testGuessLanguages_returnsNonNullForTypicalWord() throws Throwable {
        Languages.LanguageSet ls = lang0.guessLanguages("abcdef");
        assertNotNull(ls);
    }

    // Covers guessLanguages across every supported NameType's own rule set and language seed
    @Test
    public void testGuessLanguages_allNameTypes_returnNonNullForSameWord() throws Throwable {
        for (int i = 0; i < types.length; i++) {
            Lang l = Lang.instance(types[i]);
            assertNotNull(l.guessLanguages("test"));
        }
    }

    // Covers rule matching over an unusually long repeated-character input string
    @Test
    public void testGuessLanguages_longRepeatedCharacterInput_returnsNonNullLanguageSet() throws Throwable {
        Languages.LanguageSet ls = lang0.guessLanguages("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertNotNull(ls);
    }



    // Covers NPE from input.toLowerCase(Locale.ENGLISH) when the input text itself is null
    @Test
    public void testGuessLanguages_nullInput_throwsNullPointerException() throws Throwable {
        try {
            lang0.guessLanguages(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // Covers zero-length input text going through the rule-matching loop with no exception
    @Test
    public void testGuessLanguages_emptyStringInput_returnsNonNullLanguageSet() throws Throwable {
        Languages.LanguageSet ls = lang0.guessLanguages("");
        assertNotNull(ls);
    }

    // Covers input that is only whitespace, exercising toLowerCase and the rule loop
    @Test
    public void testGuessLanguages_whitespaceInput_returnsNonNullLanguageSet() throws Throwable {
        Languages.LanguageSet ls = lang0.guessLanguages(" ");
        assertNotNull(ls);
    }

    // Covers regex matching over non-ASCII unicode characters in input text
    @Test
    public void testGuessLanguages_unicodeInput_returnsNonNullLanguageSet() throws Throwable {
        Languages.LanguageSet ls = lang0.guessLanguages("m\u00fcller");
        assertNotNull(ls);
    }

    // Covers the final substitution: ls.equals(NO_LANGUAGES) ? ANY_LANGUAGE : ls, so result is never NO_LANGUAGES
    @Test
    public void testGuessLanguages_resultNeverEqualsNoLanguages() throws Throwable {
        String[] words = {"abc", "xyz123", "test", "qqqqqq", ""};
        for (int i = 0; i < words.length; i++) {
            Languages.LanguageSet ls = lang0.guessLanguages(words[i]);
            assertFalse(ls.equals(Languages.NO_LANGUAGES));
        }
    }

    // Covers NPE from this.languages.getLanguages() when the Lang's languages field itself is null
    @Test
    public void testGuessLanguages_whenLanguagesIsNull_throwsNullPointerException() throws Throwable {
        Lang lang = Lang.loadFromResource(LANG_RESOURCE, null);
        try {
            lang.guessLanguages("test");
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }
}
