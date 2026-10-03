package org.apache.commons.compress.archivers.zip;

import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

public class X5455_ExtendedTimestampClaudeTest {

    // Default constructor: all presence bits false and all time fields null
    @Test
    public void testConstructor_defaultState_allFalseAndNullTimes() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        assertFalse(xf.isBit0_modifyTimePresent());
        assertFalse(xf.isBit1_accessTimePresent());
        assertFalse(xf.isBit2_createTimePresent());
        assertNull(xf.getModifyTime());
        assertEquals(0, xf.getFlags());
    }

    // getHeaderId must always return the fixed 0x5455 tag
    @Test
    public void testGetHeaderId_returnsFixedHeaderValue() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        assertEquals(0x5455, xf.getHeaderId().getValue());
    }

    // getLocalFileDataLength: no timestamps -> just the flags byte (length 1)
    @Test
    public void testGetLocalFileDataLength_noTimestampsPresent_returnsOne() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        assertEquals(1, xf.getLocalFileDataLength().getValue());
    }

    // getLocalFileDataLength: modify time only -> flags byte + 4 bytes
    @Test
    public void testGetLocalFileDataLength_modifyOnly_returnsFive() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(1L));
        assertEquals(5, xf.getLocalFileDataLength().getValue());
    }

    // getLocalFileDataLength: all three timestamps -> 1 + 4*3 = 13
    @Test
    public void testGetLocalFileDataLength_allThreePresent_returnsThirteen() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(1L));
        xf.setAccessTime(new ZipLong(2L));
        xf.setCreateTime(new ZipLong(3L));
        assertEquals(13, xf.getLocalFileDataLength().getValue());
    }

    // access bit set via flags but accessTime never assigned -> excluded from length (&&-guard branch)
    @Test
    public void testGetLocalFileDataLength_accessBitSetButTimeNull_excludesAccessBytes() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setFlags(X5455_ExtendedTimestamp.ACCESS_TIME_BIT);
        assertEquals(1, xf.getLocalFileDataLength().getValue());
    }

    // create bit set via flags but createTime never assigned -> excluded from length (&&-guard branch)
    @Test
    public void testGetLocalFileDataLength_createBitSetButTimeNull_excludesCreateBytes() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setFlags(X5455_ExtendedTimestamp.CREATE_TIME_BIT);
        assertEquals(1, xf.getLocalFileDataLength().getValue());
    }

    // getCentralDirectoryLength: no modify time -> 1
    @Test
    public void testGetCentralDirectoryLength_noModify_returnsOne() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        assertEquals(1, xf.getCentralDirectoryLength().getValue());
    }

    // getCentralDirectoryLength ignores access/create presence, only modify counts -> 5
    @Test
    public void testGetCentralDirectoryLength_withModifyIgnoresAccessCreate_returnsFive() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(1L));
        xf.setAccessTime(new ZipLong(2L));
        xf.setCreateTime(new ZipLong(3L));
        assertEquals(5, xf.getCentralDirectoryLength().getValue());
    }

    // getLocalFileDataData: flags byte must encode all three presence bits (1|2|4=7)
    @Test
    public void testGetLocalFileDataData_flagByteEncodesAllPresentBits() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(1L));
        xf.setAccessTime(new ZipLong(2L));
        xf.setCreateTime(new ZipLong(3L));
        byte[] data = xf.getLocalFileDataData();
        assertEquals(7, data[0]);
    }

    // getLocalFileDataData: byte layout after flags byte must match each ZipLong's bytes in order
    @Test
    public void testGetLocalFileDataData_byteLayoutMatchesTimestamps() throws Throwable {
        ZipLong mod = new ZipLong(111L);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(mod);
        byte[] data = xf.getLocalFileDataData();
        byte[] modBytes = mod.getBytes();
        for (int i = 0; i < 4; i++) {
            assertEquals(modBytes[i], data[1 + i]);
        }
    }

    // getCentralDirectoryData truncates to flags+modTime but flags byte still carries all set bits
    @Test
    public void testGetCentralDirectoryData_truncatesButKeepsFullFlagsByte() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(9L));
        xf.setAccessTime(new ZipLong(8L));
        xf.setCreateTime(new ZipLong(7L));
        byte[] central = xf.getCentralDirectoryData();
        assertEquals(5, central.length);
        assertEquals(7, central[0]);
    }

    // parseFromLocalFileData with all three timestamps present parses every field correctly
    @Test
    public void testParseFromLocalFileData_allThreeTimestamps_parsedCorrectly() throws Throwable {
        byte[] data = new byte[13];
        data[0] = 7;
        System.arraycopy(new ZipLong(1000L).getBytes(), 0, data, 1, 4);
        System.arraycopy(new ZipLong(2000L).getBytes(), 0, data, 5, 4);
        System.arraycopy(new ZipLong(3000L).getBytes(), 0, data, 9, 4);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.parseFromLocalFileData(data, 0, 13);
        assertEquals(1000, xf.getModifyTime().getIntValue());
        assertEquals(2000, xf.getAccessTime().getIntValue());
        assertEquals(3000, xf.getCreateTime().getIntValue());
    }

    // parseFromLocalFileData honors a non-zero start offset
    @Test
    public void testParseFromLocalFileData_withNonZeroOffset_parsesCorrectSlice() throws Throwable {
        byte[] data = new byte[16];
        data[0] = 9; data[1] = 9; data[2] = 9;
        data[3] = 7;
        System.arraycopy(new ZipLong(10L).getBytes(), 0, data, 4, 4);
        System.arraycopy(new ZipLong(20L).getBytes(), 0, data, 8, 4);
        System.arraycopy(new ZipLong(30L).getBytes(), 0, data, 12, 4);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.parseFromLocalFileData(data, 3, 13);
        assertEquals(10, xf.getModifyTime().getIntValue());
        assertEquals(30, xf.getCreateTime().getIntValue());
    }

    // central-like short data: flags claim access/create present but bytes absent -> stay null
    @Test
    public void testParseFromLocalFileData_shortCentralLikeData_accessCreateStayNull() throws Throwable {
        byte[] data = new byte[5];
        data[0] = 7;
        System.arraycopy(new ZipLong(1000L).getBytes(), 0, data, 1, 4);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.parseFromLocalFileData(data, 0, 5);
        assertTrue(xf.isBit1_accessTimePresent());
        assertNull(xf.getAccessTime());
        assertNull(xf.getCreateTime());
        assertEquals(1000, xf.getModifyTime().getIntValue());
    }

    // calling parseFromLocalFileData again resets previous state cleanly
    @Test
    public void testParseFromLocalFileData_calledTwice_resetsPreviousState() throws Throwable {
        byte[] data = new byte[13];
        data[0] = 7;
        System.arraycopy(new ZipLong(1L).getBytes(), 0, data, 1, 4);
        System.arraycopy(new ZipLong(2L).getBytes(), 0, data, 5, 4);
        System.arraycopy(new ZipLong(3L).getBytes(), 0, data, 9, 4);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.parseFromLocalFileData(data, 0, 13);
        xf.parseFromLocalFileData(new byte[]{0}, 0, 1);
        assertEquals(0, xf.getFlags());
        assertFalse(xf.isBit0_modifyTimePresent());
        assertNull(xf.getModifyTime());
        assertNull(xf.getAccessTime());
    }

    // parseFromCentralDirectoryData behaves identically to parseFromLocalFileData for short data
    @Test
    public void testParseFromCentralDirectoryData_behavesLikeLocalParse() throws Throwable {
        byte[] data = new byte[5];
        data[0] = 7;
        System.arraycopy(new ZipLong(555L).getBytes(), 0, data, 1, 4);
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.parseFromCentralDirectoryData(data, 0, 5);
        assertEquals(555, xf.getModifyTime().getIntValue());
        assertTrue(xf.isBit2_createTimePresent());
        assertNull(xf.getCreateTime());
    }

    // setFlags decodes each presence bit independently
    @Test
    public void testSetFlags_getFlags_bitFlagsDecodedCorrectly() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        byte flags = (byte) (X5455_ExtendedTimestamp.MODIFY_TIME_BIT | X5455_ExtendedTimestamp.CREATE_TIME_BIT);
        xf.setFlags(flags);
        assertEquals(flags, xf.getFlags());
        assertTrue(xf.isBit0_modifyTimePresent());
        assertFalse(xf.isBit1_accessTimePresent());
        assertTrue(xf.isBit2_createTimePresent());
    }

    // setModifyTime(null) clears both the bit and the flag byte
    @Test
    public void testSetModifyTime_nullClearsBitAndFlag() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(5L));
        assertTrue(xf.isBit0_modifyTimePresent());
        xf.setModifyTime(null);
        assertFalse(xf.isBit0_modifyTimePresent());
        assertNull(xf.getModifyTime());
        assertEquals(0, xf.getFlags() & X5455_ExtendedTimestamp.MODIFY_TIME_BIT);
    }

    // setAccessTime with non-null value sets the bit and the access flag
    @Test
    public void testSetAccessTime_nonNullSetsBitAndFlag() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setAccessTime(new ZipLong(77L));
        assertTrue(xf.isBit1_accessTimePresent());
        assertEquals(77, xf.getAccessTime().getIntValue());
        assertEquals(X5455_ExtendedTimestamp.ACCESS_TIME_BIT, xf.getFlags());
    }

    // setCreateTime with non-null value sets the bit and the create flag
    @Test
    public void testSetCreateTime_nonNullSetsBitAndFlag() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setCreateTime(new ZipLong(88L));
        assertTrue(xf.isBit2_createTimePresent());
        assertEquals(88, xf.getCreateTime().getIntValue());
        assertEquals(X5455_ExtendedTimestamp.CREATE_TIME_BIT, xf.getFlags());
    }

    // getModifyJavaTime returns null when no modify time has been set
    @Test
    public void testGetModifyJavaTime_nullWhenTimeAbsent() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        assertNull(xf.getModifyJavaTime());
    }

    // getModifyJavaTime converts the stored seconds into milliseconds
    @Test
    public void testGetModifyJavaTime_convertsSecondsToMilliseconds() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(100L));
        assertEquals(100000L, xf.getModifyJavaTime().getTime());
    }

    // getAccessJavaTime converts the stored seconds into milliseconds
    @Test
    public void testGetAccessJavaTime_convertsSecondsToMilliseconds() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setAccessTime(new ZipLong(200L));
        assertEquals(200000L, xf.getAccessJavaTime().getTime());
    }

    // getCreateJavaTime converts the stored seconds into milliseconds
    @Test
    public void testGetCreateJavaTime_convertsSecondsToMilliseconds() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setCreateTime(new ZipLong(300L));
        assertEquals(300000L, xf.getCreateJavaTime().getTime());
    }

    // setModifyJavaTime truncates sub-second milliseconds (per-second precision contract)
    @Test
    public void testSetModifyJavaTime_truncatesSubSecondMillis() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyJavaTime(new Date(1999L));
        assertEquals(1000L, xf.getModifyJavaTime().getTime());
    }

    // setAccessJavaTime(null) clears the access time and its presence bit
    @Test
    public void testSetAccessJavaTime_nullClearsAccessTime() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setAccessJavaTime(new Date(5000L));
        assertTrue(xf.isBit1_accessTimePresent());
        xf.setAccessJavaTime(null);
        assertFalse(xf.isBit1_accessTimePresent());
        assertNull(xf.getAccessTime());
    }

    // setCreateJavaTime sets create time derived from the given Date
    @Test
    public void testSetCreateJavaTime_setsCreateTimeFromDate() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setCreateJavaTime(new Date(3000L));
        assertTrue(xf.isBit2_createTimePresent());
        assertEquals(3000L, xf.getCreateJavaTime().getTime());
    }

    // boundary: exactly Integer.MAX_VALUE seconds is the largest valid signed 32-bit value, must not throw
    @Test
    public void testSetModifyJavaTime_atSignedMaxBoundary_doesNotThrow() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        long seconds = 2147483647L;
        xf.setModifyJavaTime(new Date(seconds * 1000L));
        assertEquals(seconds, xf.getModifyJavaTime().getTime() / 1000L);
    }

    // BUG: exception message states "signed 32 bit integer" but the range check only rejects values >= 2^32,
    // so a value beyond the signed max (2147483647) but below 2^32 must still be rejected per the documented contract
    @Test
    public void testSetModifyJavaTime_exceedsSignedRange_throwsIllegalArgumentException() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        long seconds = 3000000000L;
        try {
            xf.setModifyJavaTime(new Date(seconds * 1000L));
            fail("expected IllegalArgumentException for value exceeding signed 32 bit integer range");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("signed 32 bit"));
        }
    }

    // value at exactly 2^32 seconds must throw per the existing explicit boundary check
    @Test
    public void testSetModifyJavaTime_atTwoToThirtyTwo_throwsIllegalArgumentException() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        long seconds = 4294967296L;
        try {
            xf.setModifyJavaTime(new Date(seconds * 1000L));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected per explicit TWO_TO_32 boundary check
        }
    }

    // clone() produces a distinct but equal copy
    @Test
    public void testClone_returnsEqualButDistinctInstance() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(42L));
        Object clone = xf.clone();
        assertTrue(clone instanceof X5455_ExtendedTimestamp);
        X5455_ExtendedTimestamp xfClone = (X5455_ExtendedTimestamp) clone;
        assertNotSame(xf, xfClone);
        assertEquals(xf, xfClone);
    }

    // equals() only considers the lowest 3 bits of flags, ignoring any higher bits
    @Test
    public void testEquals_ignoresBitsBeyondLowestThree() throws Throwable {
        X5455_ExtendedTimestamp a = new X5455_ExtendedTimestamp();
        a.setFlags((byte) 0x07);
        X5455_ExtendedTimestamp b = new X5455_ExtendedTimestamp();
        b.setFlags((byte) 0x87);
        assertTrue(a.equals(b));
    }

    // equals() returns false when modify times differ
    @Test
    public void testEquals_falseWhenModifyTimeDiffers() throws Throwable {
        X5455_ExtendedTimestamp a = new X5455_ExtendedTimestamp();
        a.setModifyTime(new ZipLong(1L));
        X5455_ExtendedTimestamp b = new X5455_ExtendedTimestamp();
        b.setModifyTime(new ZipLong(2L));
        assertFalse(a.equals(b));
    }

    // equals() returns false for an unrelated type and for null, never throwing
    @Test
    public void testEquals_falseForNonMatchingTypeOrNull() throws Throwable {
        X5455_ExtendedTimestamp a = new X5455_ExtendedTimestamp();
        assertFalse(a.equals(new Object()));
        assertFalse(a.equals(null));
    }

    // equal objects must produce the same hashCode
    @Test
    public void testHashCode_equalObjectsHaveSameHashCode() throws Throwable {
        X5455_ExtendedTimestamp a = new X5455_ExtendedTimestamp();
        a.setModifyTime(new ZipLong(99L));
        X5455_ExtendedTimestamp b = new X5455_ExtendedTimestamp();
        b.setModifyTime(new ZipLong(99L));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // toString includes a label for each present timestamp
    @Test
    public void testToString_includesLabelsForPresentTimestamps() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        xf.setModifyTime(new ZipLong(1L));
        xf.setAccessTime(new ZipLong(2L));
        xf.setCreateTime(new ZipLong(3L));
        String s = xf.toString();
        assertTrue(s.contains("Modify:"));
        assertTrue(s.contains("Access:"));
        assertTrue(s.contains("Create:"));
    }

    // toString omits all timestamp labels when none are present
    @Test
    public void testToString_omitsLabelsWhenTimestampsAbsent() throws Throwable {
        X5455_ExtendedTimestamp xf = new X5455_ExtendedTimestamp();
        String s = xf.toString();
        assertFalse(s.contains("Modify:"));
        assertFalse(s.contains("Access:"));
        assertFalse(s.contains("Create:"));
    }
}
