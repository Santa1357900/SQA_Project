package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarArchiveInputStreamClaudeTest {

    private void writeOctal(byte[] buf, int offset, int length, long value) {
        String oct = Long.toOctalString(value);
        int digits = length - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = oct.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(oct);
        byte[] bytes = sb.toString().getBytes();
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
        buf[offset + length - 1] = 0;
    }

    private byte[] buildHeader(String name, long size, char typeflag) {
        byte[] buf = new byte[512];
        byte[] nameBytes = name.getBytes();
        System.arraycopy(nameBytes, 0, buf, 0, nameBytes.length);
        writeOctal(buf, 100, 8, 420L);
        writeOctal(buf, 108, 8, 0L);
        writeOctal(buf, 116, 8, 0L);
        writeOctal(buf, 124, 12, size);
        writeOctal(buf, 136, 12, 0L);
        for (int i = 148; i < 156; i++) {
            buf[i] = ' ';
        }
        buf[156] = (byte) typeflag;
        buf[257] = 'u';
        buf[258] = 's';
        buf[259] = 't';
        buf[260] = 'a';
        buf[261] = 'r';
        buf[262] = 0;
        buf[263] = '0';
        buf[264] = '0';
        writeOctal(buf, 329, 8, 0L);
        writeOctal(buf, 337, 8, 0L);
        long sum = 0;
        for (int i = 0; i < 512; i++) {
            sum += (buf[i] & 0xFF);
        }
        String oct = Long.toOctalString(sum);
        StringBuilder sb = new StringBuilder();
        for (int i = oct.length(); i < 6; i++) {
            sb.append('0');
        }
        sb.append(oct);
        byte[] chk = sb.toString().getBytes();
        System.arraycopy(chk, 0, buf, 148, 6);
        buf[154] = 0;
        buf[155] = ' ';
        return buf;
    }

    private byte[] buildSimpleEntryStream(String name, byte[] content) {
        byte[] header = buildHeader(name, content.length, '0');
        byte[] dataRecord = new byte[512];
        System.arraycopy(content, 0, dataRecord, 0, content.length);
        byte[] terminator = new byte[512];
        byte[] result = new byte[512 * 3];
        System.arraycopy(header, 0, result, 0, 512);
        System.arraycopy(dataRecord, 0, result, 512, 512);
        System.arraycopy(terminator, 0, result, 1024, 512);
        return result;
    }

    // constructor(InputStream): default record size must be the standard 512-byte tar record
    @Test
    public void testConstructor_defaultBlockAndRecordSize_usesStandardRecordSize() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(512, tis.getRecordSize());
    }

    // constructor(InputStream, blockSize): record size still defaults to 512
    @Test
    public void testConstructor_customBlockSize_defaultRecordSize() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048);
        assertEquals(512, tis.getRecordSize());
    }

    // constructor(InputStream, blockSize, recordSize): record size uses given value
    @Test
    public void testConstructor_customBlockAndRecordSize_usesGivenRecordSize() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024, 256);
        assertEquals(256, tis.getRecordSize());
    }

    // close(): calling twice must not throw, state remains usable
    @Test
    public void testClose_calledTwice_doesNotThrowAndStateUnaffected() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        tis.close();
        tis.close();
        assertEquals(512, tis.getRecordSize());
    }

    // available(): with no current entry, entrySize/entryOffset are both 0
    @Test
    public void testAvailable_noCurrentEntry_returnsZero() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0, tis.available());
    }

    // available(): right after reading header, equals full entry size
    @Test
    public void testAvailable_afterGetNextEntry_returnsFullEntrySize() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        assertEquals(5, tis.available());
    }

    // available(): after partial read, returns remaining bytes of entry
    @Test
    public void testAvailable_afterPartialRead_returnsRemainingBytes() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        byte[] buf = new byte[2];
        tis.read(buf, 0, 2);
        assertEquals(3, tis.available());
    }

    // available(): when entrySize-entryOffset exceeds Integer.MAX_VALUE, caps at Integer.MAX_VALUE
    @Test
    public void testAvailable_largeEntrySize_returnsIntegerMaxValue() throws Throwable {
        byte[] header = buildHeader("big.bin", 3000000000L, '0');
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(header), 512, 512);
        tis.getNextTarEntry();
        assertEquals(Integer.MAX_VALUE, tis.available());
    }

    // skip(): no current entry means entryOffset(0) >= entrySize(0), read returns -1 immediately
    @Test
    public void testSkip_noCurrentEntry_returnsZero() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0L, tis.skip(100));
    }

    // skip(): requesting zero bytes returns zero without reading
    @Test
    public void testSkip_zeroRequested_returnsZero() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0L, tis.skip(0));
    }

    // skip(): skipping within entry data advances entryOffset by requested amount
    @Test
    public void testSkip_withinEntryData_skipsRequestedBytes() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        long skipped = tis.skip(3);
        assertEquals(3L, skipped);
        assertEquals(2, tis.available());
    }

    // skip(): skipping more than entry size stops at entry boundary
    @Test
    public void testSkip_moreThanEntrySize_capsAtEntryBoundary() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        long skipped = tis.skip(100);
        assertEquals(5L, skipped);
    }

    // reset(): documented as no-op, stream state (available) must stay unchanged
    @Test
    public void testReset_doesNothing_availableUnchanged() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        int before = tis.available();
        tis.reset();
        assertEquals(before, tis.available());
    }

    // getNextTarEntry(): valid header is parsed, returned entry carries the declared size
    @Test
    public void testGetNextTarEntry_validHeader_returnsEntryWithCorrectSize() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        TarArchiveEntry entry = tis.getNextTarEntry();
        assertNotNull(entry);
        assertEquals(5L, entry.getSize());
    }

    // getNextTarEntry(): completely empty underlying stream yields null (EOF)
    @Test
    public void testGetNextTarEntry_emptyStream_returnsNull() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 512, 512);
        assertNull(tis.getNextTarEntry());
    }

    // getNextTarEntry(): a single all-zero record is the EOF marker, returns null
    @Test
    public void testGetNextTarEntry_terminatorRecordOnly_returnsNull() throws Throwable {
        byte[] data = new byte[512];
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(data), 512, 512);
        assertNull(tis.getNextTarEntry());
    }

    // getNextTarEntry(): calling again without reading data skips remaining bytes, then EOF
    @Test
    public void testGetNextTarEntry_afterEntryWithUnreadData_skipsAndReturnsNullAtEOF() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        TarArchiveEntry first = tis.getNextTarEntry();
        assertNotNull(first);
        TarArchiveEntry second = tis.getNextTarEntry();
        assertNull(second);
    }

    // read(): reads entry content fully and matches written bytes
    @Test
    public void testRead_afterGetNextEntry_readsContentCorrectly() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        byte[] buf = new byte[5];
        int n = tis.read(buf, 0, 5);
        assertEquals(5, n);
        assertEquals("Hello", new String(buf, 0, n));
    }

    // read(): requesting more than available caps the read to entry size
    @Test
    public void testRead_requestMoreThanEntrySize_capsToEntrySize() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        byte[] buf = new byte[100];
        int n = tis.read(buf, 0, 100);
        assertEquals(5, n);
    }

    // read(): after fully consuming entry, further read returns -1 (EOF of entry)
    @Test
    public void testRead_atEntryEOF_returnsMinusOne() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        tis.getNextTarEntry();
        byte[] buf = new byte[5];
        tis.read(buf, 0, 5);
        int n = tis.read(buf, 0, 5);
        assertEquals(-1, n);
    }

    // canReadEntryData(): regular (non-GNU-sparse) tar entry is readable
    @Test
    public void testCanReadEntryData_nonSparseTarEntry_returnsTrue() throws Throwable {
        byte[] stream = buildSimpleEntryStream("test.txt", "Hello".getBytes());
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(stream), 512, 512);
        TarArchiveEntry entry = tis.getNextTarEntry();
        assertTrue(tis.canReadEntryData(entry));
    }

    // canReadEntryData(): a null/non-TarArchiveEntry argument is not readable
    @Test
    public void testCanReadEntryData_nullArgument_returnsFalse() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertFalse(tis.canReadEntryData(null));
    }

    // matches(): signature shorter than required header area is never a tar match
    @Test
    public void testMatches_lengthTooShort_returnsFalse() throws Throwable {
        byte[] sig = new byte[10];
        assertFalse(TarArchiveInputStream.matches(sig, 5));
    }

    // matches(): full-length but all-zero buffer does not match any tar magic
    @Test
    public void testMatches_nonTarSignature_returnsFalse() throws Throwable {
        byte[] sig = new byte[512];
        assertFalse(TarArchiveInputStream.matches(sig, 512));
    }

    // parsePaxHeaders(): empty input yields an empty header map (0-round loop)
    @Test
    public void testParsePaxHeaders_emptyInput_returnsEmptyMap() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        Map<String, String> result = tis.parsePaxHeaders(new StringReader(""));
        assertTrue(result.isEmpty());
    }

    // parsePaxHeaders(): single well-formed "length key=value\n" record parses correctly
    @Test
    public void testParsePaxHeaders_singleHeader_parsesKeyValue() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        Map<String, String> result = tis.parsePaxHeaders(new StringReader("6 a=b\n"));
        assertEquals(1, result.size());
        assertEquals("b", result.get("a"));
    }

    // parsePaxHeaders(): multiple concatenated records are all parsed (multi-round loop)
    @Test
    public void testParsePaxHeaders_multipleHeaders_parsesAll() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        Map<String, String> result = tis.parsePaxHeaders(new StringReader("6 a=b\n10 bb=ccc\n"));
        assertEquals("b", result.get("a"));
        assertEquals("ccc", result.get("bb"));
    }

    // parsePaxHeaders(): declared length longer than available value data throws IOException
    @Test
    public void testParsePaxHeaders_malformedLength_throwsIOException() throws Throwable {
        TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            tis.parsePaxHeaders(new StringReader("20 a=b\n"));
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Paxheader"));
        }
    }
}
