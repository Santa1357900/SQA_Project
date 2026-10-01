package org.apache.commons.codec.binary;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

public class StringUtilsClaudeTest {

    // equals: both null -> true
    @Test
    public void testEquals_bothNull_true() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
    }

    // equals: cs1 == cs2 same reference -> true (short-circuit branch)
    @Test
    public void testEquals_sameReference_true() throws Throwable {
        String s = "abc";
        assertTrue(StringUtils.equals(s, s));
    }

    // equals: cs1 null, cs2 not null -> false
    @Test
    public void testEquals_firstNullSecondNotNull_false() throws Throwable {
        assertFalse(StringUtils.equals(null, "abc"));
    }

    // equals: cs1 not null, cs2 null -> false
    @Test
    public void testEquals_firstNotNullSecondNull_false() throws Throwable {
        assertFalse(StringUtils.equals("abc", null));
    }

    // equals: both String instances, equal content -> true via String.equals branch
    @Test
    public void testEquals_bothStringsEqual_true() throws Throwable {
        assertTrue(StringUtils.equals("abc", "abc"));
    }

    // equals: both String instances, different case -> false (case sensitive)
    @Test
    public void testEquals_bothStringsDifferentCase_false() throws Throwable {
        assertFalse(StringUtils.equals("abc", "ABC"));
    }

    // equals: non-String CharSequences with equal content -> true via regionMatches branch
    @Test
    public void testEquals_nonStringCharSequencesEqual_true() throws Throwable {
        StringBuilder sb1 = new StringBuilder("hello");
        StringBuilder sb2 = new StringBuilder("hello");
        assertTrue(StringUtils.equals(sb1, sb2));
    }

    // equals: non-String CharSequences with different lengths -> false
    @Test
    public void testEquals_nonStringCharSequencesDifferentLength_false() throws Throwable {
        StringBuilder sb1 = new StringBuilder("ab");
        StringBuilder sb2 = new StringBuilder("abc");
        assertFalse(StringUtils.equals(sb1, sb2));
    }

    // getByteBufferUtf8: null input -> null output
    @Test
    public void testGetByteBufferUtf8_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getByteBufferUtf8(null));
    }

    // getByteBufferUtf8: valid string -> ByteBuffer wrapping UTF-8 bytes
    @Test
    public void testGetByteBufferUtf8_validString_returnsCorrectBytes() throws Throwable {
        ByteBuffer buf = StringUtils.getByteBufferUtf8("abc");
        byte[] expected = "abc".getBytes("UTF-8");
        assertTrue(Arrays.equals(expected, buf.array()));
    }

    // getBytesIso8859_1: null input -> null output
    @Test
    public void testGetBytesIso8859_1_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesIso8859_1(null));
    }

    // getBytesIso8859_1: valid string -> correct ISO-8859-1 bytes
    @Test
    public void testGetBytesIso8859_1_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesIso8859_1("abc");
        byte[] expected = "abc".getBytes("ISO-8859-1");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUnchecked: null input -> null output
    @Test
    public void testGetBytesUnchecked_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUnchecked(null, "UTF-8"));
    }

    // getBytesUnchecked: valid charset name -> correct bytes
    @Test
    public void testGetBytesUnchecked_validCharset_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUnchecked("abc", "UTF-8");
        byte[] expected = "abc".getBytes("UTF-8");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUnchecked: invalid charset name -> IllegalStateException wrapping UnsupportedEncodingException
    @Test
    public void testGetBytesUnchecked_invalidCharsetName_throwsIllegalStateException() throws Throwable {
        try {
            StringUtils.getBytesUnchecked("abc", "INVALID-CHARSET-XYZ");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("INVALID-CHARSET-XYZ"));
        }
    }

    // getBytesUsAscii: null input -> null output
    @Test
    public void testGetBytesUsAscii_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUsAscii(null));
    }

    // getBytesUsAscii: valid string -> correct US-ASCII bytes
    @Test
    public void testGetBytesUsAscii_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUsAscii("abc");
        byte[] expected = "abc".getBytes("US-ASCII");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUtf16: null input -> null output
    @Test
    public void testGetBytesUtf16_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUtf16(null));
    }

    // getBytesUtf16: valid string -> correct UTF-16 bytes
    @Test
    public void testGetBytesUtf16_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUtf16("abc");
        byte[] expected = "abc".getBytes("UTF-16");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUtf16Be: null input -> null output
    @Test
    public void testGetBytesUtf16Be_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Be(null));
    }

    // getBytesUtf16Be: valid string -> correct UTF-16BE bytes
    @Test
    public void testGetBytesUtf16Be_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUtf16Be("abc");
        byte[] expected = "abc".getBytes("UTF-16BE");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUtf16Le: null input -> null output
    @Test
    public void testGetBytesUtf16Le_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUtf16Le(null));
    }

    // getBytesUtf16Le: valid string -> correct UTF-16LE bytes
    @Test
    public void testGetBytesUtf16Le_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUtf16Le("abc");
        byte[] expected = "abc".getBytes("UTF-16LE");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUtf8: null input -> null output
    @Test
    public void testGetBytesUtf8_null_returnsNull() throws Throwable {
        assertNull(StringUtils.getBytesUtf8(null));
    }

    // getBytesUtf8: valid string -> correct UTF-8 bytes
    @Test
    public void testGetBytesUtf8_validString_returnsCorrectBytes() throws Throwable {
        byte[] actual = StringUtils.getBytesUtf8("abc");
        byte[] expected = "abc".getBytes("UTF-8");
        assertTrue(Arrays.equals(expected, actual));
    }

    // getBytesUtf8: unicode string with multi-byte chars -> correct UTF-8 bytes
    @Test
    public void testGetBytesUtf8_unicodeString_returnsCorrectBytes() throws Throwable {
        String unicode = "h\u00e9llo";
        byte[] actual = StringUtils.getBytesUtf8(unicode);
        byte[] expected = unicode.getBytes("UTF-8");
        assertTrue(Arrays.equals(expected, actual));
    }

    // newString(byte[], String): null bytes -> null output
    @Test
    public void testNewString_nullBytes_returnsNull() throws Throwable {
        assertNull(StringUtils.newString(null, "UTF-8"));
    }

    // newString(byte[], String): valid bytes and charset -> correct decoded string
    @Test
    public void testNewString_validBytesAndCharset_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-8");
        String actual = StringUtils.newString(bytes, "UTF-8");
        assertEquals("abc", actual);
    }

    // newString(byte[], String): invalid charset name -> IllegalStateException
    @Test
    public void testNewString_invalidCharsetName_throwsIllegalStateException() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-8");
        try {
            StringUtils.newString(bytes, "INVALID-CHARSET-XYZ");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("INVALID-CHARSET-XYZ"));
        }
    }

    // newStringIso8859_1: null input -> per Javadoc must return null (catches the Codec-17 NPE bug)
    @Test
    public void testNewStringIso8859_1_nullInput_returnsNullPerJavadoc() throws Throwable {
        assertNull(StringUtils.newStringIso8859_1(null));
    }

    // newStringIso8859_1: valid bytes -> correct decoded ISO-8859-1 string
    @Test
    public void testNewStringIso8859_1_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("ISO-8859-1");
        String actual = StringUtils.newStringIso8859_1(bytes);
        assertEquals("abc", actual);
    }

    // newStringUsAscii: null input -> null output
    @Test
    public void testNewStringUsAscii_null_returnsNull() throws Throwable {
        assertNull(StringUtils.newStringUsAscii(null));
    }

    // newStringUsAscii: valid bytes -> correct decoded string
    @Test
    public void testNewStringUsAscii_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("US-ASCII");
        String actual = StringUtils.newStringUsAscii(bytes);
        assertEquals("abc", actual);
    }

    // newStringUtf16: null input -> null output
    @Test
    public void testNewStringUtf16_null_returnsNull() throws Throwable {
        assertNull(StringUtils.newStringUtf16(null));
    }

    // newStringUtf16: valid bytes -> correct decoded string
    @Test
    public void testNewStringUtf16_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-16");
        String actual = StringUtils.newStringUtf16(bytes);
        assertEquals("abc", actual);
    }

    // newStringUtf16Be: null input -> null output
    @Test
    public void testNewStringUtf16Be_null_returnsNull() throws Throwable {
        assertNull(StringUtils.newStringUtf16Be(null));
    }

    // newStringUtf16Be: valid bytes -> correct decoded string
    @Test
    public void testNewStringUtf16Be_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-16BE");
        String actual = StringUtils.newStringUtf16Be(bytes);
        assertEquals("abc", actual);
    }

    // newStringUtf16Le: null input -> null output
    @Test
    public void testNewStringUtf16Le_null_returnsNull() throws Throwable {
        assertNull(StringUtils.newStringUtf16Le(null));
    }

    // newStringUtf16Le: valid bytes -> correct decoded string
    @Test
    public void testNewStringUtf16Le_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-16LE");
        String actual = StringUtils.newStringUtf16Le(bytes);
        assertEquals("abc", actual);
    }

    // newStringUtf8: null input -> null output
    @Test
    public void testNewStringUtf8_null_returnsNull() throws Throwable {
        assertNull(StringUtils.newStringUtf8(null));
    }

    // newStringUtf8: valid bytes -> correct decoded string
    @Test
    public void testNewStringUtf8_validBytes_returnsCorrectString() throws Throwable {
        byte[] bytes = "abc".getBytes("UTF-8");
        String actual = StringUtils.newStringUtf8(bytes);
        assertEquals("abc", actual);
    }

    // newStringUtf8: unicode round trip with multi-byte chars -> correct decoded string
    @Test
    public void testNewStringUtf8_unicodeRoundTrip_returnsCorrectString() throws Throwable {
        String unicode = "h\u00e9llo\u4e2d";
        byte[] bytes = unicode.getBytes("UTF-8");
        String actual = StringUtils.newStringUtf8(bytes);
        assertEquals(unicode, actual);
    }
}
