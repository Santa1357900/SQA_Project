package org.apache.commons.compress.archivers.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZipArchiveOutputStreamClaudeTest {

    private ByteArrayOutputStream baos;
    private ZipArchiveOutputStream zos;

    @Before
    public void setUp() throws Throwable {
        baos = new ByteArrayOutputStream();
        zos = new ZipArchiveOutputStream(baos);
    }

    private long crc32(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data);
        return c.getValue();
    }

    private int countOccurrences(byte[] haystack, byte[] needle) {
        int count = 0;
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                count++;
            }
        }
        return count;
    }

    private boolean isUtf8FlagSet(byte[] zipBytes) {
        int gpb = (zipBytes[6] & 0xff) | ((zipBytes[7] & 0xff) << 8);
        return (gpb & 0x0800) != 0;
    }

    // isSeekable(): OutputStream-based constructor must not be seekable
    @Test
    public void testIsSeekable_withOutputStreamConstructor_returnsFalse() throws Throwable {
        assertFalse(zos.isSeekable());
    }

    // getEncoding(): default value contract is UTF-8
    @Test
    public void testGetEncoding_defaultValue_isUTF8() throws Throwable {
        assertEquals("UTF-8", zos.getEncoding());
    }

    // setEncoding(null): "use null for the platform's default encoding"
    @Test
    public void testSetEncoding_null_getEncodingReturnsNull() throws Throwable {
        zos.setEncoding(null);
        assertNull(zos.getEncoding());
    }

    // setEncoding(custom): getEncoding reflects the value just set
    @Test
    public void testSetEncoding_customValue_getEncodingReturnsSameValue() throws Throwable {
        zos.setEncoding("ISO-8859-1");
        assertEquals("ISO-8859-1", zos.getEncoding());
    }

    // setUseLanguageEncodingFlag default true -> UTF8 GPB bit (0x0800) set in LFH
    @Test
    public void testSetUseLanguageEncodingFlag_defaultTrue_setsUtf8FlagBit() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertTrue(isUtf8FlagSet(baos.toByteArray()));
        zos.closeArchiveEntry();
        zos.finish();
    }

    // setUseLanguageEncodingFlag(false) -> UTF8 GPB bit cleared in LFH
    @Test
    public void testSetUseLanguageEncodingFlag_false_clearsUtf8FlagBit() throws Throwable {
        zos.setUseLanguageEncodingFlag(false);
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertFalse(isUtf8FlagSet(baos.toByteArray()));
        zos.closeArchiveEntry();
        zos.finish();
    }

    // setCreateUnicodeExtraFields(NOT_ENCODEABLE): non-ASCII name under ASCII encoding gets a unicode extra field
    @Test
    public void testSetCreateUnicodeExtraFields_notEncodeableName_addsLocalExtraData() throws Throwable {
        zos.setEncoding("ASCII");
        zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NOT_ENCODEABLE);
        ZipArchiveEntry entry = new ZipArchiveEntry("caf\u00e9.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertTrue(entry.getLocalFileDataExtra().length > 0);
        zos.closeArchiveEntry();
        zos.finish();
    }

    // setFallbackToUTF8(true): non-encodable name forces UTF8 flag even though encoding is ASCII
    @Test
    public void testSetFallbackToUTF8_true_setsUtf8FlagForNonEncodableName() throws Throwable {
        zos.setEncoding("ASCII");
        zos.setFallbackToUTF8(true);
        ZipArchiveEntry entry = new ZipArchiveEntry("caf\u00e9.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertTrue(isUtf8FlagSet(baos.toByteArray()));
        zos.closeArchiveEntry();
        zos.finish();
    }

    // setFallbackToUTF8(false) default: non-encodable name does NOT force UTF8 flag
    @Test
    public void testSetFallbackToUTF8_false_doesNotSetUtf8FlagForNonEncodableName() throws Throwable {
        zos.setEncoding("ASCII");
        ZipArchiveEntry entry = new ZipArchiveEntry("caf\u00e9.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertFalse(isUtf8FlagSet(baos.toByteArray()));
        zos.closeArchiveEntry();
        zos.finish();
    }

    // finish(): calling finish twice throws IOException mentioning "finished"
    @Test
    public void testFinish_calledTwice_throwsIOException() throws Throwable {
        zos.finish();
        try {
            zos.finish();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // finish(): an unclosed entry must be reported
    @Test
    public void testFinish_withUnclosedEntry_throwsIOException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        try {
            zos.finish();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unclosed"));
        }
    }

    // finish(): empty archive (0 entries) must start directly with the End-Of-Central-Directory signature
    @Test
    public void testFinish_emptyArchive_writesEndOfCentralDirectorySignature() throws Throwable {
        zos.finish();
        byte[] result = baos.toByteArray();
        byte[] head = new byte[4];
        System.arraycopy(result, 0, head, 0, 4);
        assertArrayEquals(ZipArchiveOutputStream.EOCD_SIG, head);
    }

    // setComment(): comment bytes are appended at the very end of the archive
    @Test
    public void testFinish_setComment_writesCommentBytesAtEnd() throws Throwable {
        zos.setComment("hello");
        zos.finish();
        byte[] result = baos.toByteArray();
        byte[] tail = new byte[5];
        System.arraycopy(result, result.length - 5, tail, 0, 5);
        assertArrayEquals("hello".getBytes("UTF-8"), tail);
    }

    // closeArchiveEntry(): no current entry open
    @Test
    public void testCloseArchiveEntry_noCurrentEntry_throwsIOException() throws Throwable {
        try {
            zos.closeArchiveEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No current entry"));
        }
    }

    // closeArchiveEntry(): stream already finished
    @Test
    public void testCloseArchiveEntry_afterFinished_throwsIOException() throws Throwable {
        zos.finish();
        try {
            zos.closeArchiveEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // closeArchiveEntry(): STORED method, wrong declared CRC must be rejected
    @Test
    public void testCloseArchiveEntry_storedWrongCrc_throwsZipException() throws Throwable {
        byte[] data = "abc".getBytes("UTF-8");
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(data.length);
        entry.setCrc(crc32(data) + 1);
        zos.putArchiveEntry(entry);
        zos.write(data, 0, data.length);
        try {
            zos.closeArchiveEntry();
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("CRC"));
        }
    }

    // closeArchiveEntry(): STORED method, wrong declared size must be rejected
    @Test
    public void testCloseArchiveEntry_storedWrongSize_throwsZipException() throws Throwable {
        byte[] data = "abcd".getBytes("UTF-8");
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(data.length + 1);
        entry.setCrc(crc32(data));
        zos.putArchiveEntry(entry);
        zos.write(data, 0, data.length);
        try {
            zos.closeArchiveEntry();
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("size"));
        }
    }

    // closeArchiveEntry(): DEFLATED method must auto-compute size and CRC per class contract
    @Test
    public void testCloseArchiveEntry_deflatedEntry_autoCalculatesSizeAndCrc() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ZipArchiveEntry entry = new ZipArchiveEntry("d.txt");
        zos.putArchiveEntry(entry);
        zos.write(data, 0, data.length);
        zos.closeArchiveEntry();
        assertEquals(data.length, entry.getSize());
        assertEquals(crc32(data), entry.getCrc());
        assertTrue(countOccurrences(baos.toByteArray(), ZipArchiveOutputStream.DD_SIG) >= 1);
        zos.finish();
    }

    // addRawArchiveEntry(): 2-phase (sizes known) STORED entry is written with a valid local file header
    @Test
    public void testAddRawArchiveEntry_storedPhasedEntry_writesLocalFileHeader() throws Throwable {
        byte[] data = "payload".getBytes("UTF-8");
        ZipArchiveEntry src = new ZipArchiveEntry("raw.bin");
        src.setMethod(ZipArchiveOutputStream.STORED);
        src.setSize(data.length);
        src.setCompressedSize(data.length);
        src.setCrc(crc32(data));
        ByteArrayInputStream rawIn = new ByteArrayInputStream(data);
        zos.addRawArchiveEntry(src, rawIn);
        zos.finish();
        byte[] result = baos.toByteArray();
        byte[] head = new byte[4];
        System.arraycopy(result, 0, head, 0, 4);
        assertArrayEquals(ZipArchiveOutputStream.LFH_SIG, head);
    }

    // putArchiveEntry(): stream already finished
    @Test
    public void testPutArchiveEntry_afterFinished_throwsIOException() throws Throwable {
        zos.finish();
        ZipArchiveEntry entry = new ZipArchiveEntry("x.txt");
        try {
            zos.putArchiveEntry(entry);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // putArchiveEntry(): STORED method requires a known uncompressed size up-front
    @Test
    public void testPutArchiveEntry_storedUnknownSize_throwsZipException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("x.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        try {
            zos.putArchiveEntry(entry);
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("size"));
        }
    }

    // putArchiveEntry(): STORED method requires a known CRC up-front
    @Test
    public void testPutArchiveEntry_storedUnknownCrc_throwsZipException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("x.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        try {
            zos.putArchiveEntry(entry);
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("crc"));
        }
    }

    // putArchiveEntry()/closeArchiveEntry(): STORED with correct size/crc produces a valid LFH
    @Test
    public void testPutArchiveEntry_storedCorrectSizeAndCrc_writesLocalFileHeader() throws Throwable {
        byte[] data = "data".getBytes("UTF-8");
        ZipArchiveEntry entry = new ZipArchiveEntry("s.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(data.length);
        entry.setCrc(crc32(data));
        zos.putArchiveEntry(entry);
        zos.write(data, 0, data.length);
        zos.closeArchiveEntry();
        zos.finish();
        byte[] result = baos.toByteArray();
        byte[] head = new byte[4];
        System.arraycopy(result, 0, head, 0, 4);
        assertArrayEquals(ZipArchiveOutputStream.LFH_SIG, head);
    }

    // putArchiveEntry(): Zip64Mode.Never with a declared size too big must raise Zip64RequiredException
    @Test
    public void testPutArchiveEntry_zip64ModeNeverEntryTooBig_throwsZip64RequiredException() throws Throwable {
        zos.setUseZip64(Zip64Mode.Never);
        ZipArchiveEntry entry = new ZipArchiveEntry("big.bin");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(ZipConstants.ZIP64_MAGIC);
        entry.setCrc(0L);
        try {
            zos.putArchiveEntry(entry);
            fail("expected Zip64RequiredException");
        } catch (Zip64RequiredException expected) {
            // expected: entry too big for Zip64Mode.Never
        }
    }

    // putArchiveEntry(): Zip64Mode.Always must add a Zip64 extended information extra field
    @Test
    public void testPutArchiveEntry_zip64ModeAlways_addsZip64ExtraField() throws Throwable {
        zos.setUseZip64(Zip64Mode.Always);
        ZipArchiveEntry entry = new ZipArchiveEntry("z.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertNotNull(entry.getExtraField(Zip64ExtendedInformationExtraField.HEADER_ID));
        zos.closeArchiveEntry();
        zos.finish();
    }

    // putArchiveEntry(): opening a second entry without closing the first auto-closes the first one
    @Test
    public void testPutArchiveEntry_secondEntryWithoutClosingFirst_autoClosesPreviousEntry() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("1.txt");
        e1.setMethod(ZipArchiveOutputStream.STORED);
        e1.setSize(0L);
        e1.setCrc(0L);
        zos.putArchiveEntry(e1);
        ZipArchiveEntry e2 = new ZipArchiveEntry("2.txt");
        e2.setMethod(ZipArchiveOutputStream.STORED);
        e2.setSize(0L);
        e2.setCrc(0L);
        zos.putArchiveEntry(e2);
        zos.closeArchiveEntry();
        zos.finish();
        assertEquals(2, countOccurrences(baos.toByteArray(), ZipArchiveOutputStream.LFH_SIG));
    }

    // setMethod(): default method is applied to an entry whose method is unspecified
    @Test
    public void testSetMethod_appliesDefaultMethodToEntryWithUnspecifiedMethod() throws Throwable {
        zos.setMethod(ZipArchiveOutputStream.STORED);
        ZipArchiveEntry entry = new ZipArchiveEntry("m.txt");
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putArchiveEntry(entry);
        assertEquals(ZipArchiveOutputStream.STORED, entry.getMethod());
        zos.closeArchiveEntry();
        zos.finish();
    }

    // setLevel(): below Deflater.DEFAULT_COMPRESSION must be rejected
    @Test
    public void testSetLevel_belowMinimum_throwsIllegalArgumentException() throws Throwable {
        try {
            zos.setLevel(Deflater.DEFAULT_COMPRESSION - 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setLevel(): above Deflater.BEST_COMPRESSION must be rejected
    @Test
    public void testSetLevel_aboveMaximum_throwsIllegalArgumentException() throws Throwable {
        try {
            zos.setLevel(Deflater.BEST_COMPRESSION + 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // canWriteEntryData(): a plain ZipArchiveEntry can always be written
    @Test
    public void testCanWriteEntryData_normalZipArchiveEntry_returnsTrue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("c.txt");
        assertTrue(zos.canWriteEntryData(entry));
    }

    // write(): no current entry open must raise IllegalStateException
    @Test
    public void testWrite_noCurrentEntry_throwsIllegalStateException() throws Throwable {
        byte[] data = new byte[] {1, 2, 3};
        try {
            zos.write(data, 0, data.length);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("No current entry"));
        }
    }

    // close(): finishes the archive and further finish() calls must fail
    @Test
    public void testClose_finishesArchiveAndPreventsFurtherFinish() throws Throwable {
        zos.close();
        assertTrue(baos.size() > 0);
        try {
            zos.finish();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // flush(): delegates to the underlying stream without losing already written data
    @Test
    public void testFlush_afterWritingEntryData_outputNotEmpty() throws Throwable {
        byte[] data = "flush-me".getBytes("UTF-8");
        ZipArchiveEntry entry = new ZipArchiveEntry("f.txt");
        zos.putArchiveEntry(entry);
        zos.write(data, 0, data.length);
        zos.flush();
        assertTrue(baos.size() > 0);
        zos.closeArchiveEntry();
        zos.finish();
    }

    // createArchiveEntry(): must refuse to create entries after the stream is finished
    @Test
    public void testCreateArchiveEntry_afterFinished_throwsIOException() throws Throwable {
        zos.finish();
        try {
            zos.createArchiveEntry(new File("dummy.txt"), "dummy.txt");
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }
}
