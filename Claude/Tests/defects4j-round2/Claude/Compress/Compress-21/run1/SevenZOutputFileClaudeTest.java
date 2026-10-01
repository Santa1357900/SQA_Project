package org.apache.commons.compress.archivers.sevenz;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SevenZOutputFileClaudeTest {

    private File tempFile;

    @Before
    public void setUp() throws Throwable {
        tempFile = File.createTempFile("sevenzouttest", ".7z");
        tempFile.deleteOnExit();
    }

    @After
    public void tearDown() throws Throwable {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    // ครอบคลุม constructor + finish(): ไม่มี entry -> ต้องเขียน 7z signature และ version bytes (0,2) ที่ต้นไฟล์
    @Test
    public void testClose_noEntries_writesSevenZSignatureAndVersionBytes() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.close();
        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        try {
            byte[] sig = SevenZFile.sevenZSignature;
            byte[] actual = new byte[sig.length];
            raf.readFully(actual);
            assertArrayEquals(sig, actual);
            assertEquals(0, raf.read());
            assertEquals(2, raf.read());
        } finally {
            raf.close();
        }
    }

    // ครอบคลุม close() เรียกซ้ำ: finished flag ป้องกัน finish() ซ้ำ, ไฟล์ยังสมบูรณ์
    @Test
    public void testClose_calledTwice_doesNotThrow() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.close();
        out.close();
        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        try {
            byte[] sig = SevenZFile.sevenZSignature;
            byte[] actual = new byte[sig.length];
            raf.readFully(actual);
            assertArrayEquals(sig, actual);
        } finally {
            raf.close();
        }
    }

    // ครอบคลุม finish() เรียกซ้ำ -> ต้อง throw IOException ตาม javadoc
    @Test
    public void testFinish_calledTwice_throwsIOException() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.finish();
        try {
            out.finish();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already"));
        } finally {
            out.close();
        }
    }

    // ครอบคลุม close() แล้วเรียก finish() อีก -> ต้อง throw IOException เพราะ finished=true แล้ว
    @Test
    public void testFinish_afterClose_throwsIOException() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.close();
        try {
            out.finish();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already"));
        }
    }

    // ครอบคลุม closeArchiveEntry() เมื่อไม่มี putArchiveEntry มาก่อน -> files.get(-1) ตาม List contract
    @Test
    public void testCloseArchiveEntry_withoutPutArchiveEntry_throwsIndexOutOfBoundsException() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            try {
                out.closeArchiveEntry();
                fail("expected IndexOutOfBoundsException");
            } catch (IndexOutOfBoundsException expected) {
                // ตามสัญญาของ List#get เมื่อ index ติดลบ
            }
        } finally {
            out.close();
        }
    }

    // ครอบคลุม closeArchiveEntry() เมื่อไม่มีการ write() -> entry ต้องไม่มี stream, size/compressedSize = 0
    @Test
    public void testCloseArchiveEntry_noDataWritten_marksEntryAsStreamless() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setDirectory(false);
            entry.setName("empty.txt");
            out.putArchiveEntry(entry);
            out.closeArchiveEntry();
            assertFalse(entry.hasStream());
            assertEquals(0L, entry.getSize());
            assertEquals(0L, entry.getCompressedSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม closeArchiveEntry() หลัง write() -> hasStream=true, size เท่ากับจำนวนไบต์ที่เขียน
    @Test
    public void testCloseArchiveEntry_afterWritingBytes_setsSizeEqualToBytesWritten() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("data.bin");
            out.putArchiveEntry(entry);
            byte[] data = new byte[] {1, 2, 3, 4, 5};
            out.write(data);
            out.closeArchiveEntry();
            assertTrue(entry.hasStream());
            assertEquals((long) data.length, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม crc32 ที่คำนวณระหว่างเขียน -> ต้องตรงกับ CRC32 มาตรฐานของข้อมูลที่เขียนจริง
    @Test
    public void testCloseArchiveEntry_crcValue_matchesStandardCrc32OfWrittenData() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        byte[] data = "hello world".getBytes("UTF-8");
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("f.txt");
            out.putArchiveEntry(entry);
            out.write(data);
            out.closeArchiveEntry();
            CRC32 expected = new CRC32();
            expected.update(data);
            assertEquals(expected.getValue(), entry.getCrcValue());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม SevenZMethod.COPY (ไม่บีบอัด) -> compressedSize/compressedCrc ต้องเท่ากับค่าดิบ
    @Test
    public void testCloseArchiveEntry_copyMethod_compressedSizeEqualsUncompressedSize() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        byte[] data = new byte[] {10, 20, 30, 40};
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("c.bin");
            out.putArchiveEntry(entry);
            out.write(data);
            out.closeArchiveEntry();
            assertEquals(entry.getSize(), entry.getCompressedSize());
            assertEquals(entry.getCrcValue(), entry.getCompressedCrcValue());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม entry หลายตัว: แต่ละ entry ต้องมี size/hasStream เป็นของตัวเองแยกกัน
    @Test
    public void testCloseArchiveEntry_multipleEntries_eachTracksOwnSize() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry e1 = new SevenZArchiveEntry();
            e1.setName("a");
            out.putArchiveEntry(e1);
            out.write(new byte[] {1, 2, 3});
            out.closeArchiveEntry();

            SevenZArchiveEntry e2 = new SevenZArchiveEntry();
            e2.setDirectory(true);
            e2.setName("b");
            out.putArchiveEntry(e2);
            out.closeArchiveEntry();

            assertEquals(3L, e1.getSize());
            assertTrue(e1.hasStream());
            assertFalse(e2.hasStream());
            assertEquals(0L, e2.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม closeArchiveEntry() ใช้ files.get(files.size()-1) -> ต้องแก้ entry ล่าสุดเท่านั้น
    @Test
    public void testCloseArchiveEntry_operatesOnMostRecentlyPutEntry() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry first = new SevenZArchiveEntry();
            first.setDirectory(true);
            first.setName("first");
            out.putArchiveEntry(first);
            out.closeArchiveEntry();

            SevenZArchiveEntry second = new SevenZArchiveEntry();
            second.setName("second");
            out.putArchiveEntry(second);
            out.write(new byte[] {7, 7});
            out.closeArchiveEntry();

            assertFalse(first.hasStream());
            assertTrue(second.hasStream());
            assertEquals(2L, second.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม reset ของ crc32/fileBytesWritten ระหว่าง entry -> entry ถัดไปต้องไม่พกค่าของ entry ก่อนหน้า
    @Test
    public void testCloseArchiveEntry_resetsCrcAndByteCounterBetweenEntries() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry e1 = new SevenZArchiveEntry();
            e1.setName("e1");
            out.putArchiveEntry(e1);
            out.write(new byte[] {1, 2, 3, 4});
            out.closeArchiveEntry();

            SevenZArchiveEntry e2 = new SevenZArchiveEntry();
            e2.setName("e2");
            out.putArchiveEntry(e2);
            out.write(new byte[] {9});
            out.closeArchiveEntry();

            CRC32 expected2 = new CRC32();
            expected2.update(new byte[] {9});
            assertEquals(1L, e2.getSize());
            assertEquals(expected2.getValue(), e2.getCrcValue());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม write(int) หนึ่งไบต์ -> size ของ entry ต้องเป็น 1
    @Test
    public void testWrite_singleByte_recordedAsSizeOne() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("one");
            out.putArchiveEntry(entry);
            out.write(65);
            out.closeArchiveEntry();
            assertEquals(1L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม write(byte[]) เต็มอาเรย์ -> size เท่ากับความยาวอาเรย์
    @Test
    public void testWrite_byteArray_fullLengthWritten() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("arr");
            out.putArchiveEntry(entry);
            byte[] data = new byte[] {9, 8, 7};
            out.write(data);
            out.closeArchiveEntry();
            assertEquals(3L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม write(byte[],off,len) ที่ len=0 -> ไม่สร้าง stream เลย (เงื่อนไข if (len > 0))
    @Test
    public void testWrite_zeroLength_doesNotCreateStream() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("zero");
            out.putArchiveEntry(entry);
            out.write(new byte[] {1, 2, 3}, 1, 0);
            out.closeArchiveEntry();
            assertFalse(entry.hasStream());
            assertEquals(0L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม write(byte[],off,len) บางส่วนของอาเรย์ -> size เท่ากับ len ที่ระบุ
    @Test
    public void testWrite_partialRange_sizeMatchesLength() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("part");
            out.putArchiveEntry(entry);
            byte[] data = new byte[] {1, 2, 3, 4, 5, 6};
            out.write(data, 2, 3);
            out.closeArchiveEntry();
            assertEquals(3L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม write() หลายครั้งติดกัน -> ต้องสะสม size รวมถูกต้อง
    @Test
    public void testWrite_multipleCalls_accumulatesTotalSize() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("multi");
            out.putArchiveEntry(entry);
            out.write(1);
            out.write(new byte[] {2, 3});
            out.write(new byte[] {9, 4, 5, 6}, 1, 2);
            out.closeArchiveEntry();
            assertEquals(5L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม createArchiveEntry กับไฟล์ปกติ -> isDirectory=false, name ตรงกับที่ส่งเข้าไป
    @Test
    public void testCreateArchiveEntry_regularFile_notDirectoryAndNameSet() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            File regular = File.createTempFile("entrysrc", ".txt");
            regular.deleteOnExit();
            SevenZArchiveEntry entry = out.createArchiveEntry(regular, "myentry.txt");
            assertFalse(entry.isDirectory());
            assertEquals("myentry.txt", entry.getName());
            regular.delete();
        } finally {
            out.close();
        }
    }

    // ครอบคลุม createArchiveEntry กับ directory -> isDirectory=true
    @Test
    public void testCreateArchiveEntry_directoryInput_isDirectoryTrue() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            File dir = tempFile.getParentFile();
            SevenZArchiveEntry entry = out.createArchiveEntry(dir, "dirEntry");
            assertTrue(entry.isDirectory());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม createArchiveEntry ตั้งค่า lastModifiedDate จาก inputFile.lastModified()
    @Test
    public void testCreateArchiveEntry_setsLastModifiedDateFromInputFile() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        try {
            File regular = File.createTempFile("entrysrc2", ".txt");
            regular.deleteOnExit();
            long expected = regular.lastModified();
            SevenZArchiveEntry entry = out.createArchiveEntry(regular, "x");
            assertTrue(entry.getHasLastModifiedDate());
            assertEquals(expected, entry.getLastModifiedDate().getTime());
            regular.delete();
        } finally {
            out.close();
        }
    }

    // ครอบคลุม setContentCompression(COPY) ก่อนเขียนข้อมูลจริง -> ไม่มี error และ size ถูกต้อง
    @Test
    public void testSetContentCompression_copyMethod_doesNotThrowWhenWritingData() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        out.setContentCompression(SevenZMethod.COPY);
        try {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName("cp");
            out.putArchiveEntry(entry);
            out.write(new byte[] {1});
            out.closeArchiveEntry();
            assertEquals(1L, entry.getSize());
        } finally {
            out.close();
        }
    }

    // ครอบคลุม writeFileEmptyFiles: entry ที่ไม่ใช่ directory และไม่มี stream -> hasEmptyFiles=true branch
    @Test
    public void testClose_emptyNonDirectoryEntry_doesNotThrow_andEntryFieldsStreamless() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setDirectory(false);
        entry.setName("emptyfile.txt");
        out.putArchiveEntry(entry);
        out.closeArchiveEntry();
        out.close();
        assertFalse(entry.hasStream());
        assertFalse(entry.isDirectory());
    }

    // ครอบคลุม writeFileNames กับชื่อ unicode -> ชื่อบน entry ต้องคงเดิม หลัง close() สำเร็จ
    @Test
    public void testClose_unicodeEntryName_nameRoundTripsOnEntryObject() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setDirectory(true);
        String name = "\u65e5\u672c\u8a9e\u00e9.txt";
        entry.setName(name);
        out.putArchiveEntry(entry);
        out.closeArchiveEntry();
        out.close();
        assertEquals(name, entry.getName());
    }

    // ครอบคลุม writeBits ผ่าน writeFileEmptyStreams กรณี 1 บิต (sanity, ไม่ชน boundary 8 บิต)
    @Test
    public void testFinish_singleDirectoryEntry_emptyStreamSingleBitByte() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        SevenZArchiveEntry e = new SevenZArchiveEntry();
        e.setDirectory(true);
        e.setName("d0");
        out.putArchiveEntry(e);
        out.closeArchiveEntry();
        out.close();

        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        try {
            long base = SevenZFile.SIGNATURE_HEADER_SIZE;
            raf.seek(base + 8);
            assertEquals(1, raf.read());
            raf.seek(base + 9);
            assertEquals(0x80, raf.read());
        } finally {
            raf.close();
        }
    }

    // ล่าบั๊ก: writeBits ต้องแพ็ก 8 บิตลงใน 1 ไบต์พอดี (MSB-first) ตามมาตรฐานการแพ็กบิตของรูปแบบ 7z
    @Test
    public void testFinish_eightDirectoryEntries_emptyStreamBitsPackedAsSingleFullByte() throws Throwable {
        SevenZOutputFile out = new SevenZOutputFile(tempFile);
        for (int i = 0; i < 8; i++) {
            SevenZArchiveEntry e = new SevenZArchiveEntry();
            e.setDirectory(true);
            e.setName("d" + i);
            out.putArchiveEntry(e);
            out.closeArchiveEntry();
        }
        out.close();

        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        try {
            long base = SevenZFile.SIGNATURE_HEADER_SIZE;
            raf.seek(base + 8);
            int lengthByte = raf.read();
            assertEquals(1, lengthByte);
            raf.seek(base + 9);
            int contentByte = raf.read();
            assertEquals(0xFF, contentByte);
        } finally {
            raf.close();
        }
    }
}
