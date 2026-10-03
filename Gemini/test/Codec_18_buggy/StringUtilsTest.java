package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void testEquals_CharSequences() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", null));
        assertTrue(StringUtils.equals("abc", "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertFalse(StringUtils.equals("abc", "abcd"));

        StringBuilder sb1 = new StringBuilder("abc");
        StringBuilder sb2 = new StringBuilder("abc");
        StringBuffer sf1 = new StringBuffer("abc");

        assertTrue(StringUtils.equals(sb1, sb2));
        assertTrue(StringUtils.equals(sb1, sf1));
        assertTrue(StringUtils.equals("abc", sb1));
        assertTrue(StringUtils.equals(sb1, "abc"));
        assertFalse(StringUtils.equals(sb1, "abd"));
    }

    @Test
    public void testGetByteBufferUtf8() throws Throwable {
        assertNull(StringUtils.getByteBufferUtf8(null));
        ByteBuffer buffer = StringUtils.getByteBufferUtf8("abc");
        assertNotNull(buffer);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        assertArrayEquals(new byte[] { 97, 98, 99 }, bytes);
    }

    @Test
    public void testGetBytesIso8859_1() throws Throwable {
        assertNull(StringUtils.getBytesIso8859_1(null));
        assertArrayEquals(new byte[] { 97, 98, 99 }, StringUtils.getBytesIso8859_1("abc"));
    }

    @Test
    public void testGetBytesUnchecked() throws Throwable {
        assertNull(StringUtils.getBytesUnchecked(null, "UTF-8"));
        assertArrayEquals(new byte[] { 97, 98, 99 }, StringUtils.getBytesUnchecked("abc", "UTF-8"));

        try {
            StringUtils.getBytesUnchecked("abc", "InvalidCharsetName");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("InvalidCharsetName"));
        }
    }

    @Test
    public void testGetBytesUsAscii() throws Throwable {
        assertNull(StringUtils.getBytesUsAscii(null));
        assertArrayEquals(new byte[] { 97, 98, 99 }, StringUtils.getBytesUsAscii("abc"));
    }

    @Test
    public void testGetBytesUtf16() throws Throwable {
        assertNull(StringUtils.getBytesUtf16(null));
        byte[] encoded = StringUtils.getBytesUtf16("abc");
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    public void testGetBytesUtf16Be() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Be(null));
        byte[] encoded = StringUtils.getBytesUtf16Be("abc");
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    public void testGetBytesUtf16Le() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Le(null));
        byte[] encoded = StringUtils.getBytesUtf16Le("abc");
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    public void testGetBytesUtf8() throws Throwable {
        assertNull(StringUtils.getBytesUtf8(null));
        assertArrayEquals(new byte[] { 97, 98, 99 }, StringUtils.getBytesUtf8("abc"));
    }

    @Test
    public void testNewString_CharsetName() throws Throwable {
        assertNull(StringUtils.newString(null, "UTF-8"));
        byte[] bytes = new byte[] { 97, 98, 99 };
        assertEquals("abc", StringUtils.newString(bytes, "UTF-8"));

        try {
            StringUtils.newString(bytes, "InvalidCharsetName");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("InvalidCharsetName"));
        }
    }

    @Test
    public void testNewStringIso8859_1() throws Throwable {
        assertNull(StringUtils.newStringIso8859_1(null));
        assertEquals("abc", StringUtils.newStringIso8859_1(new byte[] { 97, 98, 99 }));
    }

    @Test
    public void testNewStringUsAscii() throws Throwable {
        assertNull(StringUtils.newStringUsAscii(null));
        assertEquals("abc", StringUtils.newStringUsAscii(new byte[] { 97, 98, 99 }));
    }

    @Test
    public void testNewStringUtf16() throws Throwable {
        assertNull(StringUtils.newStringUtf16(null));
        byte[] encoded = StringUtils.getBytesUtf16("abc");
        assertEquals("abc", StringUtils.newStringUtf16(encoded));
    }

    @Test
    public void testNewStringUtf16Be() throws Throwable {
        assertNull(StringUtils.newStringUtf16Be(null));
        byte[] encoded = StringUtils.getBytesUtf16Be("abc");
        assertEquals("abc", StringUtils.newStringUtf16Be(encoded));
    }

    @Test
    public void testNewStringUtf16Le() throws Throwable {
        assertNull(StringUtils.newStringUtf16Le(null));
        byte[] encoded = StringUtils.getBytesUtf16Le("abc");
        assertEquals("abc", StringUtils.newStringUtf16Le(encoded));
    }

    @Test
    public void testNewStringUtf8() throws Throwable {
        assertNull(StringUtils.newStringUtf8(null));
        assertEquals("abc", StringUtils.newStringUtf8(new byte[] { 97, 98, 99 }));
    }

    @Test
    public void testConstructor() throws Throwable {
        StringUtils utils = new StringUtils();
        assertNotNull(utils);
    }
}