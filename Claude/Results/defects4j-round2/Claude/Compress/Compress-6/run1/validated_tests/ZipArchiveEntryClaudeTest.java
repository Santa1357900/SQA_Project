package org.apache.commons.compress.archivers.zip;

import java.io.File;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.junit.Test;
import static org.junit.Assert.*;

public class ZipArchiveEntryClaudeTest {

    // Constructor(String): internal name field stays null, getName() delegates to super
    @Test
    public void testConstructorString_setsName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("test.txt");
        assertEquals("test.txt", entry.getName());
    }

    // isDirectory() true branch: name ends with "/"
    @Test
    public void testConstructorString_directoryNameEndsWithSlash_isDirectoryTrue() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir/");
        assertTrue(entry.isDirectory());
    }

    // isDirectory() false branch: name does not end with "/"
    @Test
    public void testConstructorString_nonDirectoryName_isDirectoryFalse() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        assertFalse(entry.isDirectory());
    }

    // protected no-arg constructor delegates to this("")
    @Test
    public void testProtectedNoArgConstructor_emptyName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry();
        assertEquals("", entry.getName());
    }

    // File constructor: nonexistent file -> isDirectory()==false so name unchanged
    @Test
    public void testConstructorFileString_nonExistentFile_nameUnchanged() throws Throwable {
        File file = new File("zzz_does_not_exist_12345");
        ZipArchiveEntry entry = new ZipArchiveEntry(file, "entry.txt");
        assertEquals("entry.txt", entry.getName());
        assertFalse(entry.isDirectory());
    }

    // File constructor: entryName already ends with "/" -> no extra slash appended
    @Test
    public void testConstructorFileString_nameAlreadyEndsWithSlash_noDoubleSlash() throws Throwable {
        File file = new File("zzz_nonexistent_dir_abc");
        ZipArchiveEntry entry = new ZipArchiveEntry(file, "dir/");
        assertEquals("dir/", entry.getName());
    }

    // ZipEntry constructor: default method is -1, setMethod(-1) throws IllegalArgumentException
    @Test
    public void testConstructorZipEntry_methodNotSet_throwsIllegalArgumentException() throws Throwable {
        ZipEntry ze = new ZipEntry("x.txt");
        try {
            new ZipArchiveEntry(ze);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ZipEntry constructor: method set -> name and method copied correctly
    @Test
    public void testConstructorZipEntry_methodSet_copiesNameAndMethod() throws Throwable {
        ZipEntry ze = new ZipEntry("copy.txt");
        ze.setMethod(ZipEntry.STORED);
        ZipArchiveEntry zae = new ZipArchiveEntry(ze);
        assertEquals("copy.txt", zae.getName());
        assertEquals(ZipEntry.STORED, zae.getMethod());
    }

    // ZipEntry constructor: null extra -> setExtra() called, resulting local extra is empty
    @Test
    public void testConstructorZipEntry_nullExtra_setsEmptyLocalFileDataExtra() throws Throwable {
        ZipEntry ze = new ZipEntry("e1.txt");
        ze.setMethod(ZipEntry.STORED);
        ZipArchiveEntry zae = new ZipArchiveEntry(ze);
        byte[] extra = zae.getLocalFileDataExtra();
        assertNotNull(extra);
        assertEquals(0, extra.length);
    }

    // ZipEntry constructor: non-null (empty) extra -> parse branch executed without exception
    @Test
    public void testConstructorZipEntry_nonNullEmptyExtra_noException() throws Throwable {
        ZipEntry ze = new ZipEntry("e2.txt");
        ze.setMethod(ZipEntry.STORED);
        ze.setExtra(new byte[0]);
        ZipArchiveEntry zae = new ZipArchiveEntry(ze);
        assertEquals("e2.txt", zae.getName());
        assertEquals(0, zae.getExtraFields().length);
    }

    // Copy constructor: attributes copied from source ZipArchiveEntry
    @Test
    public void testCopyConstructor_copiesAttributes() throws Throwable {
        ZipArchiveEntry original = new ZipArchiveEntry("orig.txt");
        original.setMethod(ZipEntry.DEFLATED);
        original.setInternalAttributes(7);
        original.setExternalAttributes(999L);
        original.setExtraFields(new ZipExtraField[0]);
        ZipArchiveEntry copy = new ZipArchiveEntry(original);
        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getMethod(), copy.getMethod());
        assertEquals(original.getInternalAttributes(), copy.getInternalAttributes());
        assertEquals(original.getExternalAttributes(), copy.getExternalAttributes());
    }

    // clone(): produces a distinct object with equal attribute values
    @Test
    public void testClone_returnsEqualButDistinctInstance() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("clone.txt");
        entry.setMethod(ZipEntry.STORED);
        entry.setInternalAttributes(3);
        entry.setExternalAttributes(55L);
        entry.setExtraFields(new ZipExtraField[0]);
        ZipArchiveEntry cloned = (ZipArchiveEntry) entry.clone();
        assertNotSame(entry, cloned);
        assertEquals(entry.getName(), cloned.getName());
        assertEquals(entry.getMethod(), cloned.getMethod());
        assertEquals(entry.getInternalAttributes(), cloned.getInternalAttributes());
        assertEquals(entry.getExternalAttributes(), cloned.getExternalAttributes());
        assertEquals(0, cloned.getExtraFields().length);
    }

    // isSupportedCompressionMethod(): default method -1 is unsupported
    @Test
    public void testIsSupportedCompressionMethod_default_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        assertFalse(entry.isSupportedCompressionMethod());
    }

    // isSupportedCompressionMethod(): STORED is supported
    @Test
    public void testIsSupportedCompressionMethod_stored_true() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipEntry.STORED);
        assertTrue(entry.isSupportedCompressionMethod());
    }

    // isSupportedCompressionMethod(): DEFLATED is supported
    @Test
    public void testIsSupportedCompressionMethod_deflated_true() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(ZipEntry.DEFLATED);
        assertTrue(entry.isSupportedCompressionMethod());
    }

    // isSupportedCompressionMethod(): other method value is unsupported
    @Test
    public void testIsSupportedCompressionMethod_otherMethod_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(99);
        assertFalse(entry.isSupportedCompressionMethod());
    }

    // setMethod(): negative value throws IllegalArgumentException
    @Test
    public void testSetMethod_negative_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        try {
            entry.setMethod(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // setMethod()/getMethod(): boundary value 0 is accepted
    @Test
    public void testSetMethod_zero_getMethodReturnsZero() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setMethod(0);
        assertEquals(0, entry.getMethod());
    }

    // getInternalAttributes()/setInternalAttributes(): simple round trip
    @Test
    public void testGetSetInternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setInternalAttributes(42);
        assertEquals(42, entry.getInternalAttributes());
        entry.setInternalAttributes(-5);
        assertEquals(-5, entry.getInternalAttributes());
    }

    // getExternalAttributes()/setExternalAttributes(): simple round trip
    @Test
    public void testGetSetExternalAttributes() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setExternalAttributes(123456789L);
        assertEquals(123456789L, entry.getExternalAttributes());
    }



    // setUnixMode(): owner-write bit present -> read-only flag not set
    @Test
    public void testSetUnixMode_writableBit() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        entry.setUnixMode(0666);
        assertEquals(0L, entry.getExternalAttributes() & 1L);
    }

    // setUnixMode(): directory entry sets MS-DOS directory flag bit
    @Test
    public void testSetUnixMode_directoryFlag() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dir/");
        entry.setUnixMode(0755);
        assertTrue((entry.getExternalAttributes() & 0x10L) != 0L);
    }

    // getUnixMode(): platform not UNIX -> returns 0
    @Test
    public void testGetUnixMode_platformNotUnix_returnsZero() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        assertEquals(0, entry.getUnixMode());
    }

    // getPlatform(): default is PLATFORM_FAT
    @Test
    public void testGetPlatform_default_isFat() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry.getPlatform());
    }

    // setPlatform() (protected, same package): changes returned platform
    @Test
    public void testSetPlatform_protected_changesPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        entry.setPlatform(ZipArchiveEntry.PLATFORM_UNIX);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
    }

    // setExtraFields(empty array)/getExtraFields(): 0-iteration loop, empty result
    @Test
    public void testSetExtraFields_emptyArray_getExtraFieldsEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        entry.setExtraFields(new ZipExtraField[0]);
        assertEquals(0, entry.getExtraFields().length);
    }

    // getExtraFields(): null internal map -> returns empty array, not null
    @Test
    public void testGetExtraFields_default_emptyArray() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        ZipExtraField[] fields = entry.getExtraFields();
        assertNotNull(fields);
        assertEquals(0, fields.length);
    }

    // getLocalFileDataExtra(): no extra ever set -> non-null empty array
    @Test
    public void testGetLocalFileDataExtra_default_nonNullEmpty() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        byte[] extra = entry.getLocalFileDataExtra();
        assertNotNull(extra);
        assertEquals(0, extra.length);
    }

    // setExtra(byte[]): empty array parses without throwing RuntimeException
    @Test
    public void testSetExtra_emptyArray_noException() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
        entry.setExtra(new byte[0]);
        byte[] extra = entry.getLocalFileDataExtra();
        assertNotNull(extra);
        assertEquals(0, extra.length);
    }

    // setName() (protected, same package): internal name field takes priority over super
    @Test
    public void testSetName_overridesSuperGetName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("original.txt");
        entry.setName("renamed.txt");
        assertEquals("renamed.txt", entry.getName());
    }

    // hashCode(): documented to be based on getName()
    @Test
    public void testHashCode_equalsNameHashCode() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("hash.txt");
        assertEquals("hash.txt".hashCode(), entry.hashCode());
    }

    // equals(): reflexive - same instance
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        assertTrue(entry.equals(entry));
    }

    // equals(): comparing against null returns false
    @Test
    public void testEquals_null_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        assertFalse(entry.equals(null));
    }

    // equals(): comparing against different class returns false
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        assertFalse(entry.equals("a.txt"));
    }

    // equals(): same name on two distinct instances -> equal
    @Test
    public void testEquals_sameNameDifferentInstances_true() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("same.txt");
        ZipArchiveEntry e2 = new ZipArchiveEntry("same.txt");
        assertTrue(e1.equals(e2));
        assertTrue(e2.equals(e1));
    }

    // Bug hunt: equals() must reflect getName() (used by hashCode()); entries built via the
    // public String constructor with different names must never be considered equal.
    @Test
    public void testEquals_differentNames_notEqual() throws Throwable {
        ZipArchiveEntry e1 = new ZipArchiveEntry("foo.txt");
        ZipArchiveEntry e2 = new ZipArchiveEntry("bar.txt");
        assertFalse(e1.equals(e2));
    }

    // getLastModifiedDate(): wraps getTime() in a java.util.Date
    @Test
    public void testGetLastModifiedDate_returnsDateFromTime() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("a.txt");
        entry.setTime(100000L);
        Date d = entry.getLastModifiedDate();
        assertEquals(100000L, d.getTime());
    }
}
