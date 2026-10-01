package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnixStatClaudeTest {

    // PERM_MASK should equal octal 07777 = decimal 4095 (mask covering setuid/setgid/sticky + rwxrwxrwx)
    @Test
    public void testPermMask_value_equals4095() throws Throwable {
        assertEquals(4095, UnixStat.PERM_MASK);
    }

    // LINK_FLAG should equal octal 0120000 = decimal 40960 (S_IFLNK from stat.h)
    @Test
    public void testLinkFlag_value_equals40960() throws Throwable {
        assertEquals(40960, UnixStat.LINK_FLAG);
    }

    // FILE_FLAG should equal octal 0100000 = decimal 32768 (S_IFREG from stat.h)
    @Test
    public void testFileFlag_value_equals32768() throws Throwable {
        assertEquals(32768, UnixStat.FILE_FLAG);
    }

    // DIR_FLAG should equal octal 040000 = decimal 16384 (S_IFDIR from stat.h)
    @Test
    public void testDirFlag_value_equals16384() throws Throwable {
        assertEquals(16384, UnixStat.DIR_FLAG);
    }

    // DEFAULT_LINK_PERM should equal octal 0777 = decimal 511 (rwxrwxrwx)
    @Test
    public void testDefaultLinkPerm_value_equals511() throws Throwable {
        assertEquals(511, UnixStat.DEFAULT_LINK_PERM);
    }

    // DEFAULT_DIR_PERM should equal octal 0755 = decimal 493 (rwxr-xr-x)
    @Test
    public void testDefaultDirPerm_value_equals493() throws Throwable {
        assertEquals(493, UnixStat.DEFAULT_DIR_PERM);
    }

    // DEFAULT_FILE_PERM should equal octal 0644 = decimal 420 (rw-r--r--)
    @Test
    public void testDefaultFilePerm_value_equals420() throws Throwable {
        assertEquals(420, UnixStat.DEFAULT_FILE_PERM);
    }

    // Ordering: DIR_FLAG < FILE_FLAG < LINK_FLAG per stat.h type codes (0040000 < 0100000 < 0120000)
    @Test
    public void testTypeFlags_ordering_dirLessThanFileLessThanLink() throws Throwable {
        assertTrue(UnixStat.DIR_FLAG < UnixStat.FILE_FLAG);
        assertTrue(UnixStat.FILE_FLAG < UnixStat.LINK_FLAG);
    }

    // The three type flags must be pairwise distinct values
    @Test
    public void testTypeFlags_arePairwiseDistinct() throws Throwable {
        assertTrue(UnixStat.DIR_FLAG != UnixStat.FILE_FLAG);
        assertTrue(UnixStat.DIR_FLAG != UnixStat.LINK_FLAG);
        assertTrue(UnixStat.FILE_FLAG != UnixStat.LINK_FLAG);
    }

    // All default permission values must fit entirely within PERM_MASK (no stray higher bits)
    @Test
    public void testDefaultLinkPerm_isWithinPermMask() throws Throwable {
        assertEquals(UnixStat.DEFAULT_LINK_PERM, UnixStat.DEFAULT_LINK_PERM & UnixStat.PERM_MASK);
    }

    @Test
    public void testDefaultDirPerm_isWithinPermMask() throws Throwable {
        assertEquals(UnixStat.DEFAULT_DIR_PERM, UnixStat.DEFAULT_DIR_PERM & UnixStat.PERM_MASK);
    }

    @Test
    public void testDefaultFilePerm_isWithinPermMask() throws Throwable {
        assertEquals(UnixStat.DEFAULT_FILE_PERM, UnixStat.DEFAULT_FILE_PERM & UnixStat.PERM_MASK);
    }

    // PERM_MASK bits must not intersect with any of the three type flag bit regions
    @Test
    public void testPermMask_doesNotOverlapDirFlag() throws Throwable {
        assertEquals(0, UnixStat.PERM_MASK & UnixStat.DIR_FLAG);
    }

    @Test
    public void testPermMask_doesNotOverlapFileFlag() throws Throwable {
        assertEquals(0, UnixStat.PERM_MASK & UnixStat.FILE_FLAG);
    }

    @Test
    public void testPermMask_doesNotOverlapLinkFlag() throws Throwable {
        assertEquals(0, UnixStat.PERM_MASK & UnixStat.LINK_FLAG);
    }

    // Combining DIR_FLAG with DEFAULT_DIR_PERM must reconstruct the full stat.h mode 040755 = 16877
    @Test
    public void testCombinedDirMode_equalsExpectedStatMode() throws Throwable {
        int mode = UnixStat.DIR_FLAG | UnixStat.DEFAULT_DIR_PERM;
        assertEquals(16877, mode);
    }

    // Combining FILE_FLAG with DEFAULT_FILE_PERM must reconstruct the full stat.h mode 0100644 = 33188
    @Test
    public void testCombinedFileMode_equalsExpectedStatMode() throws Throwable {
        int mode = UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM;
        assertEquals(33188, mode);
    }

    // Combining LINK_FLAG with DEFAULT_LINK_PERM must reconstruct the full stat.h mode 0120777 = 41471
    @Test
    public void testCombinedLinkMode_equalsExpectedStatMode() throws Throwable {
        int mode = UnixStat.LINK_FLAG | UnixStat.DEFAULT_LINK_PERM;
        assertEquals(41471, mode);
    }

    // The combined dir/file/link modes must themselves be pairwise distinct (sanity on composition)
    @Test
    public void testCombinedModes_arePairwiseDistinct() throws Throwable {
        int dirMode = UnixStat.DIR_FLAG | UnixStat.DEFAULT_DIR_PERM;
        int fileMode = UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM;
        int linkMode = UnixStat.LINK_FLAG | UnixStat.DEFAULT_LINK_PERM;
        assertTrue(dirMode != fileMode);
        assertTrue(dirMode != linkMode);
        assertTrue(fileMode != linkMode);
    }

    // All seven published constants must be mutually unique values (no accidental duplication)
    @Test
    public void testAllConstants_areMutuallyUnique() throws Throwable {
        int[] values = new int[] {
            UnixStat.PERM_MASK,
            UnixStat.LINK_FLAG,
            UnixStat.FILE_FLAG,
            UnixStat.DIR_FLAG,
            UnixStat.DEFAULT_LINK_PERM,
            UnixStat.DEFAULT_DIR_PERM,
            UnixStat.DEFAULT_FILE_PERM
        };
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertTrue(values[i] != values[j]);
            }
        }
    }

    // PERM_MASK must be strictly greater than every default permission value (it is the superset mask)
    @Test
    public void testPermMask_isGreaterThanAllDefaultPerms() throws Throwable {
        assertTrue(UnixStat.PERM_MASK > UnixStat.DEFAULT_LINK_PERM);
        assertTrue(UnixStat.PERM_MASK > UnixStat.DEFAULT_DIR_PERM);
        assertTrue(UnixStat.PERM_MASK > UnixStat.DEFAULT_FILE_PERM);
    }

    // PERM_MASK must be strictly less than the smallest type flag (DIR_FLAG) since they occupy higher bits
    @Test
    public void testPermMask_isLessThanDirFlag() throws Throwable {
        assertTrue(UnixStat.PERM_MASK < UnixStat.DIR_FLAG);
    }

    // DEFAULT_DIR_PERM must be strictly less than DEFAULT_LINK_PERM (0755 < 0777) per their octal literals
    @Test
    public void testDefaultDirPerm_lessThanDefaultLinkPerm() throws Throwable {
        assertTrue(UnixStat.DEFAULT_DIR_PERM < UnixStat.DEFAULT_LINK_PERM);
    }

    // DEFAULT_FILE_PERM must be strictly less than DEFAULT_DIR_PERM (0644 < 0755) per their octal literals
    @Test
    public void testDefaultFilePerm_lessThanDefaultDirPerm() throws Throwable {
        assertTrue(UnixStat.DEFAULT_FILE_PERM < UnixStat.DEFAULT_DIR_PERM);
    }

    // All constants must be strictly positive, non-zero values (they encode real mode bits)
    @Test
    public void testAllConstants_arePositive() throws Throwable {
        assertTrue(UnixStat.PERM_MASK > 0);
        assertTrue(UnixStat.LINK_FLAG > 0);
        assertTrue(UnixStat.FILE_FLAG > 0);
        assertTrue(UnixStat.DIR_FLAG > 0);
        assertTrue(UnixStat.DEFAULT_LINK_PERM > 0);
        assertTrue(UnixStat.DEFAULT_DIR_PERM > 0);
        assertTrue(UnixStat.DEFAULT_FILE_PERM > 0);
    }

    // LINK_FLAG shares its high-order bit with FILE_FLAG under stat.h encoding: ANDing yields FILE_FLAG's bit
    @Test
    public void testLinkFlagAndFileFlag_bitwiseAndEqualsFileFlag() throws Throwable {
        assertEquals(UnixStat.FILE_FLAG, UnixStat.LINK_FLAG & UnixStat.FILE_FLAG);
    }

    // DIR_FLAG shares no bits with FILE_FLAG under stat.h encoding (disjoint bit positions 14 and 15)
    @Test
    public void testDirFlagAndFileFlag_bitwiseAndIsZero() throws Throwable {
        assertEquals(0, UnixStat.DIR_FLAG & UnixStat.FILE_FLAG);
    }

    // PERM_MASK applied to itself via AND is idempotent (self-mask identity property)
    @Test
    public void testPermMask_andWithItself_isIdentity() throws Throwable {
        assertEquals(UnixStat.PERM_MASK, UnixStat.PERM_MASK & UnixStat.PERM_MASK);
    }
}
