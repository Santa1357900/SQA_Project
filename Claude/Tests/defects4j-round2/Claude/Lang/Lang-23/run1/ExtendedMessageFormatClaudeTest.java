package org.apache.commons.lang3.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedMessageFormatClaudeTest {

    private static class CustomFormatFactory implements FormatFactory {
        public Format getFormat(String name, String args, Locale locale) {
            if ("custom".equals(name)) {
                DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                if (args != null && args.length() > 0) {
                    return new DecimalFormat(args, symbols);
                }
                return new DecimalFormat("0.00", symbols);
            }
            return null;
        }
    }

    // constructor(pattern) default locale, plain text + argument substitution works
    @Test
    public void testConstructor_patternOnly_formatsPlainText() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}!");
        assertEquals("Hello World!", emf.format(new Object[] { "World" }));
    }

    // constructor(pattern, locale) stores the given locale
    @Test
    public void testConstructor_patternAndLocale_storesLocale() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("x", Locale.US);
        assertEquals(Locale.US, emf.getLocale());
    }

    // constructor(pattern, registry) with no custom format name used in pattern
    @Test
    public void testConstructor_patternAndRegistry_noCustomFormat_formatsPlainText() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hi {0}", registry);
        assertEquals("Hi there", emf.format(new Object[] { "there" }));
    }

    // constructor(pattern, locale, registry) all args combined, custom format applied
    @Test
    public void testConstructor_patternLocaleRegistry_allArgsUsed() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,custom}", Locale.US, registry);
        assertEquals("3.14", emf.format(new Object[] { Double.valueOf(3.14) }));
    }

    // applyPattern(null) with registry==null delegates to super, null String causes NPE
    @Test
    public void testConstructor_nullPattern_registryNull_throwsNullPointerException() throws Throwable {
        try {
            new ExtendedMessageFormat((String) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // applyPattern(null) with registry!=null: pattern.length() in while-loop NPEs
    @Test
    public void testConstructor_nullPattern_registryNonNull_throwsNullPointerException() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        try {
            new ExtendedMessageFormat(null, registry);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // toPattern(): registry==null branch returns super's reconstructed pattern
    @Test
    public void testToPattern_simplePatternNoRegistry_returnsSamePattern() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        assertEquals("{0}", emf.toPattern());
    }

    // toPattern(): custom format found, full "name,style" description round-trips
    @Test
    public void testToPattern_customFormatRegistered_roundTripsFullDescription() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,custom,0.00}", registry);
        assertEquals("{0,custom,0.00}", emf.toPattern());
    }

    // toPattern(): custom format style arguments containing a comma round-trip correctly
    @Test
    public void testToPattern_customFormatFactoryArgsIncluded_roundTrips() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,custom,#,##0.00}", registry);
        assertEquals("{0,custom,#,##0.00}", emf.toPattern());
    }

    // BUG: a quoted literal brace "'{'" must stay literal text per MessageFormat quoting
    // rules and must not be misread as the start of a format element when registry != null
    @Test
    public void testApplyPattern_quotedBraceWithRegistry_doesNotThrow() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("'{'", Locale.US, registry);
        assertEquals("{", emf.format(new Object[0]));
    }

    // quoted literal text (no braces inside) is preserved literally with registry set
    @Test
    public void testApplyPattern_quotedLiteralTextWithRegistry_formatsLiteral() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("'XYZ'", registry);
        assertEquals("XYZ", emf.format(new Object[0]));
    }

    // escaped quote '' inside pattern produces a single literal quote character
    @Test
    public void testApplyPattern_escapedQuoteWithRegistry_producesLiteralQuote() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("it''s fine", registry);
        assertEquals("it's fine", emf.format(new Object[0]));
    }

    // readArgumentIndex: non-digit argument index throws IllegalArgumentException
    @Test
    public void testApplyPattern_invalidArgumentIndex_throwsIllegalArgumentException() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        try {
            new ExtendedMessageFormat("{a}", registry);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("argument index"));
        }
    }

    // readArgumentIndex: pattern ends before closing brace throws "Unterminated"
    @Test
    public void testApplyPattern_unterminatedFormatElement_throwsIllegalArgumentException() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        try {
            new ExtendedMessageFormat("{0", registry);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unterminated"));
        }
    }

    // registry==null branch delegates straight to MessageFormat; unknown type throws
    @Test
    public void testApplyPattern_unknownFormatType_registryNull_throwsIllegalArgumentException() throws Throwable {
        try {
            new ExtendedMessageFormat("{0,bogus}");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // format name not present in (non-null, empty) registry falls back to standard "number"
    @Test
    public void testApplyPattern_customFormatNotInRegistry_fallsBackToStandardNumberFormat() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,number}", Locale.US, registry);
        assertEquals("1,234", emf.format(new Object[] { Integer.valueOf(1234) }));
    }

    // readArgumentIndex: whitespace around the argument index is skipped correctly
    @Test
    public void testApplyPattern_whitespaceAroundArgumentIndex_parsedCorrectly() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{ 0 }", registry);
        assertEquals("X", emf.format(new Object[] { "X" }));
    }

    // two format elements: plain {0} and custom {1,custom} map to correct argument indices
    @Test
    public void testApplyPattern_multipleFormatElements_customAppliedToCorrectArgument() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0} - {1,custom}", Locale.US, registry);
        String result = emf.format(new Object[] { "Name", Double.valueOf(2.5) });
        assertEquals("Name - 2.50", result);
    }

    // calling applyPattern again on the same instance updates toPattern()/behavior
    @Test
    public void testApplyPattern_reapplyChangesToPattern() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}", registry);
        assertEquals("{0}", emf.toPattern());
        emf.applyPattern("{0,custom}");
        assertEquals("{0,custom}", emf.toPattern());
    }

    // setFormat(int, Format) is disabled and must always throw
    @Test
    public void testSetFormat_throwsUnsupportedOperationException() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormat(0, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // setFormatByArgumentIndex(int, Format) is disabled and must always throw
    @Test
    public void testSetFormatByArgumentIndex_throwsUnsupportedOperationException() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormatByArgumentIndex(0, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // setFormats(Format[]) is disabled and must always throw
    @Test
    public void testSetFormats_throwsUnsupportedOperationException() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormats(new Format[0]);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // setFormatsByArgumentIndex(Format[]) is disabled and must always throw
    @Test
    public void testSetFormatsByArgumentIndex_throwsUnsupportedOperationException() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormatsByArgumentIndex(new Format[0]);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // equals() general contract: an object must equal itself
    @Test
    public void testEquals_reflexive_returnsTrue() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        assertTrue(emf.equals(emf));
    }

    // equals() general contract: comparison with null returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        assertFalse(emf.equals(null));
    }

    // equals() general contract: comparison with unrelated type returns false
    @Test
    public void testEquals_differentType_returnsFalse() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        assertFalse(emf.equals("not a format"));
    }

    // hashCode() general contract: multiple calls on the same object are consistent
    @Test
    public void testHashCode_sameInstance_consistent() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        assertEquals(emf.hashCode(), emf.hashCode());
    }

    // getFormats(): custom format from the registry is stored at the matching argument index
    @Test
    public void testGetFormats_customFormatStoredAtArgumentIndex() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,custom}", registry);
        Format[] formats = emf.getFormats();
        assertTrue(formats[0] instanceof DecimalFormat);
        assertEquals("5.00", formats[0].format(Double.valueOf(5)));
    }

    // FormatFactory style args (containing a comma) are passed through and used for formatting
    @Test
    public void testFormat_argsWithFactoryUsingStyleArgs_formatsAccordingly() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,custom,#,##0.00}", Locale.US, registry);
        assertEquals("1,234.50", emf.format(new Object[] { Double.valueOf(1234.5) }));
    }
}
