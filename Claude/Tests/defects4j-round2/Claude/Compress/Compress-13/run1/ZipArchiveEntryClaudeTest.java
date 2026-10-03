package org.apache.commons.compress.archivers.zip;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.ArchiveEntry;

public class ZipArchiveEntryClaudeTest {

    // String constructor: non-directory name -> isDirectory() false
    @Test
    public void testConstructorString_setsNameAndIsDirectoryFalse() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("simple.txt");
        assertEquals("simple.txt", entry.getName());
        assertFalse(entry.isDirectory());
    }

    // String constructor: name ending with "/" -> isDirectory() true
    @Test
    public void testConstructorString_directoryName_isDirectoryTrue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir/");
        assertTrue(entry.isDirectory());
    }

    // ZipEntry constructor: copies name, size and method when method is set
    @Test
    public void testConstructorZipEntry_copiesNameSizeAndMethod() throws Throwable {
        ZipEntry je = new ZipEntry("copy.txt");
        je.setMethod(8);
        je.setSize(50L);
        ZipArchiveEntry entry = new ZipArchiveEntry(je);
        assertEquals("copy.txt", entry.getName());
        assertEquals(50L, entry.getSize());
        assertEquals(8, entry.getMethod());
    }

    // ZipEntry constructor: source method unspecified (-1) must not throw per
    // "method has not been specified" contract documented on the method field
    @Test
    public void testConstructorZipEntry_methodNotSet_doesNotThrow() throws Throwable {
        ZipEntry je = new ZipEntry("nomethod.txt");
        ZipArchiveEntry entry = new ZipArchiveEntry(je);
        assertEquals(-1, entry.getMethod());
    }

    // ZipEntry constructor: null extra -> setExtra() branch produces empty local extra
    @Test
    public void testConstructorZipEntry_extraNull_setsEmptyLocalExtra() throws Throwable {
        ZipEntry je = new ZipEntry("noextra.txt");
        je.setMethod(0);
        ZipArchiveEntry entry = new ZipArchiveEntry(je);
        assertEquals(0, entry.getLocalFileDataExtra().length);
    }

    // Copy constructor: copies internal/external attributes and name
    @Test
    public void testCopyConstructor_copiesInternalAndExternalAttributesAndName() throws Throwable {
        ZipArchiveEntry source = new ZipArchiveEntry("src.txt");
        source.setMethod(8);
        source.setInternalAttributes(3);
        source.setExternalAttributes(99L);
        ZipArchiveEntry copy = new ZipArchiveEntry(source);
        assertEquals(3, copy.getInternalAttributes());
        assertEquals(99L, copy.getExternalAttributes());
        assertEquals("src.txt", copy.getName());
    }

    // Protected no-arg constructor delegates to ZipArchiveEntry("")
    @Test
    public void testProtectedNoArgConstructor_emptyName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry();
        assertEquals("", entry.getName());
    }

    // File constructor: non-existent file -> not a file, size stays unknown
    @Test
    public void testConstructorFile_nonExistentFile_sizeUnknownAndNameUnchanged() throws Throwable {
        File f = new File("no-such-file-zae-test-xyz.tmp");
        ZipArchiveEntry entry = new ZipArchiveEntry(f, "entry.txt");
        assertEquals("entry.txt", entry.getName());
        assertEquals(ArchiveEntry.SIZE_UNKNOWN, entry.getSize());
    }

    // clone(): copies attributes, but internal state becomes independent afterwards
    @Test
    public void testClone_copiesAttributesAndIsIndependent() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("clone.txt");
        entry.setMethod(8);
        entry.setInternalAttributes(5);
        entry.setExternalAttributes(10L);
        ZipArchiveEntry clone = (ZipArchiveEntry) entry.clone();
        assertEquals("clone.txt", clone.getName());
        assertEquals(8, clone.getMethod());
        assertEquals(5, clone.getInternalAttributes());
        clone.setInternalAttributes(99);
        assertEquals(5, entry.getInternalAttributes());
        assertEquals(99, clone.getInternalAttributes());
    }

    // getMethod(): default value -1 (unspecified)
    @Test
    public void testGetMethod_default_isMinusOne() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("meth.txt");
        assertEquals(-1, entry.getMethod());
    }

    // setMethod()/getMethod(): valid non-negative value stored and returned
    @Test
    public void testSetGetMethod_validValue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("meth2.txt");
        entry.setMethod(8);
        assertEquals(8, entry.getMethod());
    }

    // setMethod(): negative value throws IllegalArgumentException
    @Test
    public void testSetMethod_negative_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("meth3.txt");
        try {
            entry.setMethod(-5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // get/setInternalAttributes round trip
    @Test
    public void testGetSetInternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ia.txt");
        entry.setInternalAttributes(7);
        assertEquals(7, entry.getInternalAttributes());
    }

    // get/setExternalAttributes round trip
    @Test
    public void testGetSetExternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ea.txt");
        entry.setExternalAttributes(123456789L);
        assertEquals(123456789L, entry.getExternalAttributes());
    }

    // setUnixMode(): owner-writable mode -> read-only bit clear; mode round trips via getUnixMode()
    @Test
    public void testSetUnixMode_writableFile_readOnlyFlagClearAndModeRoundTrip() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        entry.setUnixMode(0755);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
        assertEquals(0L, entry.getExternalAttributes() & 1L);
        assertEquals(0755, entry.getUnixMode());
    }

    // setUnixMode(): owner not writable -> read-only bit set
    @Test
    public void testSetUnixMode_readOnlyFile_readOnlyFlagSet() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ro.txt");
        entry.setUnixMode(0555);
        assertEquals(1L, entry.getExternalAttributes() & 1L);
    }

    // setUnixMode(): directory entry -> MS-DOS directory flag 0x10 set
    @Test
    public void testSetUnixMode_directoryEntry_directoryFlagSet() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir2/");
        entry.setUnixMode(0755);
        assertEquals(0x10L, entry.getExternalAttributes() & 0x10L);
    }

    // getUnixMode(): platform not UNIX -> always 0
    @Test
    public void testGetUnixMode_platformNotUnix_returnsZero() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("p.txt");
        assertEquals(0, entry.getUnixMode());
    }

    // getPlatform() default FAT, setPlatform() (protected) updates it
    @Test
    public void testGetPlatform_defaultAndSetPlatformUpdates() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("plat.txt");
        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry.getPlatform());
        entry.setPlatform(ZipArchiveEntry.PLATFORM_UNIX);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
    }

    // setExtraFields(empty array) -> both getExtraFields() overloads return empty
    @Test
    public void testSetExtraFields_emptyArray_resultsInEmptyExtraFields() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e.txt");
        entry.setExtraFields(new ZipExtraField[0]);
        assertEquals(0, entry.getExtraFields().length);
        assertEquals(0, entry.getExtraFields(true).length);
    }

    // getExtraFields(): default (no fields ever set) is empty for both overloads
    @Test
    public void testGetExtraFields_defaultEmptyBothOverloads() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ef.txt");
        assertEquals(0, entry.getExtraFields().length);
        assertEquals(0, entry.getExtraFields(true).length);
    }

    // removeExtraField(): no fields exist -> NoSuchElementException
    @Test
    public void testRemoveExtraField_whenNoFieldsExist_throwsNoSuchElementException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e4.txt");
        try {
            entry.removeExtraField(null);
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // getExtraField(): no fields exist -> returns null
    @Test
    public void testGetExtraField_whenNoFieldsExist_returnsNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e5.txt");
        assertNull(entry.getExtraField(null));
    }

    // removeUnparseableExtraFieldData(): none exist -> NoSuchElementException
    @Test
    public void testRemoveUnparseableExtraFieldData_whenNoneExist_throwsNoSuchElementException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e6.txt");
        try {
            entry.removeUnparseableExtraFieldData();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // getUnparseableExtraFieldData(): default null
    @Test
    public void testGetUnparseableExtraFieldData_defaultNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("e7.txt");
        assertNull(entry.getUnparseableExtraFieldData());
    }

    // setExtra(empty array): parses to no fields, local extra stays empty, no exception
    @Test
    public void testSetExtra_emptyArray_doesNotThrowAndExtraIsEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("x.txt");
        entry.setExtra(new byte[0]);
        assertEquals(0, entry.getLocalFileDataExtra().length);
    }

    // setExtra(): data too short to form a header must be consumed as unparseable, not throw
    @Test
    public void testSetExtra_malformedData_consumedAsUnparseableNotThrown() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("x2.txt");
        entry.setExtra(new byte[] { 1, 0 });
        assertNotNull(entry.getUnparseableExtraFieldData());
    }

    // setCentralDirectoryExtra(empty array): no exception, empty central extra
    @Test
    public void testSetCentralDirectoryExtra_emptyArray_doesNotThrow() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("cd.txt");
        entry.setCentralDirectoryExtra(new byte[0]);
        assertEquals(0, entry.getCentralDirectoryExtra().length);
    }

    // getLocalFileDataExtra(): when underlying extra is null, returns empty (not null) array
    @Test
    public void testGetLocalFileDataExtra_whenExtraNull_returnsEmptyArray() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("ld.txt");
        byte[] extra = entry.getLocalFileDataExtra();
        assertNotNull(extra);
        assertEquals(0, extra.length);
    }

    // getCentralDirectoryExtra(): default (no fields) is empty array
    @Test
    public void testGetCentralDirectoryExtra_defaultEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("cd2.txt");
        assertEquals(0, entry.getCentralDirectoryExtra().length);
    }

    // getName(): overridden getter returns the name set via constructor
    @Test
    public void testGetName_returnsConstructorName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("folder/file.txt");
        assertEquals("folder/file.txt", entry.getName());
    }

    // setName() (protected): changes the entry name
    @Test
    public void testSetName_protected_changesEntryName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("old.txt");
        entry.setName("new.txt");
        assertEquals("new.txt", entry.getName());
    }

    // get/setSize(): valid non-negative value round trip
    @Test
    public void testGetSetSize_validValue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("s.txt");
        entry.setSize(12345L);
        assertEquals(12345L, entry.getSize());
    }

    // setSize(): negative value throws IllegalArgumentException
    @Test
    public void testSetSize_negative_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("s2.txt");
        try {
            entry.setSize(-1L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getSize(): default value is SIZE_UNKNOWN
    @Test
    public void testGetSize_default_isSizeUnknown() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("s3.txt");
        assertEquals(ArchiveEntry.SIZE_UNKNOWN, entry.getSize());
    }

    // setName(name, rawName) (protected): rawName default null, then getRawName() returns a defensive copy
    @Test
    public void testSetNameWithRawNameAndDefault_rawNameBehavior() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("raw.txt");
        assertNull(entry.getRawName());
        byte[] raw = new byte[] { 65, 66, 67 };
        entry.setName("raw.txt", raw);
        byte[] result = entry.getRawName();
        assertNotNull(result);
        assertEquals(3, result.length);
        assertNotSame(raw, result);
    }

    // hashCode(): based solely on the entry name
    @Test
    public void testHashCode_matchesNameHashCode() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("hash.txt");
        assertEquals("hash.txt".hashCode(), entry.hashCode());
    }

    // getGeneralPurposeBit(): default instance is never null
    @Test
    public void testGetGeneralPurposeBit_defaultNotNull() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("g.txt");
        assertNotNull(entry.getGeneralPurposeBit());
    }

    // setGeneralPurposeBit(): stored reference is returned as-is by getter
    @Test
    public void testSetGeneralPurposeBit_updatesReference() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("g2.txt");
        GeneralPurposeBit bit = new GeneralPurposeBit();
        entry.setGeneralPurposeBit(bit);
        assertSame(bit, entry.getGeneralPurposeBit());
    }

    // getLastModifiedDate(): reflects the time set via setTime()
    @Test
    public void testGetLastModifiedDate_reflectsSetTime() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("t.txt");
        entry.setTime(100000L);
        Date date = entry.getLastModifiedDate();
        assertEquals(100000L, date.getTime());
    }

    // equals(): same instance reference -> true
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("eq.txt");
        assertTrue(entry.equals(entry));
    }

    // equals(): null argument and different-class argument both -> false
    @Test
    public void testEquals_nullOrDifferentClass_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("eqc.txt");
        assertFalse(entry.equals(null));
        assertFalse(entry.equals("eqc.txt"));
    }

    // equals(): same name and default attributes on two separate instances -> true
    @Test
    public void testEquals_sameNameAndDefaults_true() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("same.txt");
        ZipArchiveEntry e2 = new ZipArchiveEntry("same.txt");
        assertTrue(e1.equals(e2));
    }

    // equals(): different names -> false
    @Test
    public void testEquals_differentName_false() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("a.txt");
        ZipArchiveEntry e2 = new ZipArchiveEntry("b.txt");
        assertFalse(e1.equals(e2));
    }

    // equals(): same name but different compression method -> false
    @Test
    public void testEquals_differentMethod_false() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("m1.txt");
        ZipArchiveEntry e2 = new ZipArchiveEntry("m1.txt");
        e1.setMethod(8);
        e2.setMethod(0);
        assertFalse(e1.equals(e2));
    }
}
