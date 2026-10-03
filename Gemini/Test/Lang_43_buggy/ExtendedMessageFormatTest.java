package org.apache.commons.lang.text;

import junit.framework.TestCase;
import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExtendedMessageFormatTest extends TestCase {

    public void testConstructorPatternString() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        assertNotNull(emf);
        assertEquals("Hello {0}", emf.toPattern());
    }

    public void testConstructorPatternStringLocale() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}", Locale.US);
        assertNotNull(emf);
        assertEquals(Locale.US, emf.getLocale());
    }

    public void testConstructorPatternStringMap() throws Throwable {
        Map registry = new HashMap();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}", registry);
        assertNotNull(emf);
        assertEquals("Hello {0}", emf.toPattern());
    }

    public void testConstructorPatternStringLocaleMap() throws Throwable {
        Map registry = new HashMap();
        registry.put("test", new DummyFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0,test}", Locale.US, registry);
        assertNotNull(emf);
        assertEquals("Hello {0,test}", emf.toPattern());
    }

    public void testApplyPatternWithoutRegistry() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        emf.applyPattern("Goodbye {0}");
        assertEquals("Goodbye {0}", emf.toPattern());
    }

    public void testApplyPatternWithRegistryCustomFormat() throws Throwable {
        Map registry = new HashMap();
        registry.put("custom", new DummyFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Test {0,custom,style}", registry);
        assertEquals("Test {0,custom,style}", emf.toPattern());
    }

    public void testApplyPatternWithRegistryUnknownFormat() throws Throwable {
        Map registry = new HashMap();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Test {0,unknown,style}", registry);
        assertEquals("Test {0,unknown,style}", emf.toPattern());
    }

    public void testApplyPatternWithQuotedString() throws Throwable {
        Map registry = new HashMap();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Test ''quoted'' {0}", registry);
        assertTrue(emf.toPattern().indexOf("Test") >= 0);
    }

    public void testSetFormatUnsupported() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        try {
            emf.setFormat(0, new DummyFormat());
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testSetFormatByArgumentIndexUnsupported() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        try {
            emf.setFormatByArgumentIndex(0, new DummyFormat());
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testSetFormatsUnsupported() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        try {
            emf.setFormats(new Format[0]);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testSetFormatsByArgumentIndexUnsupported() throws Throwable {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        try {
            emf.setFormatsByArgumentIndex(new Format[0]);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testInvalidFormatArgumentIndex() throws Throwable {
        Map registry = new HashMap();
        try {
            new ExtendedMessageFormat("Hello {abc,custom}", registry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testUnterminatedFormatElement() throws Throwable {
        Map registry = new HashMap();
        try {
            new ExtendedMessageFormat("Hello {0", registry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testUnreadableFormatElement() throws Throwable {
        Map registry = new HashMap();
        try {
            new ExtendedMessageFormat("Hello {0,,}", registry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testUnterminatedQuotedString() throws Throwable {
        Map registry = new HashMap();
        try {
            new ExtendedMessageFormat("Hello 'unclosed", registry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testWhitespaceInArgumentIndex() throws Throwable {
        Map registry = new HashMap();
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello { 0 }", registry);
        assertEquals("Hello {0}", emf.toPattern());
    }

    public void testNestedFormatElements() throws Throwable {
        Map registry = new HashMap();
        registry.put("custom", new DummyFormatFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0,custom,{nested}}", registry);
        assertNotNull(emf);
    }

    private static class DummyFormatFactory implements FormatFactory {
        public Format getFormat(String name, String arguments, Locale locale) {
            return new DummyFormat();
        }
    }

    private static class DummyFormat extends Format {
        public StringBuffer format(Object obj, StringBuffer toAppendTo, ParsePosition pos) {
            return toAppendTo.append(obj);
        }
        public Object parseObject(String source, ParsePosition pos) {
            return source;
        }
    }
}