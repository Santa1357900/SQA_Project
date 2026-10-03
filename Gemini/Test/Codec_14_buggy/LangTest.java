package org.apache.commons.codec.language.bm;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LangTest {

    @Test
    public void testInstanceValidNameTypes() throws Throwable {
        for (NameType nameType : NameType.values()) {
            Lang lang = Lang.instance(nameType);
            assertNotNull("Lang instance for " + nameType + " should not be null", lang);
        }
    }

    @Test
    public void testLoadFromResourceValid() throws Throwable {
        Languages languages = Languages.getInstance(NameType.ASHKENAZI);
        Lang lang = Lang.loadFromResource("org/apache/commons/codec/language/bm/lang.txt", languages);
        assertNotNull("Loaded Lang should not be null", lang);
    }

    @Test
    public void testLoadFromResourceInvalidResourceName() throws Throwable {
        Languages languages = Languages.getInstance(NameType.ASHKENAZI);
        try {
            Lang.loadFromResource("non/existent/resource/path.txt", languages);
            fail("Expected IllegalStateException for missing resource");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unable to resolve required resource"));
        }
    }

    @Test
    public void testGuessLanguagesAndGuessLanguage() throws Throwable {
        Lang lang = Lang.instance(NameType.ASHKENAZI);
        assertNotNull(lang);

        Languages.LanguageSet langs = lang.guessLanguages("rosen");
        assertNotNull(langs);

        String guessed = lang.guessLanguage("rosen");
        assertNotNull(guessed);
    }

    @Test
    public void testGuessLanguagesWithEmptyOrBlankInput() throws Throwable {
        Lang lang = Lang.instance(NameType.ASHKENAZI);
        assertNotNull(lang);

        Languages.LanguageSet langsEmpty = lang.guessLanguages("");
        assertNotNull(langsEmpty);

        String guessedEmpty = lang.guessLanguage("");
        assertNotNull(guessedEmpty);

        Languages.LanguageSet langsBlank = lang.guessLanguages("   ");
        assertNotNull(langsBlank);

        String guessedBlank = lang.guessLanguage("   ");
        assertNotNull(guessedBlank);
    }

    @Test
    public void testCustomLangRuleBehaviorViaResourceLoading() throws Throwable {
        // Since lang.txt contains comments and rules, loading it exercises rules parsing.
        // Let's verify that a known name type gives expected results.
        Lang sephardicLang = Lang.instance(NameType.SEPHARDIC);
        assertNotNull(sephardicLang);
        
        Languages.LanguageSet ls = sephardicLang.guessLanguages("testword");
        assertNotNull(ls);
    }
}