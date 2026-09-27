package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;

import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        StringUtils utils = new StringUtils();
        assertNotNull(utils);
    }

    @Test
    public void testGetBytesIso8859_1() throws Throwable {
        assertNull(StringUtils.getBytesIso8859_1(null));
        byte[] encoded = StringUtils.getBytesIso8859_1("abc");
        assertNotNull(encoded);
        assertArrayEquals(new byte[] { 97, 98, 99 }, encoded);
    }

    @Test
    public void testGetBytesUsAscii() throws Throwable {
        assertNull(StringUtils.getBytesUsAscii(null));
        byte[] encoded = StringUtils.getBytesUsAscii("abc");
        assertNotNull(encoded);
        assertArrayEquals(new byte[] { 97, 98, 99 }, encoded);
    }

    @Test
    public void testGetBytesUtf16() throws Throwable {
        assertNull(StringUtils.getBytesUtf16(null));
        byte[] encoded = StringUtils.getBytesUtf16("a");
        assertNotNull(encoded);
    }

    @Test
    public void testGetBytesUtf16Be() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Be(null));
        byte[] encoded = StringUtils.getBytesUtf16Be("a");
        assertNotNull(encoded);
    }

    @Test
    public void testGetBytesUtf16Le() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Le(null));
        byte[] encoded = StringUtils.getBytesUtf16Le("a");
        assertNotNull(encoded);
    }

    @Test
    public void testGetBytesUtf8() throws Throwable {
        assertNull(StringUtils.getBytesUtf8(null));
        byte[] encoded = StringUtils.getBytesUtf8("abc");
        assertNotNull(encoded);
        assertArrayEquals(new byte[] { 97, 98, 99 }, encoded);
    }

    @Test
    public void testGetBytesUncheckedValid() throws Throwable {
        assertNull(StringUtils.getBytesUnchecked(null, "UTF-8"));
        byte[] encoded = StringUtils.getBytesUnchecked("abc", "UTF-8");
        assertNotNull(encoded);
        assertArrayEquals(new byte[] { 97, 98, 99 }, encoded);
    }

    @Test
    public void testGetBytesUncheckedInvalid() throws Throwable {
        try {
            StringUtils.getBytesUnchecked("abc", "NonExistentCharset");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNewStringIso8859_1() throws Throwable {
        byte[] bytes = new byte[] { 97, 98, 99 };
        assertEquals("abc", StringUtils.newStringIso8859_1(bytes));
    }

    @Test
    public void testNewStringUsAscii() throws Throwable {
        byte[] bytes = new byte[] { 97, 98, 99 };
        assertEquals("abc", StringUtils.newStringUsAscii(bytes));
    }

    @Test
    public void testNewStringUtf16() throws Throwable {
        byte[] bytes = StringUtils.getBytesUtf16("abc");
        assertEquals("abc", StringUtils.newStringUtf16(bytes));
    }

    @Test
    public void testNewStringUtf16Be() throws Throwable {
        byte[] bytes = StringUtils.getBytesUtf16Be("abc");
        assertEquals("abc", StringUtils.newStringUtf16Be(bytes));
    }

    @Test
    public void testNewStringUtf16Le() throws Throwable {
        byte[] bytes = StringUtils.getBytesUtf16Le("abc");
        assertEquals("abc", StringUtils.newStringUtf16Le(bytes));
    }

    @Test
    public void testNewStringUtf8() throws Throwable {
        byte[] bytes = new byte[] { 97, 98, 99 };
        assertEquals("abc", StringUtils.newStringUtf8(bytes));
    }

    @Test
    public void testNewStringValidCharsetName() throws Throwable {
        assertNull(StringUtils.newString(null, "UTF-8"));
        byte[] bytes = new byte[] { 97, 98, 99 };
        assertEquals("abc", StringUtils.newString(bytes, "UTF-8"));
    }

    @Test
    public void testNewStringInvalidCharsetName() throws Throwable {
        byte[] bytes = new byte[] { 97, 98, 99 };
        try {
            StringUtils.newString(bytes, "NonExistentCharset");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }
    }
}