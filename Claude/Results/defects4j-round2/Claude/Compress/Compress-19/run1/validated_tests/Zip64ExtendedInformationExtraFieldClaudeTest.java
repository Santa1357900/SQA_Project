package org.apache.commons.compress.archivers.zip;

import java.util.zip.ZipException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.apache.commons.compress.archivers.zip.ZipConstants.DWORD;
import static org.apache.commons.compress.archivers.zip.ZipConstants.WORD;

public class Zip64ExtendedInformationExtraFieldClaudeTest {

    private Zip64ExtendedInformationExtraField field;

    @Before
    public void setUp() throws Throwable {
        field = new Zip64ExtendedInformationExtraField();
    }

    // getHeaderId always returns the static HEADER_ID constant with value 0x0001
    @Test
    public void testGetHeaderId_returnsHeaderIdConstant() throws Throwable {
        assertSame(Zip64ExtendedInformationExtraField.HEADER_ID, field.getHeaderId());
        assertEquals(0x0001, field.getHeaderId().getValue());
    }

    // default (no-arg) constructor leaves all fields null
    @Test
    public void testDefaultConstructor_allFieldsNull() throws Throwable {
        assertNull(field.getSize());
        assertNull(field.getCompressedSize());
        assertNull(field.getRelativeHeaderOffset());
        assertNull(field.getDiskStartNumber());
    }

    // two-arg constructor sets size and compressedSize, leaves others null
    @Test
    public void testTwoArgConstructor_validValues_setsSizeAndCompressedSize() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(100L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(50L);
        Zip64ExtendedInformationExtraField f =
            new Zip64ExtendedInformationExtraField(size, compressed);
        assertEquals(100L, f.getSize().getLongValue());
        assertEquals(50L, f.getCompressedSize().getLongValue());
        assertNull(f.getRelativeHeaderOffset());
        assertNull(f.getDiskStartNumber());
    }





    // four-arg constructor sets all four fields when all are provided
    @Test
    public void testFourArgConstructor_allValues_setsAllFields() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(10L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(20L);
        ZipEightByteInteger rho = new ZipEightByteInteger(30L);
        ZipLong diskStart = new ZipLong(40L);
        Zip64ExtendedInformationExtraField f =
            new Zip64ExtendedInformationExtraField(size, compressed, rho, diskStart);
        assertEquals(10L, f.getSize().getLongValue());
        assertEquals(20L, f.getCompressedSize().getLongValue());
        assertEquals(30L, f.getRelativeHeaderOffset().getLongValue());
        assertEquals(40L, f.getDiskStartNumber().getValue());
    }





    // relativeHeaderOffset and diskStart are optional and may legitimately be null
    @Test
    public void testFourArgConstructor_nullOptionalFields_allowed() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(1L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(2L);
        Zip64ExtendedInformationExtraField f =
            new Zip64ExtendedInformationExtraField(size, compressed, null, null);
        assertNull(f.getRelativeHeaderOffset());
        assertNull(f.getDiskStartNumber());
    }

    // getLocalFileDataLength returns 0 when size is not set
    @Test
    public void testGetLocalFileDataLength_sizeNull_returnsZero() throws Throwable {
        assertEquals(0, field.getLocalFileDataLength().getValue());
    }

    // getLocalFileDataLength returns 2*DWORD when size is set
    @Test
    public void testGetLocalFileDataLength_sizeSet_returnsTwoDwords() throws Throwable {
        field.setSize(new ZipEightByteInteger(5L));
        assertEquals(2 * DWORD, field.getLocalFileDataLength().getValue());
    }

    // getCentralDirectoryLength returns 0 when nothing is set
    @Test
    public void testGetCentralDirectoryLength_allNull_returnsZero() throws Throwable {
        assertEquals(0, field.getCentralDirectoryLength().getValue());
    }

    // getCentralDirectoryLength sums all four field lengths when all are set
    @Test
    public void testGetCentralDirectoryLength_allSet_returnsFullSum() throws Throwable {
        field.setSize(new ZipEightByteInteger(1L));
        field.setCompressedSize(new ZipEightByteInteger(2L));
        field.setRelativeHeaderOffset(new ZipEightByteInteger(3L));
        field.setDiskStartNumber(new ZipLong(4L));
        assertEquals(3 * DWORD + WORD, field.getCentralDirectoryLength().getValue());
    }

    // getCentralDirectoryLength with only diskStart set returns WORD
    @Test
    public void testGetCentralDirectoryLength_onlyDiskStartSet_returnsWord() throws Throwable {
        field.setDiskStartNumber(new ZipLong(7L));
        assertEquals(WORD, field.getCentralDirectoryLength().getValue());
    }

    // getLocalFileDataData returns an empty array when neither size nor compressedSize is set
    @Test
    public void testGetLocalFileDataData_bothNull_returnsEmptyArray() throws Throwable {
        assertEquals(0, field.getLocalFileDataData().length);
    }

    // getLocalFileDataData returns correctly ordered bytes when both sizes are set
    @Test
    public void testGetLocalFileDataData_bothSet_returnsConcatenatedBytes() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(0x0102030405060708L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(0x1112131415161718L);
        field.setSize(size);
        field.setCompressedSize(compressed);
        byte[] data = field.getLocalFileDataData();
        assertEquals(2 * DWORD, data.length);
        byte[] expected = concat(size.getBytes(), compressed.getBytes());
        assertArrayEquals(expected, data);
    }

    // Javadoc: LFH must include BOTH sizes - only size set must throw
    @Test
    public void testGetLocalFileDataData_onlySizeSet_throwsIllegalArgumentException() throws Throwable {
        field.setSize(new ZipEightByteInteger(1L));
        try {
            field.getLocalFileDataData();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Javadoc: LFH must include BOTH sizes - only compressedSize set must throw
    @Test
    public void testGetLocalFileDataData_onlyCompressedSizeSet_throwsIllegalArgumentException() throws Throwable {
        field.setCompressedSize(new ZipEightByteInteger(1L));
        try {
            field.getLocalFileDataData();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getCentralDirectoryData with all four fields produces bytes in size,compressedSize,rho,diskStart order
    @Test
    public void testGetCentralDirectoryData_allFieldsSet_correctLayout() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(11L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(22L);
        ZipEightByteInteger rho = new ZipEightByteInteger(33L);
        ZipLong diskStart = new ZipLong(44L);
        field.setSize(size);
        field.setCompressedSize(compressed);
        field.setRelativeHeaderOffset(rho);
        field.setDiskStartNumber(diskStart);
        byte[] expected = concat(concat(size.getBytes(), compressed.getBytes()),
                                  concat(rho.getBytes(), diskStart.getBytes()));
        assertArrayEquals(expected, field.getCentralDirectoryData());
    }

    // getCentralDirectoryData with only diskStart set produces a WORD-sized array
    @Test
    public void testGetCentralDirectoryData_onlyDiskStartSet_returnsWordBytes() throws Throwable {
        ZipLong diskStart = new ZipLong(9L);
        field.setDiskStartNumber(diskStart);
        assertArrayEquals(diskStart.getBytes(), field.getCentralDirectoryData());
    }

    // parseFromLocalFileData with length 0 is a no-op (fields remain null)
    @Test
    public void testParseFromLocalFileData_lengthZero_doesNothing() throws Throwable {
        field.parseFromLocalFileData(new byte[0], 0, 0);
        assertNull(field.getSize());
        assertNull(field.getCompressedSize());
    }

    // parseFromLocalFileData with length < 2*DWORD must throw ZipException per javadoc contract
    @Test
    public void testParseFromLocalFileData_tooShort_throwsZipException() throws Throwable {
        byte[] buffer = new byte[DWORD];
        try {
            field.parseFromLocalFileData(buffer, 0, DWORD);
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("both size"));
        }
    }

    // parseFromLocalFileData with exactly 2*DWORD sets only the two sizes
    @Test
    public void testParseFromLocalFileData_exactTwoDwords_setsSizesOnly() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(123L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(456L);
        byte[] buffer = concat(size.getBytes(), compressed.getBytes());
        field.parseFromLocalFileData(buffer, 0, buffer.length);
        assertEquals(123L, field.getSize().getLongValue());
        assertEquals(456L, field.getCompressedSize().getLongValue());
        assertNull(field.getRelativeHeaderOffset());
        assertNull(field.getDiskStartNumber());
    }

    // parseFromLocalFileData with 3*DWORD also sets relativeHeaderOffset
    @Test
    public void testParseFromLocalFileData_threeDwords_setsRelativeHeaderOffset() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(1L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(2L);
        ZipEightByteInteger rho = new ZipEightByteInteger(3L);
        byte[] buffer = concat(concat(size.getBytes(), compressed.getBytes()), rho.getBytes());
        field.parseFromLocalFileData(buffer, 0, buffer.length);
        assertEquals(3L, field.getRelativeHeaderOffset().getLongValue());
        assertNull(field.getDiskStartNumber());
    }

    // parseFromLocalFileData with 3*DWORD+WORD also sets diskStart
    @Test
    public void testParseFromLocalFileData_fullLength_setsDiskStart() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(1L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(2L);
        ZipEightByteInteger rho = new ZipEightByteInteger(3L);
        ZipLong diskStart = new ZipLong(4L);
        byte[] buffer = concat(concat(size.getBytes(), compressed.getBytes()),
                                concat(rho.getBytes(), diskStart.getBytes()));
        field.parseFromLocalFileData(buffer, 0, buffer.length);
        assertEquals(4L, field.getDiskStartNumber().getValue());
    }

    // parseFromLocalFileData respects a non-zero offset into a larger buffer
    @Test
    public void testParseFromLocalFileData_withOffset_readsFromOffset() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(77L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(88L);
        byte[] payload = concat(size.getBytes(), compressed.getBytes());
        byte[] buffer = concat(new byte[]{0, 0, 0}, payload);
        field.parseFromLocalFileData(buffer, 3, payload.length);
        assertEquals(77L, field.getSize().getLongValue());
        assertEquals(88L, field.getCompressedSize().getLongValue());
    }

    // parseFromCentralDirectoryData with full length (>= 3*DWORD+WORD) delegates to local-file parsing
    @Test
    public void testParseFromCentralDirectoryData_fullLength_parsesAllFields() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(1L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(2L);
        ZipEightByteInteger rho = new ZipEightByteInteger(3L);
        ZipLong diskStart = new ZipLong(4L);
        byte[] buffer = concat(concat(size.getBytes(), compressed.getBytes()),
                                concat(rho.getBytes(), diskStart.getBytes()));
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        assertEquals(1L, field.getSize().getLongValue());
        assertEquals(2L, field.getCompressedSize().getLongValue());
        assertEquals(3L, field.getRelativeHeaderOffset().getLongValue());
        assertEquals(4L, field.getDiskStartNumber().getValue());
    }

    // parseFromCentralDirectoryData with length == 3*DWORD sets size, compressedSize and relativeHeaderOffset
    @Test
    public void testParseFromCentralDirectoryData_threeDwords_setsThreeFields() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(5L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(6L);
        ZipEightByteInteger rho = new ZipEightByteInteger(7L);
        byte[] buffer = concat(concat(size.getBytes(), compressed.getBytes()), rho.getBytes());
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        assertEquals(5L, field.getSize().getLongValue());
        assertEquals(6L, field.getCompressedSize().getLongValue());
        assertEquals(7L, field.getRelativeHeaderOffset().getLongValue());
        assertNull(field.getDiskStartNumber());
    }

    // parseFromCentralDirectoryData with length % DWORD == WORD only sets diskStart
    @Test
    public void testParseFromCentralDirectoryData_lengthModDwordEqualsWord_setsDiskStartOnly() throws Throwable {
        ZipLong diskStart = new ZipLong(99L);
        byte[] buffer = diskStart.getBytes();
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        assertEquals(99L, field.getDiskStartNumber().getValue());
        assertNull(field.getSize());
    }

    // parseFromCentralDirectoryData with an undetectable length leaves all fields null
    @Test
    public void testParseFromCentralDirectoryData_unrecognizedLength_leavesFieldsNull() throws Throwable {
        byte[] buffer = new byte[2 * DWORD];
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        assertNull(field.getSize());
        assertNull(field.getCompressedSize());
        assertNull(field.getDiskStartNumber());
    }

    // reparseCentralDirectoryData with matching flags/length correctly reconstructs all fields
    @Test
    public void testReparseCentralDirectoryData_allFlagsTrue_matchingLength_setsAllFields() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(1L);
        ZipEightByteInteger compressed = new ZipEightByteInteger(2L);
        ZipEightByteInteger rho = new ZipEightByteInteger(3L);
        ZipLong diskStart = new ZipLong(4L);
        byte[] buffer = concat(concat(size.getBytes(), compressed.getBytes()),
                                concat(rho.getBytes(), diskStart.getBytes()));
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        field.reparseCentralDirectoryData(true, true, true, true);
        assertEquals(1L, field.getSize().getLongValue());
        assertEquals(2L, field.getCompressedSize().getLongValue());
        assertEquals(3L, field.getRelativeHeaderOffset().getLongValue());
        assertEquals(4L, field.getDiskStartNumber().getValue());
    }

    // reparseCentralDirectoryData with mismatched expected length throws ZipException
    @Test
    public void testReparseCentralDirectoryData_lengthMismatch_throwsZipException() throws Throwable {
        byte[] buffer = new byte[DWORD];
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        try {
            field.reparseCentralDirectoryData(true, true, false, false);
            fail("expected ZipException");
        } catch (ZipException expected) {
            assertTrue(expected.getMessage().contains("Expected length"));
        }
    }

    // reparseCentralDirectoryData with only hasUncompressedSize true parses a single field
    @Test
    public void testReparseCentralDirectoryData_onlyUncompressedSizeFlag_setsSizeOnly() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(42L);
        byte[] buffer = size.getBytes();
        field.parseFromCentralDirectoryData(buffer, 0, buffer.length);
        field.reparseCentralDirectoryData(true, false, false, false);
        assertEquals(42L, field.getSize().getLongValue());
        assertNull(field.getCompressedSize());
    }

    // reparseCentralDirectoryData when rawCentralDirectoryData was never populated is a no-op
    @Test
    public void testReparseCentralDirectoryData_noRawData_isNoop() throws Throwable {
        field.reparseCentralDirectoryData(true, true, true, true);
        assertNull(field.getSize());
        assertNull(field.getCompressedSize());
    }

    // setSize/getSize round trip
    @Test
    public void testSetSizeGetSize_roundTrip() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(555L);
        field.setSize(size);
        assertSame(size, field.getSize());
    }

    // setCompressedSize/getCompressedSize round trip
    @Test
    public void testSetCompressedSizeGetCompressedSize_roundTrip() throws Throwable {
        ZipEightByteInteger compressed = new ZipEightByteInteger(666L);
        field.setCompressedSize(compressed);
        assertSame(compressed, field.getCompressedSize());
    }

    // setRelativeHeaderOffset/getRelativeHeaderOffset round trip
    @Test
    public void testSetRelativeHeaderOffsetGetRelativeHeaderOffset_roundTrip() throws Throwable {
        ZipEightByteInteger rho = new ZipEightByteInteger(777L);
        field.setRelativeHeaderOffset(rho);
        assertSame(rho, field.getRelativeHeaderOffset());
    }

    // setDiskStartNumber/getDiskStartNumber round trip
    @Test
    public void testSetDiskStartNumberGetDiskStartNumber_roundTrip() throws Throwable {
        ZipLong diskStart = new ZipLong(888L);
        field.setDiskStartNumber(diskStart);
        assertSame(diskStart, field.getDiskStartNumber());
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
