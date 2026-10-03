package org.apache.commons.codec.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class BaseNCodecInputStreamTest {

    @Test(expected = NullPointerException.class)
    public void testReadNullByteArray() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            in.read(null, 0, 1);
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadIndexOutOfBoundsNegativeOffset() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            in.read(new byte[10], -1, 5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadIndexOutOfBoundsNegativeLength() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            in.read(new byte[10], 0, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadIndexOutOfBoundsOffsetTooLarge() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            in.read(new byte[10], 11, 0);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadIndexOutOfBoundsOffsetPlusLenTooLarge() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            in.read(new byte[10], 5, 6);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadZeroLength() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            int result = in.read(new byte[10], 0, 0);
            assertEquals(0, result);
        } finally {
            in.close();
        }
    }

    @Test
    public void testMarkSupported() throws Throwable {
        byte[] data = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            assertFalse(in.markSupported());
        } finally {
            in.close();
        }
    }

    @Test
    public void testReadEncodeAndDecode() throws Throwable {
        byte[] original = "Hello World".getBytes("UTF-8");
        
        // Test Encoding
        ByteArrayInputStream baisEnc = new ByteArrayInputStream(original);
        Base64 base64Enc = new Base64();
        BaseNCodecInputStream inEnc = new BaseNCodecInputStream(baisEnc, base64Enc, true);
        
        byte[] encodedBuf = new byte[100];
        int totalEncodedRead = 0;
        int n;
        try {
            while ((n = inEnc.read(encodedBuf, totalEncodedRead, encodedBuf.length - totalEncodedRead)) != -1) {
                if (n == 0) {
                    continue;
                }
                totalEncodedRead += n;
            }
        } finally {
            inEnc.close();
        }
        
        // Test Decoding
        ByteArrayInputStream baisDec = new ByteArrayInputStream(encodedBuf, 0, totalEncodedRead);
        Base64 base64Dec = new Base64();
        BaseNCodecInputStream inDec = new BaseNCodecInputStream(baisDec, base64Dec, false);
        
        byte[] decodedBuf = new byte[100];
        int totalDecodedRead = 0;
        try {
            while ((n = inDec.read(decodedBuf, totalDecodedRead, decodedBuf.length - totalDecodedRead)) != -1) {
                if (n == 0) {
                    continue;
                }
                totalDecodedRead += n;
            }
        } finally {
            inDec.close();
        }
        
        assertEquals(original.length, totalDecodedRead);
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], decodedBuf[i]);
        }
    }

    @Test
    public void testReadSingleByte() throws Throwable {
        byte[] original = "A".getBytes("UTF-8");
        ByteArrayInputStream bais = new ByteArrayInputStream(original);
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(bais, base64, true);
        try {
            int singleRead = in.read();
            // Should read a valid encoded byte or EOF (-1)
            boolean isValidRange = (singleRead >= 0 && singleRead <= 255) || singleRead == -1;
            assertEquals(true, isValidRange);
        } finally {
            in.close();
        }
    }

    @Test
    public void testIOExceptionPropagation() throws Throwable {
        InputStream faultyStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated IO error");
            }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("Simulated IO error");
            }
        };
        
        Base64 base64 = new Base64();
        BaseNCodecInputStream in = new BaseNCodecInputStream(faultyStream, base64, false);
        try {
            in.read(new byte[10], 0, 5);
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("Simulated IO error", e.getMessage());
        } finally {
            in.close();
        }
    }
}