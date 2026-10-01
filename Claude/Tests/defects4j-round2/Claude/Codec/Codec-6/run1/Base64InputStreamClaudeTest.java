package org.apache.commons.codec.binary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import static org.junit.Assert.*;

public class Base64InputStreamClaudeTest {

    private byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64];
        int r = stream.read(buf, 0, buf.length);
        while (r != -1) {
            if (r > 0) {
                out.write(buf, 0, r);
            }
            r = stream.read(buf, 0, buf.length);
        }
        return out.toByteArray();
    }

    // Constructor(InputStream): ตรวจว่า default คือ decode
    @Test
    public void testConstructor_singleArg_decodesBase64Data() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] decoded = readAll(stream);
        assertEquals("Hello World!", new String(decoded, "UTF-8"));
    }

    // Constructor(InputStream, boolean doEncode=false): ต้อง decode เหมือน constructor เดียว
    @Test
    public void testConstructor_twoArgsDoEncodeFalse_decodesBase64Data() throws Throwable {
        byte[] encoded = "QQ==".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded), false);
        byte[] decoded = readAll(stream);
        assertEquals("A", new String(decoded, "UTF-8"));
    }

    // Constructor(InputStream, boolean doEncode=true): ต้อง encode ข้อมูล
    @Test
    public void testConstructor_twoArgsDoEncodeTrue_encodesData() throws Throwable {
        byte[] plain = "Hello World!".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(plain), true);
        byte[] encoded = readAll(stream);
        String text = new String(encoded, "UTF-8");
        assertTrue(text.startsWith("SGVsbG8gV29ybGQh"));
    }

    // Constructor 4-arg โหมด encode: ต้องใช้ lineLength/lineSeparator ที่กำหนด
    @Test
    public void testConstructor_fourArgsEncodeCustomLineLengthAndSeparator_usesSeparator() throws Throwable {
        byte[] plain = "Hello World!".getBytes("UTF-8");
        byte[] sep = new byte[] { (byte) '|' };
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(plain), true, 4, sep);
        byte[] encoded = readAll(stream);
        String text = new String(encoded, "UTF-8");
        assertTrue(text.indexOf('|') >= 0);
        Base64InputStream decoder = new Base64InputStream(new ByteArrayInputStream(encoded), false);
        byte[] decoded = readAll(decoder);
        assertEquals("Hello World!", new String(decoded, "UTF-8"));
    }

    // Constructor 4-arg โหมด decode: lineLength/lineSeparator ต้องถูกละเลยตาม Javadoc
    @Test
    public void testConstructor_fourArgsDecodeMode_ignoresLineLengthAndSeparator() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        byte[] sep = new byte[] { (byte) '|' };
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded), false, 4, sep);
        byte[] decoded = readAll(stream);
        assertEquals("Hello World!", new String(decoded, "UTF-8"));
    }

    // read(): สตรีมว่างเปล่าต้องคืน -1 ทันที (EOF)
    @Test
    public void testRead_emptyStream_returnsMinusOne() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(-1, stream.read());
    }

    // read(): อ่านทีละ 1 ไบต์เพื่อถอดรหัสข้อมูลที่ผลลัพธ์มีหลายไบต์ - จุดต้องสงสัยเรื่อง setInitialBuffer กับ buffer ขนาด 1
    @Test
    public void testRead_singleByteLoopDecodesMultiByteData_matchesExpected() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] result = new byte[12];
        for (int i = 0; i < 12; i++) {
            result[i] = (byte) stream.read();
        }
        assertEquals("Hello World!", new String(result, "UTF-8"));
    }

    // read(): ตรวจ ternary เมื่อไบต์ถอดรหัสมี bit สูงสุดเป็น 1 (0xFF) ต้องคืนค่า 0-255
    @Test
    public void testRead_decodedByteWithHighBit_returnsUnsignedIntValue() throws Throwable {
        byte[] encoded = "/w==".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        int value = stream.read();
        assertEquals(255, value);
    }

    // read(): ตรวจ ternary เมื่อไบต์ถอดรหัสมีค่าน้อยกว่า 128 ไม่ต้องบวก 256
    @Test
    public void testRead_decodedByteLowValue_returnsSameIntValue() throws Throwable {
        byte[] encoded = "QQ==".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        int value = stream.read();
        assertEquals(65, value);
    }

    // read(): หลัง EOF เรียกซ้ำยังต้องคืน -1 เสมอ
    @Test
    public void testRead_afterEOF_returnsMinusOneRepeatedly() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
    }

    // read(byte[],off,len): b == null -> NullPointerException
    @Test
    public void testReadByteArray_nullArray_throwsNullPointerException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        try {
            stream.read(null, 0, 1);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // read(byte[],off,len): offset < 0 -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_negativeOffset_throwsIndexOutOfBoundsException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[5];
        try {
            stream.read(buf, -1, 1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],off,len): len < 0 -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_negativeLen_throwsIndexOutOfBoundsException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[5];
        try {
            stream.read(buf, 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],off,len): offset > b.length -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsetGreaterThanArrayLength_throwsIndexOutOfBoundsException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[3];
        try {
            stream.read(buf, 5, 0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],off,len): offset+len > b.length -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsetPlusLenExceedsArrayLength_throwsIndexOutOfBoundsException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[5];
        try {
            stream.read(buf, 0, 10);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],off,len): len == 0 -> คืน 0 ทันทีโดยไม่แตะสตรีม
    @Test
    public void testReadByteArray_lenZero_returnsZeroImmediately() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[5];
        int result = stream.read(buf, 0, 0);
        assertEquals(0, result);
    }

    // read(byte[],off,len): b.length == len เปิด path setInitialBuffer โดย capacity พอดีกับผลลัพธ์จริง
    @Test
    public void testReadByteArray_exactSizeBufferTriggersOptimization_decodesCorrectly() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[12];
        int n = stream.read(buf, 0, 12);
        assertEquals(12, n);
        assertEquals("Hello World!", new String(buf, "UTF-8"));
    }

    // read(byte[],off,len): b.length != len ไม่เข้า path optimization ตรงๆ
    @Test
    public void testReadByteArray_largerBufferThanLen_decodesCorrectly() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[50];
        int n = stream.read(buf, 0, 20);
        assertEquals(12, n);
        assertEquals("Hello World!", new String(buf, 0, n, "UTF-8"));
    }

    // read(byte[],off,len): ถอดรหัสโดยเริ่มเขียนที่ offset ที่กำหนดอย่างถูกต้อง
    @Test
    public void testReadByteArray_withNonZeroOffset_decodesAtCorrectPosition() throws Throwable {
        byte[] encoded = "SGVsbG8gV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[20];
        int n = stream.read(buf, 3, 12);
        assertEquals(12, n);
        assertEquals("Hello World!", new String(buf, 3, 12, "UTF-8"));
    }

    // read(byte[],off,len): หลังอ่านข้อมูลหมดแล้วเรียกซ้ำต้องคืน -1
    @Test
    public void testReadByteArray_eof_returnsMinusOne() throws Throwable {
        byte[] encoded = "QQ==".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[10];
        int first = stream.read(buf, 0, 10);
        assertEquals(1, first);
        int second = stream.read(buf, 0, 10);
        assertEquals(-1, second);
    }

    // read(byte[],off,len): ข้อมูล base64 ที่มี \r\n แทรกต้องถูกข้ามและถอดรหัสได้ถูกต้อง
    @Test
    public void testReadByteArray_decodeIgnoresEmbeddedLineBreaks() throws Throwable {
        byte[] encoded = "SGVsbG8g\r\nV29ybGQh".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(encoded));
        byte[] buf = new byte[12];
        int n = stream.read(buf, 0, 12);
        assertEquals(12, n);
        assertEquals("Hello World!", new String(buf, "UTF-8"));
    }

    // read(byte[],off,len): สตรีมต้นทางว่างเปล่าต้องคืน -1 ทันที
    @Test
    public void testReadByteArray_emptyInputStream_returnsMinusOne() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[10];
        int n = stream.read(buf, 0, 10);
        assertEquals(-1, n);
    }

    // read(byte[],off,len): โหมด encode ต้องสร้าง base64 ที่ขึ้นต้นตรงตามมาตรฐาน
    @Test
    public void testReadByteArray_encodeModeProducesExpectedPrefix() throws Throwable {
        byte[] plain = "Hello World!".getBytes("UTF-8");
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(plain), true);
        byte[] encoded = readAll(stream);
        String text = new String(encoded, "UTF-8");
        assertTrue(text.startsWith("SGVsbG8gV29ybGQh"));
    }

    // read(byte[],off,len): encode แล้ว decode กลับต้องได้ข้อมูลเดิม (property-based oracle)
    @Test
    public void testReadByteArray_roundTripEncodeThenDecode_matchesOriginal() throws Throwable {
        byte[] plain = "The quick brown fox".getBytes("UTF-8");
        Base64InputStream encoder = new Base64InputStream(new ByteArrayInputStream(plain), true);
        byte[] encoded = readAll(encoder);
        Base64InputStream decoder = new Base64InputStream(new ByteArrayInputStream(encoded), false);
        byte[] decoded = readAll(decoder);
        assertEquals("The quick brown fox", new String(decoded, "UTF-8"));
    }

    // markSupported(): ต้องคืน false เสมอตาม Javadoc
    @Test
    public void testMarkSupported_returnsFalse() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        assertFalse(stream.markSupported());
    }
}
