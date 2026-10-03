package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

public class SystemUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        SystemUtils utils = new SystemUtils();
        assertNotNull(utils);
    }

    @Test
    public void testGetJavaHome() throws Throwable {
        File home = SystemUtils.getJavaHome();
        assertNotNull(home);
        assertEquals(System.getProperty("java.home"), home.getAbsolutePath());
    }

    @Test
    public void testGetJavaIoTmpDir() throws Throwable {
        File tmp = SystemUtils.getJavaIoTmpDir();
        assertNotNull(tmp);
        assertEquals(System.getProperty("java.io.tmpdir"), tmp.getAbsolutePath());
    }

    @Test
    public void testGetUserDir() throws Throwable {
        File dir = SystemUtils.getUserDir();
        assertNotNull(dir);
        assertEquals(System.getProperty("user.dir"), dir.getAbsolutePath());
    }

    @Test
    public void testGetUserHome() throws Throwable {
        File home = SystemUtils.getUserHome();
        assertNotNull(home);
        assertEquals(System.getProperty("user.home"), home.getAbsolutePath());
    }

    @Test
    public void testIsJavaAwtHeadless() throws Throwable {
        boolean headless = SystemUtils.isJavaAwtHeadless();
        String prop = System.getProperty("java.awt.headless");
        if (prop != null) {
            assertEquals(Boolean.parseBoolean(prop), headless);
        } else {
            assertFalse(headless);
        }
    }

    @Test
    public void testIsJavaVersionAtLeastFloat() throws Throwable {
        boolean result = SystemUtils.isJavaVersionAtLeast(1.1f);
        assertTrue(result);
        boolean highResult = SystemUtils.isJavaVersionAtLeast(99.0f);
        assertFalse(highResult);
    }

    @Test
    public void testIsJavaVersionAtLeastInt() throws Throwable {
        boolean result = SystemUtils.isJavaVersionAtLeast(100);
        assertTrue(result);
        boolean highResult = SystemUtils.isJavaVersionAtLeast(9999);
        assertFalse(highResult);
    }

    @Test
    public void testIsJavaVersionMatch() throws Throwable {
        assertTrue(SystemUtils.isJavaVersionMatch("1.6.0_20", "1.6"));
        assertFalse(SystemUtils.isJavaVersionMatch("1.6.0_20", "1.5"));
        assertFalse(SystemUtils.isJavaVersionMatch(null, "1.6"));
    }

    @Test
    public void testIsOSMatch() throws Throwable {
        assertTrue(SystemUtils.isOSMatch("Windows 7", "6.1", "Windows", "6.1"));
        assertFalse(SystemUtils.isOSMatch("Linux", "3.0", "Windows", "6.1"));
        assertFalse(SystemUtils.isOSMatch(null, "6.1", "Windows", "6.1"));
        assertFalse(SystemUtils.isOSMatch("Windows 7", null, "Windows", "6.1"));
    }

    @Test
    public void testIsOSNameMatch() throws Throwable {
        assertTrue(SystemUtils.isOSNameMatch("Linux", "Linux"));
        assertFalse(SystemUtils.isOSNameMatch("Windows", "Linux"));
        assertFalse(SystemUtils.isOSNameMatch(null, "Linux"));
    }

    @Test
    public void testToJavaVersionFloat() throws Throwable {
        assertEquals(1.6f, SystemUtils.toJavaVersionFloat("1.6.0_20"), 0.0001f);
        assertEquals(1.2f, SystemUtils.toJavaVersionFloat("1.2"), 0.0001f);
        assertEquals(0f, SystemUtils.toJavaVersionFloat(null), 0.0001f);
    }

    @Test
    public void testToJavaVersionInt() throws Throwable {
        assertEquals(160f, SystemUtils.toJavaVersionInt("1.6.0_20"), 0.0001f);
        assertEquals(120f, SystemUtils.toJavaVersionInt("1.2"), 0.0001f);
        assertEquals(0f, SystemUtils.toJavaVersionInt(null), 0.0001f);
    }

    @Test
    public void testToJavaVersionIntArray() throws Throwable {
        int[] arr1 = SystemUtils.toJavaVersionIntArray("1.5.0_21");
        assertNotNull(arr1);
        assertEquals(3, arr1.length);
        assertEquals(1, arr1[0]);
        assertEquals(5, arr1[1]);
        assertEquals(0, arr1[2]);

        int[] arrNull = SystemUtils.toJavaVersionIntArray(null);
        assertNotNull(arrNull);
        assertEquals(0, arrNull.length);

        int[] arrSingle = SystemUtils.toJavaVersionIntArray("9");
        assertNotNull(arrSingle);
        assertEquals(1, arrSingle.length);
        assertEquals(9, arrSingle[0]);
    }
}