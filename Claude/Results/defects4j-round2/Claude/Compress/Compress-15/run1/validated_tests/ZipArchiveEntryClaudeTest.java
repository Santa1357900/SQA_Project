package org.apache.commons.compress.archivers.zip;

import java.util.Date;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZipArchiveEntryClaudeTest {

    // Constructor(String): name is stored verbatim (no slash, no backslash)
    @Test
    public void testConstructorString_setsName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("plain.txt");
        assertEquals("plain.txt", entry.getName());
    }

    // Constructor(String): name ending with "/" => isDirectory() true
    @Test
    public void testConstructorString_trailingSlash_isDirectoryTrue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir/");
        assertTrue(entry.isDirectory());
    }

    // Constructor(String): name without trailing slash => isDirectory() false
    @Test
    public void testConstructorString_noTrailingSlash_isDirectoryFalse() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        assertFalse(entry.isDirectory());
    }

    // protected no-arg constructor delegates to ("") -> empty name
    @Test
    public void testDefaultConstructor_emptyName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry();
        assertEquals("", entry.getName());
    }

    // Constructor(ZipEntry): copies name, method and size
    @Test
    public void testConstructorZipEntry_copiesNameMethodSize() throws Throwable {
        ZipEntry plain = new ZipEntry("data.txt");
        plain.setMethod(ZipEntry.DEFLATED);
        plain.setSize(123L);
        ZipArchiveEntry entry = new ZipArchiveEntry(plain);
        assertEquals("data.txt", entry.getName());
        assertEquals(ZipEntry.DEFLATED, entry.getMethod());
        assertEquals(123L, entry.getSize());
    }

    // Constructor(ZipEntry): method unspecified (-1) propagates through setMethod and throws
    @Test
    public void testConstructorZipEntry_methodUnset_throwsIllegalArgumentException() throws Throwable {
        ZipEntry plain = new ZipEntry("unset.txt");
        try {
            new ZipArchiveEntry(plain);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // Constructor(ZipArchiveEntry): copies internal/external attributes and name
    @Test
    public void testConstructorZipArchiveEntry_copiesAttributes() throws Throwable {
        ZipArchiveEntry original = new ZipArchiveEntry("orig.txt");
        original.setMethod(ZipEntry.STORED);
        original.setInternalAttributes(7);
        original.setExternalAttributes(999L);
        ZipArchiveEntry copy = new ZipArchiveEntry(original);
        assertEquals("orig.txt", copy.getName());
        assertEquals(7, copy.getInternalAttributes());
        assertEquals(999L, copy.getExternalAttributes());
    }

    // clone(): independent object, equal content
    @Test
    public void testClone_producesEqualIndependentCopy() throws Throwable {
        ZipArchiveEntry original = new ZipArchiveEntry("clone.txt");
        original.setInternalAttributes(3);
        original.setExternalAttributes(55L);
        Object cloned = original.clone();
        assertTrue(cloned instanceof ZipArchiveEntry);
        ZipArchiveEntry clonedEntry = (ZipArchiveEntry) cloned;
        assertNotSame(original, clonedEntry);
        assertEquals(original, clonedEntry);
        assertEquals(3, clonedEntry.getInternalAttributes());
    }

    // getMethod(): default is -1 (unspecified) per javadoc
    @Test
    public void testGetMethod_defaultIsMinusOne() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("m.txt");
        assertEquals(-1, entry.getMethod());
    }

    // setMethod(): valid non-negative value is stored and returned
    @Test
    public void testSetMethod_validValue_updatesMethod() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("m2.txt");
        entry.setMethod(8);
        assertEquals(8, entry.getMethod());
    }

    // setMethod(): negative value throws IllegalArgumentException
    @Test
    public void testSetMethod_negativeValue_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("m3.txt");
        try {
            entry.setMethod(-5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    // internalAttributes: default 0, settable
    @Test
    public void testGetSetInternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ia.txt");
        assertEquals(0, entry.getInternalAttributes());
        entry.setInternalAttributes(42);
        assertEquals(42, entry.getInternalAttributes());
    }

    // externalAttributes: default 0, settable
    @Test
    public void testGetSetExternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ea.txt");
        assertEquals(0L, entry.getExternalAttributes());
        entry.setExternalAttributes(1234L);
        assertEquals(1234L, entry.getExternalAttributes());
    }

    // setUnixMode(): sets platform to UNIX and encodes mode in high 16 bits
    @Test
    public void testSetUnixMode_normalMode_setsPlatformAndMode() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        assertEquals(0, entry.getUnixMode());
        entry.setUnixMode(0755);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
        assertEquals(0755, entry.getUnixMode());
    }

    // setUnixMode(): write-bit off (mode & 0200 == 0) sets MS-DOS read-only bit
    @Test
    public void testSetUnixMode_readOnlyBit() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ro.txt");
        entry.setUnixMode(0444);
        assertEquals(1L, entry.getExternalAttributes() & 1L);
    }

    // setUnixMode(): directory entry sets MS-DOS directory flag 0x10
    @Test
    public void testSetUnixMode_directoryFlag() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir/");
        entry.setUnixMode(0755);
        assertTrue((entry.getExternalAttributes() & 0x10) != 0);
    }

    // getPlatform(): defaults to PLATFORM_FAT, settable via protected setPlatform
    @Test
    public void testGetPlatform_defaultAndSettable() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("p.txt");
        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry.getPlatform());
        entry.setPlatform(ZipArchiveEntry.PLATFORM_UNIX);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
    }

    // setExtraFields(empty array): clears extra fields, mergeLocalFileDataData yields none
    @Test
    public void testSetExtraFields_emptyArray_noExtraFields() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e.txt");
        entry.setExtraFields(new ZipExtraField[0]);
        assertEquals(0, entry.getExtraFields().length);
        assertEquals(0, entry.getExtraFields(true).length);
    }

    // getExtraFields(): default (no fields set) returns empty array
    @Test
    public void testGetExtraFields_defaultEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e2.txt");
        assertEquals(0, entry.getExtraFields().length);
    }

    // getExtraField(): no fields present => null regardless of lookup type
    @Test
    public void testGetExtraField_withNullType_returnsNullWhenNoFields() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e3.txt");
        assertNull(entry.getExtraField(null));
    }

    // removeExtraField(): no extra fields present => NoSuchElementException
    @Test
    public void testRemoveExtraField_whenNone_throwsNoSuchElementException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e4.txt");
        try {
            entry.removeExtraField(null);
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            // expected
        }
    }

    // removeUnparseableExtraFieldData(): none present => NoSuchElementException
    @Test
    public void testRemoveUnparseableExtraFieldData_whenNone_throwsNoSuchElementException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e5.txt");
        try {
            entry.removeUnparseableExtraFieldData();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            // expected
        }
    }

    // getUnparseableExtraFieldData(): default null
    @Test
    public void testGetUnparseableExtraFieldData_defaultNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e6.txt");
        assertNull(entry.getUnparseableExtraFieldData());
    }

    // setExtra(empty): results in empty local file data extra
    @Test
    public void testSetExtra_emptyArray_resultsInEmptyExtra() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e7.txt");
        entry.setExtra(new byte[0]);
        assertEquals(0, entry.getLocalFileDataExtra().length);
    }

    // setCentralDirectoryExtra(empty): results in empty central directory extra
    @Test
    public void testSetCentralDirectoryExtra_emptyArray_resultsInEmptyExtra() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e8.txt");
        entry.setCentralDirectoryExtra(new byte[0]);
        assertEquals(0, entry.getCentralDirectoryExtra().length);
    }

    // default entry: both local and central extra are empty, never null
    @Test
    public void testGetLocalAndCentralExtra_defaultsEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e9.txt");
        assertNotNull(entry.getLocalFileDataExtra());
        assertEquals(0, entry.getLocalFileDataExtra().length);
        assertNotNull(entry.getCentralDirectoryExtra());
        assertEquals(0, entry.getCentralDirectoryExtra().length);
    }

    // setName(): backslash replaced by forward slash when name has no "/" and platform is FAT
    @Test
    public void testGetName_backslashReplaced_whenNoSlashPresentOnFatPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a\\b");
        assertEquals("a/b", entry.getName());
    }

    // setName(): backslash NOT replaced when a "/" is already present in the name
    @Test
    public void testGetName_backslashNotReplaced_whenSlashAlreadyPresent() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a/b\\c");
        assertEquals("a/b\\c", entry.getName());
    }

    // setName(): backslash NOT replaced when platform is not FAT (e.g. UNIX)
    @Test
    public void testGetName_backslashNotReplaced_whenPlatformUnix() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry();
        entry.setPlatform(ZipArchiveEntry.PLATFORM_UNIX);
        entry.setName("x\\y");
        assertEquals("x\\y", entry.getName());
    }

    // setName(String,byte[]): getRawName returns a defensive copy equal in content
    @Test
    public void testSetNameWithRawName_getRawNameReturnsCopy() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry();
        byte[] raw = new byte[] {1, 2, 3};
        entry.setName("raw.txt", raw);
        byte[] got = entry.getRawName();
        assertNotNull(got);
        assertArrayEquals(raw, got);
        assertNotSame(raw, got);
    }

    // getRawName(): default null when entry was not read from raw bytes
    @Test
    public void testGetRawName_defaultNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("noraw.txt");
        assertNull(entry.getRawName());
    }

    // getSize(): default is SIZE_UNKNOWN per ArchiveEntry contract
    @Test
    public void testGetSize_defaultUnknown() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("size.txt");
        assertEquals(ArchiveEntry.SIZE_UNKNOWN, entry.getSize());
    }

    // setSize(): negative value throws IllegalArgumentException
    @Test
    public void testSetSize_negative_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("neg.txt");
        try {
            entry.setSize(-1L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("invalid"));
        }
    }

    // setSize(): zero is a valid boundary value
    @Test
    public void testSetSize_zeroBoundary_allowed() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("zero.txt");
        entry.setSize(0L);
        assertEquals(0L, entry.getSize());
    }

    // setSize(): regular positive value is stored
    @Test
    public void testSetSize_validValue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("pos.txt");
        entry.setSize(500L);
        assertEquals(500L, entry.getSize());
    }

    // hashCode(): uses the entry name's hashCode per javadoc
    @Test
    public void testHashCode_equalsNameHashCode() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("hash.txt");
        assertEquals("hash.txt".hashCode(), entry.hashCode());
    }

    // getGeneralPurposeBit(): never null by default
    @Test
    public void testGetGeneralPurposeBit_defaultNotNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("gp.txt");
        assertNotNull(entry.getGeneralPurposeBit());
    }

    // setGeneralPurposeBit()/getGeneralPurposeBit(): round trips the same instance
    @Test
    public void testSetGeneralPurposeBit_returnsSameInstance() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("gp2.txt");
        GeneralPurposeBit bit = new GeneralPurposeBit();
        entry.setGeneralPurposeBit(bit);
        assertSame(bit, entry.getGeneralPurposeBit());
    }

    // getLastModifiedDate(): wraps getTime() in a Date
    @Test
    public void testGetLastModifiedDate_matchesGetTime() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("date.txt");
        entry.setTime(100000L);
        Date date = entry.getLastModifiedDate();
        assertEquals(entry.getTime(), date.getTime());
    }

    // equals(): same instance reference is equal to itself
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("eq.txt");
        assertTrue(entry.equals(entry));
    }

    // equals(): comparing to null returns false
    @Test
    public void testEquals_null_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("eq2.txt");
        assertFalse(entry.equals(null));
    }

    // equals(): comparing to a different class returns false
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("eq3.txt");
        assertFalse(entry.equals("not an entry"));
    }

    // equals(): different names make entries unequal
    @Test
    public void testEquals_differentName_false() throws Throwable {
        ZipArchiveEntry a = new ZipArchiveEntry("name1.txt");
        ZipArchiveEntry b = new ZipArchiveEntry("name2.txt");
        assertFalse(a.equals(b));
    }

    // equals(): same name but different comment makes entries unequal
    @Test
    public void testEquals_differentComment_false() throws Throwable {
        ZipArchiveEntry a = new ZipArchiveEntry("same.txt");
        ZipArchiveEntry b = new ZipArchiveEntry("same.txt");
        a.setComment("hello");
        b.setComment("world");
        assertFalse(a.equals(b));
    }

    // equals(): two freshly constructed entries with the same name are equal
    @Test
    public void testEquals_identicalDefaults_true() throws Throwable {
        ZipArchiveEntry a = new ZipArchiveEntry("ident.txt");
        ZipArchiveEntry b = new ZipArchiveEntry("ident.txt");
        assertTrue(a.equals(b));
    }
}
