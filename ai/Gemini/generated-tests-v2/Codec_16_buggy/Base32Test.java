package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class Base32Test {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Base32 base32 = new Base32();
        assertNotNull(base32);
        
        byte[] input = "Hello World".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        
        byte[] decoded = base32.decode(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testPadByteConstructor() throws Throwable {
        byte customPad = '.';
        Base32 base32 = new Base32(customPad);
        byte[] input = "Test".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        boolean hasPad = false;
        for (byte b : encoded) {
            if (b == customPad) {
                hasPad = true;
                break;
            }
        }
        assertTrue(hasPad);
        byte[] decoded = base32.decode(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testUseHexConstructor() throws Throwable {
        Base32 base32Hex = new Base32(true);
        Base32 base32Std = new Base32(false);
        
        byte[] input = "Binary Data".getBytes("UTF-8");
        byte[] encodedHex = base32Hex.encode(input);
        byte[] encodedStd = base32Std.encode(input);
        
        // They should encode differently because of different tables
        boolean different = false;
        if (encodedHex.length == encodedStd.length) {
            for (int i = 0; i < encodedHex.length; i++) {
                if (encodedHex[i] != encodedStd[i]) {
                    different = true;
                    break;
                }
            }
        } else {
            different = true;
        }
        assertTrue(different);
        
        assertArrayEquals(input, base32Hex.decode(encodedHex));
    }

    @Test
    public void testUseHexAndPadConstructor() throws Throwable {
        byte customPad = '_';
        Base32 base32 = new Base32(true, customPad);
        byte[] input = "ABC".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        assertArrayEquals(input, base32.decode(encoded));
    }

    @Test
    public void testLineLengthConstructor() throws Throwable {
        Base32 base32 = new Base32(16);
        byte[] input = "Longer string to test line wrapping capabilities of Base32 codec.".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        assertArrayEquals(input, base32.decode(encoded));
    }

    @Test
    public void testLineLengthAndSeparatorConstructor() throws Throwable {
        byte[] separator = new byte[] { '\n' };
        Base32 base32 = new Base32(8, separator);
        byte[] input = "1234567890".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        assertArrayEquals(input, base32.decode(encoded));
    }

    @Test
    public void testLineLengthSeparatorUseHexConstructor() throws Throwable {
        byte[] separator = new byte[] { '#', '#' };
        Base32 base32 = new Base32(16, separator, true);
        byte[] input = "Test Hex with Separator".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        assertArrayEquals(input, base32.decode(encoded));
    }

    @Test
    public void testFullConstructorAllParams() throws Throwable {
        byte[] separator = new byte[] { '\r', '\n' };
        byte pad = '=';
        Base32 base32 = new Base32(32, separator, false, pad);
        byte[] input = "Comprehensive test case.".getBytes("UTF-8");
        byte[] encoded = base32.encode(input);
        assertNotNull(encoded);
        assertArrayEquals(input, base32.decode(encoded));
    }

    @Test
    public void testInvalidLineLengthWithNullSeparator() throws Throwable {
        try {
            new Base32(10, null, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lineSeparator is null"));
        }
    }

    @Test
    public void testInvalidLineSeparatorContainsAlphabet() throws Throwable {
        try {
            // 'A' is in the alphabet
            new Base32(10, new byte[] { 'A' }, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lineSeparator must not contain Base32 characters"));
        }
    }

    @Test
    public void testInvalidPadInAlphabet() throws Throwable {
        try {
            // 'A' is in the alphabet, cannot be used as pad
            new Base32(false, (byte) 'A');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("pad must not be in alphabet or whitespace"));
        }
    }

    @Test
    public void testInvalidPadWhitespace() throws Throwable {
        try {
            // ' ' is whitespace, cannot be used as pad
            new Base32(false, (byte) ' ');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("pad must not be in alphabet or whitespace"));
        }
    }

    @Test
    public void testIsInAlphabet() throws Throwable {
        Base32 base32 = new Base32();
        assertTrue(base32.isInAlphabet((byte) 'A'));
        assertTrue(base32.isInAlphabet((byte) 'Z'));
        assertTrue(base32.isInAlphabet((byte) '2'));
        assertTrue(base32.isInAlphabet((byte) '7'));
        assertFalse(base32.isInAlphabet((byte) '1'));
        assertFalse(base32.isInAlphabet((byte) '8'));
        assertFalse(base32.isInAlphabet((byte) -1));
        assertFalse(base32.isInAlphabet((byte) 127));
    }

    @Test
    public void testDecodeWithModulusCases() throws Throwable {
        Base32 base32 = new Base32();
        // Test different padding/modulus scenarios in decode
        // Modulus lengths corresponding to various encoded lengths
        String[] testStrings = {
            "MY=======", // 1 byte
            "MFRA====", // 2 bytes
            "MFRGG===", // 3 bytes
            "MFRGGZA=", // 4 bytes
            "MFRGGZDF", // 5 bytes
            "MFRGGZDFMY======", // 6 bytes
            "MFRGGZDFMFRA====", // 7 bytes
        };

        for (String s : testStrings) {
            byte[] decoded = base32.decode(s);
            assertNotNull(decoded);
        }
    }

    @Test
    public void testEncodeEmptyAndNull() throws Throwable {
        Base32 base32 = new Base32();
        byte[] empty = new byte[0];
        assertArrayEquals(empty, base32.encode(empty));
        assertArrayEquals(null, base32.encode(null));
        assertArrayEquals(null, base32.decode(null));
    }

    @Test
    public void testEncodeDifferentModulusSizes() throws Throwable {
        Base32 base32 = new Base32(0);
        // Test inputs of length 1, 2, 3, 4, 5 to trigger different encode modulus branches (1 through 4)
        for (int i = 1; i <= 5; i++) {
            byte[] input = new byte[i];
            for (int j = 0; j < i; j++) {
                input[j] = (byte) (j + 1);
            }
            byte[] encoded = base32.encode(input);
            assertNotNull(encoded);
            byte[] decoded = base32.decode(encoded);
            assertArrayEquals(input, decoded);
        }
    }

    @Test
    public void testDecodeEOFAndModulusExceptionsOrHandling() throws Throwable {
        Base32 base32 = new Base32();
        // Modulus 1 is invalid in decode final switch (throws IllegalStateException if forced)
        // Let's invoke decode directly via Context if possible, or via stream of invalid length characters without padding
        // Actually, decoding a single char of alphabet gives modulus 1. If EOF hits at modulus 1, what happens?
        // Let's test standard decoding with garbage or edge cases.
        byte[] decoded = base32.decode("A");
        assertNotNull(decoded);
    }
}