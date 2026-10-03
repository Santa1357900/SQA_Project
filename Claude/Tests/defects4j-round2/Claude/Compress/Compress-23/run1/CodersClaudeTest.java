package org.apache.commons.compress.archivers.sevenz;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public class CodersClaudeTest {

    // addDecoder: COPY method dispatch must return the exact same InputStream reference
    @Test
    public void testAddDecoder_copyMethod_returnsSameStream() throws Throwable {
        Coder coder = new Coder();
        coder.decompressionMethodId = SevenZMethod.COPY.getId();
        InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        InputStream result = Coders.addDecoder(in, coder, null);
        assertSame(in, result);
    }

    // addDecoder: no matching CoderId in table -> IOException "Unsupported compression method ..."
    @Test
    public void testAddDecoder_unsupportedMethod_throwsIOException() throws Throwable {
        Coder coder = new Coder();
        coder.decompressionMethodId = new byte[] {0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E};
        InputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            Coders.addDecoder(in, coder, null);
            fail("expected IOException for unsupported method");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsupported"));
        }
    }

    // addDecoder: DEFLATE dispatch roundtrips data produced by addEncoder
    @Test
    public void testAddDecoder_deflateMethod_roundTripsData() throws Throwable {
        byte[] original = new byte[] {10, 20, 30, 40, 50};
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        OutputStream encodeOut = Coders.addEncoder(compressed, SevenZMethod.DEFLATE, null);
        encodeOut.write(original);
        encodeOut.close();

        Coder coder = new Coder();
        coder.decompressionMethodId = SevenZMethod.DEFLATE.getId();
        InputStream in = Coders.addDecoder(new ByteArrayInputStream(compressed.toByteArray()), coder, null);
        byte[] result = new byte[original.length];
        int totalRead = 0;
        while (totalRead < result.length) {
            int r = in.read(result, totalRead, result.length - totalRead);
            if (r == -1) {
                break;
            }
            totalRead += r;
        }
        assertEquals(original.length, totalRead);
        assertArrayEquals(original, result);
    }

    // addDecoder: BZIP2 dispatch roundtrips data produced by addEncoder
    @Test
    public void testAddDecoder_bzip2Method_roundTripsData() throws Throwable {
        byte[] original = new byte[] {65, 66, 67, 68, 69};
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        OutputStream encodeOut = Coders.addEncoder(compressed, SevenZMethod.BZIP2, null);
        encodeOut.write(original);
        encodeOut.close();

        Coder coder = new Coder();
        coder.decompressionMethodId = SevenZMethod.BZIP2.getId();
        InputStream in = Coders.addDecoder(new ByteArrayInputStream(compressed.toByteArray()), coder, null);
        byte[] result = new byte[original.length];
        int totalRead = 0;
        while (totalRead < result.length) {
            int r = in.read(result, totalRead, result.length - totalRead);
            if (r == -1) {
                break;
            }
            totalRead += r;
        }
        assertEquals(original.length, totalRead);
        assertArrayEquals(original, result);
    }

    // addDecoder: LZMA dispatch with dict-size bytes that have the high bit set must reject
    // an oversized dictionary via IOException (bug: sign-extension of properties bytes)
    @Test
    public void testAddDecoder_lzmaMethod_dictSizeSignExtensionBug_throwsIOException() throws Throwable {
        Coder coder = new Coder();
        coder.decompressionMethodId = SevenZMethod.LZMA.getId();
        coder.properties = new byte[] {0x5D, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        InputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            Coders.addDecoder(in, coder, null);
            fail("expected IOException: dictionary size exceeds maximum");
        } catch (IOException expected) {
            // per contract, an oversized dictionary (unsigned 32-bit value 0xFFFFFFFF)
            // must be rejected instead of silently wrapping to a negative value
        }
    }

    // addEncoder: COPY method returns the same OutputStream reference
    @Test
    public void testAddEncoder_copyMethod_returnsSameStream() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        OutputStream result = Coders.addEncoder(out, SevenZMethod.COPY, null);
        assertSame(out, result);
    }

    // addEncoder: DEFLATE returns a DeflaterOutputStream instance
    @Test
    public void testAddEncoder_deflateMethod_returnsDeflaterOutputStream() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        OutputStream result = Coders.addEncoder(out, SevenZMethod.DEFLATE, null);
        assertTrue(result instanceof DeflaterOutputStream);
        result.close();
    }

    // addEncoder: BZIP2 returns a BZip2CompressorOutputStream instance
    @Test
    public void testAddEncoder_bzip2Method_returnsBZip2CompressorOutputStream() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        OutputStream result = Coders.addEncoder(out, SevenZMethod.BZIP2, null);
        assertTrue(result instanceof BZip2CompressorOutputStream);
        result.close();
    }

    // addEncoder: LZMA decoder does not override encode -> UnsupportedOperationException
    @Test
    public void testAddEncoder_lzmaMethod_throwsUnsupportedOperationException() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        try {
            Coders.addEncoder(out, SevenZMethod.LZMA, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("doesn't support writing"));
        }
    }

    // addEncoder: AES256SHA256 decoder does not override encode -> UnsupportedOperationException
    @Test
    public void testAddEncoder_aes256sha256Method_throwsUnsupportedOperationException() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        try {
            Coders.addEncoder(out, SevenZMethod.AES256SHA256, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("doesn't support writing"));
        }
    }

    // CopyDecoder.decode: body simply returns the input stream reference unchanged
    @Test
    public void testCopyDecoderDecode_returnsSameInputStreamReference() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[] {5});
        Coders.CopyDecoder decoder = new Coders.CopyDecoder();
        InputStream result = decoder.decode(in, null, null);
        assertSame(in, result);
    }

    // CopyDecoder.encode: body simply returns the output stream reference unchanged
    @Test
    public void testCopyDecoderEncode_returnsSameOutputStreamReference() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        Coders.CopyDecoder decoder = new Coders.CopyDecoder();
        OutputStream result = decoder.encode(out, null);
        assertSame(out, result);
    }

    // DeflateDecoder: encode then decode must roundtrip non-empty data exactly
    @Test
    public void testDeflateDecoderEncodeDecode_roundTrip_nonEmptyData() throws Throwable {
        byte[] original = new byte[] {1, 2, 3, 4, 5, 100, 127, -128};
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Coders.DeflateDecoder deflateDecoder = new Coders.DeflateDecoder();
        OutputStream encodeOut = deflateDecoder.encode(compressed, null);
        encodeOut.write(original);
        encodeOut.close();

        InputStream decodeIn = deflateDecoder.decode(new ByteArrayInputStream(compressed.toByteArray()), null, null);
        byte[] result = new byte[original.length];
        int totalRead = 0;
        while (totalRead < result.length) {
            int r = decodeIn.read(result, totalRead, result.length - totalRead);
            if (r == -1) {
                break;
            }
            totalRead += r;
        }
        assertEquals(original.length, totalRead);
        assertArrayEquals(original, result);
    }

    // DeflateDecoder: encode/decode roundtrip of empty data must reach end of stream (-1)
    @Test
    public void testDeflateDecoderEncodeDecode_roundTrip_emptyData() throws Throwable {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Coders.DeflateDecoder deflateDecoder = new Coders.DeflateDecoder();
        OutputStream encodeOut = deflateDecoder.encode(compressed, null);
        encodeOut.close();

        InputStream decodeIn = deflateDecoder.decode(new ByteArrayInputStream(compressed.toByteArray()), null, null);
        byte[] buffer = new byte[10];
        int read = decodeIn.read(buffer);
        assertEquals(-1, read);
    }

    // BZIP2Decoder: encode then decode must roundtrip data exactly
    @Test
    public void testBzip2DecoderEncodeDecode_roundTrip() throws Throwable {
        byte[] original = new byte[] {104, 101, 108, 108, 111};
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Coders.BZIP2Decoder bzip2Decoder = new Coders.BZIP2Decoder();
        OutputStream encodeOut = bzip2Decoder.encode(compressed, null);
        encodeOut.write(original);
        encodeOut.close();

        InputStream decodeIn = bzip2Decoder.decode(new ByteArrayInputStream(compressed.toByteArray()), null, null);
        byte[] result = new byte[original.length];
        int totalRead = 0;
        while (totalRead < result.length) {
            int r = decodeIn.read(result, totalRead, result.length - totalRead);
            if (r == -1) {
                break;
            }
            totalRead += r;
        }
        assertEquals(original.length, totalRead);
        assertArrayEquals(original, result);
    }

    // AES256SHA256Decoder.decode: read() with null password must throw IOException
    @Test
    public void testAes256Sha256DecoderDecode_nullPassword_readSingleByteThrowsIOException() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {0x00, 0x00};
        InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        InputStream result = new Coders.AES256SHA256Decoder().decode(in, coder, null);
        try {
            result.read();
            fail("expected IOException for missing password");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("password"));
        }
    }

    // AES256SHA256Decoder.decode: read(byte[], off, len) with null password must throw IOException
    @Test
    public void testAes256Sha256DecoderDecode_nullPassword_readArrayThrowsIOException() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {0x00, 0x00};
        InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        InputStream result = new Coders.AES256SHA256Decoder().decode(in, coder, null);
        byte[] buffer = new byte[4];
        try {
            result.read(buffer, 0, 4);
            fail("expected IOException for missing password");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("password"));
        }
    }

    // AES256SHA256Decoder.decode: salt size + IV size exceeding properties length must throw IOException
    @Test
    public void testAes256Sha256DecoderDecode_saltPlusIvTooLong_throwsIOException() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {(byte) 0xC0, (byte) 0xF0};
        InputStream in = new ByteArrayInputStream(new byte[] {1});
        InputStream result = new Coders.AES256SHA256Decoder().decode(in, coder, new byte[] {1, 2, 3});
        try {
            result.read();
            fail("expected IOException for salt/iv size too long");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // AES256SHA256Decoder: close() is a no-op; a later read() still must fail without a password
    @Test
    public void testAes256Sha256DecoderDecode_close_doesNothingThenReadStillThrows() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {0x00, 0x00};
        InputStream in = new ByteArrayInputStream(new byte[0]);
        InputStream result = new Coders.AES256SHA256Decoder().decode(in, coder, null);
        result.close();
        try {
            result.read();
            fail("expected IOException for missing password after close");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("password"));
        }
    }

    // LZMADecoder.decode (direct call): dict-size bytes with high bit set must raise IOException,
    // not silently wrap into a negative dictionary size (the known sign-extension bug)
    @Test
    public void testLzmaDecoderDecode_dictSizeAllBitsSet_throwsIOExceptionForOversizedDictionary() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {0x5D, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        InputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            new Coders.LZMADecoder().decode(in, coder, null);
            fail("expected IOException: dictionary size exceeds maximum");
        } catch (IOException expected) {
            // correct behavior per source contract when dictSize > LZMAInputStream.DICT_SIZE_MAX
        }
    }

    // LZMADecoder.decode: dict-size bytes without any high bit set must not trigger the
    // "dictionary too large" rejection and must successfully construct a stream
    @Test
    public void testLzmaDecoderDecode_validSmallDictSize_constructsWithoutDictionaryTooLargeException() throws Throwable {
        Coder coder = new Coder();
        coder.properties = new byte[] {0x5D, 0x00, 0x00, 0x10, 0x00};
        InputStream in = new ByteArrayInputStream(new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
        InputStream result = new Coders.LZMADecoder().decode(in, coder, null);
        assertNotNull(result);
    }

    // CoderBase default encode(): LZMADecoder does not override it -> UnsupportedOperationException
    @Test
    public void testCoderBaseEncode_notOverriddenByLzmaDecoder_throwsUnsupportedOperationException() throws Throwable {
        OutputStream out = new ByteArrayOutputStream();
        try {
            new Coders.LZMADecoder().encode(out, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("doesn't support writing"));
        }
    }
}
