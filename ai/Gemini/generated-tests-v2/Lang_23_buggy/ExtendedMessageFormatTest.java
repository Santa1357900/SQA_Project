package org.apache.commons.lang3.text;

import java.text.Format;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedMessageFormatTest {

    @Test
    public void testConstructorPatternOnly() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        assertNotNull(emf);
        assertEquals("Hello {0}", emf.toPattern());
    }

    @Test
    public void testConstructorPatternAndLocale() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}", Locale.US);
        assertNotNull(emf);
        assertEquals("Hello {0}", emf.toPattern());
    }

    @Test
    public void testConstructorPatternAndRegistry() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}", registry);
        assertNotNull(emf);
    }

    @Test
    public void testConstructorFullParameters() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}", Locale.US, registry);
        assertNotNull(emf);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormat() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        emf.setFormat(0, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatByArgumentIndex() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        emf.setFormatByArgumentIndex(0, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormats() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        emf.setFormats(new Format[0]);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsByArgumentIndex() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        emf.setFormatsByArgumentIndex(new Format[0]);
    }

    @Test
    public void testApplyPatternWithRegistryAndCustomFormat() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new FormatFactory() {
            public Format getFormat(String name, String arguments, Locale locale) {
                return new Format() {
                    private static final long serialVersionUID = 1L;
                    public StringBuffer format(Object obj, StringBuffer toAppendTo, java.text.FieldPosition pos) {
                        return toAppendTo.append(obj);
                    }
                    public Object parseObject(String source, ParsePosition pos) {
                        return source;
                    }
                };
            }
        });

        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0,custom}", Locale.US, registry);
        assertNotNull(emf);
        assertEquals("Hello {0,custom}", emf.toPattern());
    }

    @Test
    public void testApplyPatternWithRegistryAndNullCustomFormat() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        // registry is empty, so getFormat returns null
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0,unknown}", Locale.US, registry);
        assertNotNull(emf);
        assertEquals("Hello {0,unknown}", emf.toPattern());
    }

    @Test
    public void testApplyPatternWithQuotes() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello ''{0}'' {1}");
        assertNotNull(emf);
        assertEquals("Hello ''{0}'' {1}", emf.toPattern());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgumentIndex() throws Throwable {
        new ExtendedMessageFormat("Hello {abc}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnterminatedFormatElement() throws Throwable {
        new ExtendedMessageFormat("Hello {0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnterminatedQuotedString() throws Throwable {
        new ExtendedMessageFormat("Hello 'unclosed");
    }

    @Test
    public void testEscapedQuoteInsideQuotes() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello ''world'' {0}");
        assertNotNull(emf);
    }

    @Test
    public void testWhitespaceInFormatElement() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello { 0 }");
        assertNotNull(emf);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWhitespaceInArgumentIndex() throws Throwable {
        new ExtendedMessageFormat("Hello {0 1}");
    }

    @Test
    public void testNestedFormatDescription() throws Throwable {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("fmt", new FormatFactory() {
            public Format getFormat(String name, String arguments, Locale locale) {
                return null;
            }
        });
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,fmt,args{nested}}", registry);
        assertNotNull(emf);
    }
}