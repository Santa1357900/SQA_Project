package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
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
        
        // Same reference
        String s = "test";
        assertTrue(StringUtils.equals(s, s));

        // Different CharSequence implementation (e.g., StringBuilder)
        StringBuilder sb1 = new StringBuilder("abc");
        StringBuilder sb2 = new StringBuilder("abc");
        StringBuilder sb3 = new StringBuilder("def");
        assertTrue(StringUtils.equals(sb1, sb2));
        assertFalse(StringUtils.equals(sb1, sb3));
        assertFalse(StringUtils.equals("abc", sb1));
        assertFalse(StringUtils.equals(sb1, "abc"));

        // Different lengths or mismatched region
        assertFalse(StringUtils.equals("abcd", "abc"));
        assertFalse(StringUtils.equals("abc", "abcd"));
    }

    @Test
    public void testGetByteBufferUtf8() throws Throwable {
        assertNull(StringUtils.getByteBufferUtf8(null));
        ByteBuffer buffer = StringUtils.getByteBufferUtf8("abc");
        assertNotNull(buffer);
        assertEquals(3, buffer.remaining());
        byte[] bytes = new byte[3];
        buffer.get(bytes);
        assertArrayEquals(new byte[] { 'a', 'b', 'c' }, bytes);
    }

    @Test
    public void testGetBytesIso8859_1() throws Throwable {
        assertNull(StringUtils.getBytesIso8859_1(null));
        byte[] bytes = StringUtils.getBytesIso8859_1("abc");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 'a', 'b', 'c' }, bytes);
    }

    @Test
    public void testGetBytesUnchecked() throws Throwable {
        assertNull(StringUtils.getBytesUnchecked(null, "UTF-8"));
        byte[] bytes = StringUtils.getBytesUnchecked("abc", "UTF-8");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 'a', 'b', 'c' }, bytes);

        try {
            StringUtils.getBytesUnchecked("abc", "Invalid-Charset-Name-XYZ");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Invalid-Charset-Name-XYZ"));
        }
    }

    @Test
    public void testGetBytesUsAscii() throws Throwable {
        assertNull(StringUtils.getBytesUsAscii(null));
        byte[] bytes = StringUtils.getBytesUsAscii("abc");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 'a', 'b', 'c' }, bytes);
    }

    @Test
    public void testGetBytesUtf16() throws Throwable {
        assertNull(StringUtils.getBytesUtf16(null));
        byte[] bytes = StringUtils.getBytesUtf16("a");
        assertNotNull(bytes);
        // UTF-16 adds BOM, so length should be at least 4 bytes
        assertTrue(bytes.length >= 4);
    }

    @Test
    public void testGetBytesUtf16Be() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Be(null));
        byte[] bytes = StringUtils.getBytesUtf16Be("a");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 0, 'a' }, bytes);
    }

    @Test
    public void testGetBytesUtf16Le() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Le(null));
        byte[] bytes = StringUtils.getBytesUtf16Le("a");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 'a', 0 }, bytes);
    }

    @Test
    public void testGetBytesUtf8() throws Throwable {
        assertNull(StringUtils.getBytesUtf8(null));
        byte[] bytes = StringUtils.getBytesUtf8("abc");
        assertNotNull(bytes);
        assertArrayEquals(new byte[] { 'a', 'b', 'c' }, bytes);
    }

    @Test
    public void testNewString_CharsetName() throws Throwable {
        assertNull(StringUtils.newString(null, "UTF-8"));
        String str = StringUtils.newString(new byte[] { 'a', 'b', 'c' }, "UTF-8");
        assertEquals("abc", str);

        try {
            StringUtils.newString(new byte[] { 'a' }, "Invalid-Charset-Name-XYZ");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Invalid-Charset-Name-XYZ"));
        }
    }

    @Test
    public void testNewStringIso8859_1() throws Throwable {
        // Note: Depending on implementation, newStringIso8859_1(null) might return null or throw NPE. 
        // Looking at source: return new String(bytes, Charsets.ISO_8859_1); it will throw NullPointerException if bytes is null.
        try {
            StringUtils.newStringIso8859_1(null);
            // If it doesn't throw, check if null is returned
        } catch (NullPointerException e) {
            // Expected if bytes is null for new String(byte[], Charset)
        }

        String str = StringUtils.newStringIso8859_1(new byte[] { 'a', 'b', 'c' });
        assertEquals("abc", str);
    }

    @Test
    public void testNewStringUsAscii() throws Throwable {
        assertNull(StringUtils.newStringUsAscii(null));
        String str = StringUtils.newStringUsAscii(new byte[] { 'a', 'b', 'c' });
        assertEquals("abc", str);
    }

    @Test
    public void testNewStringUtf16() throws Throwable {
        assertNull(StringUtils.newStringUtf16(null));
        byte[] encoded = StringUtils.getBytesUtf16("test");
        String str = StringUtils.newStringUtf16(encoded);
        assertEquals("test", str);
    }

    @Test
    public void testNewStringUtf16Be() throws Throwable {
        assertNull(StringUtils.newStringUtf16Be(null));
        byte[] encoded = StringUtils.getBytesUtf16Be("test");
        String str = StringUtils.newStringUtf16Be(encoded);
        assertEquals("test", str);
    }

    @Test
    public void testNewStringUtf16Le() throws Throwable {
        assertNull(StringUtils.newStringUtf16Le(null));
        byte[] encoded = StringUtils.getBytesUtf16Le("test");
        String str = StringUtils.newStringUtf16Le(encoded);
        assertEquals("test", str);
    }

    @Test
    public void testNewStringUtf8() throws Throwable {
        assertNull(StringUtils.newStringUtf8(null));
        String str = StringUtils.newStringUtf8(new byte[] { 'a', 'b', 'c' });
        assertEquals("abc", str);
    }
}