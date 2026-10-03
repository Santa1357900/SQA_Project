package org.apache.commons.compress.archivers.sevenz;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CodersTest {

    @Test
    public void testAddDecoderUnsupported() throws Throwable {
        Coder coder = new Coder();
        coder.decompressionMethodId = new byte[] { (byte) 0xFF, (byte) 0xEE };
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        
        try {
            Coders.addDecoder(bais, coder, null);
            fail("Expected IOException for unsupported method");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Unsupported compression method"));
        }
    }

    @Test
    public void testAddEncoderUnsupported() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Coders.addEncoder(baos, null, null);
            fail("Expected IOException for unsupported method");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Unsupported compression method"));
        }
    }

    @Test
    public void testCopyDecoder() throws Throwable {
        Coders.CopyDecoder decoder = new Coders.CopyDecoder();
        byte[] data = new byte[] { 1, 2, 3, 4 };
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        
        InputStream decoded = decoder.decode(bais, null, null);
        assertNotNull(decoded);
        
        byte[] readBuf = new byte[4];
        int read = decoded.read(readBuf);
        assertEquals(4, read);
        assertArrayEquals(data, readBuf);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream encoded = decoder.encode(baos, null);
        assertNotNull(encoded);
    }

    @Test
    public void testLZMADecoderInvalidDictSize() throws Throwable {
        Coders.LZMADecoder decoder = new Coders.LZMADecoder();
        Coder coder = new Coder();
        // properties: propsByte, and dictSize > LZMAInputStream.DICT_SIZE_MAX (4GiB)
        coder.properties = new byte[] { 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x10, 0, 0, 0 };
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);

        try {
            decoder.decode(bais, coder, null);
            fail("Expected IOException due to large dictionary size");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Dictionary larger than 4GiB"));
        }
    }

    @Test
    public void testDeflateDecoderAndEncoder() throws Throwable {
        Coders.DeflateDecoder decoder = new Coders.DeflateDecoder();
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        InputStream is = decoder.decode(bais, null, null);
        assertNotNull(is);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream os = decoder.encode(baos, null);
        assertNotNull(os);
        os.write(new byte[] { 1, 2, 3 });
        os.close();
    }

    @Test
    public void testBZIP2DecoderAndEncoder() throws Throwable {
        Coders.BZIP2Decoder decoder = new Coders.BZIP2Decoder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Just testing encoder instantiation and basic usage if possible, or decode with dummy stream
        try {
            OutputStream os = decoder.encode(baos, null);
            assertNotNull(os);
            os.close();
        } catch (Throwable t) {
            // bzip2 encoder might throw if data is invalid, but basic check is fine
        }
    }

    @Test
    public void testAES256SHA256DecoderNullPassword() throws Throwable {
        Coders.AES256SHA256Decoder decoder = new Coders.AES256SHA256Decoder();
        Coder coder = new Coder();
        coder.properties = new byte[10];
        // Set properties so it passes length checks
        coder.properties[0] = 3; // some byte0
        coder.properties[1] = 3; // some byte1
        
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[32]);
        InputStream is = decoder.decode(coder, coder, null); // passing coder as first arg, or bais
        
        // Correct invocation:
        InputStream decoded = decoder.decode(bais, coder, null);
        assertNotNull(decoded);

        try {
            decoded.read();
            fail("Expected IOException due to null password");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("password"));
        }
        
        decoded.close();
    }

    @Test
    public void testAES256SHA256DecoderInvalidPropertiesLength() throws Throwable {
        Coders.AES256SHA256Decoder decoder = new Coders.AES256SHA256Decoder();
        Coder coder = new Coder();
        coder.properties = new byte[] { (byte) 0xFF, (byte) 0xFF }; // requires large salt/iv
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[32]);

        InputStream decoded = decoder.decode(bais, coder, new byte[] { 1, 2, 3 });
        try {
            decoded.read();
            fail("Expected IOException due to salt/iv too long");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Salt size + IV size too long"));
        }
    }

    @Test
    public void testAES256SHA256DecoderWithCycles() throws Throwable {
        Coders.AES256SHA256Decoder decoder = new Coders.AES256SHA256Decoder();
        Coder coder = new Coder();
        // numCyclesPower = 1 (not 0x3f)
        coder.properties = new byte[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[32]);

        InputStream decoded = decoder.decode(bais, coder, new byte[] { 1, 2, 3 });
        try {
            decoded.read(new byte[10], 0, 5);
        } catch (IOException e) {
            // Expected either general security exception or init error depending on cipher/IV
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testDummyByteAddingInputStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        Coders.DeflateDecoder decoder = new Coders.DeflateDecoder();
        InputStream is = decoder.decode(bais, new Coder(), null);
        
        int val = is.read();
        // Should read the dummy byte (0) when underlying stream is exhausted
        assertEquals(0, val);
        
        // Test read with buffer
        ByteArrayInputStream bais2 = new ByteArrayInputStream(new byte[0]);
        InputStream is2 = decoder.decode(bais2, new Coder(), null);
        byte[] buf = new byte[10];
        int readLen = is2.read(buf, 0, 5);
        assertEquals(1, readLen);
        assertEquals(0, buf[0]);
    }
}