package org.apache.commons.lang3;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

public class SystemUtilsClaudeTest {

    // covers public no-arg constructor
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        SystemUtils su = new SystemUtils();
        assertNotNull(su);
    }

    // covers getJavaHome() happy path against System.getProperty contract
    @Test
    public void testGetJavaHome_returnsFileFromSystemProperty() throws Throwable {
        File expected = new File(System.getProperty("java.home"));
        assertEquals(expected, SystemUtils.getJavaHome());
    }

    // covers getJavaIoTmpDir() happy path against System.getProperty contract
    @Test
    public void testGetJavaIoTmpDir_returnsFileFromSystemProperty() throws Throwable {
        File expected = new File(System.getProperty("java.io.tmpdir"));
        assertEquals(expected, SystemUtils.getJavaIoTmpDir());
    }

    // covers getUserDir() happy path against System.getProperty contract
    @Test
    public void testGetUserDir_returnsFileFromSystemProperty() throws Throwable {
        File expected = new File(System.getProperty("user.dir"));
        assertEquals(expected, SystemUtils.getUserDir());
    }

    // covers getUserHome() happy path against System.getProperty contract
    @Test
    public void testGetUserHome_returnsFileFromSystemProperty() throws Throwable {
        File expected = new File(System.getProperty("user.home"));
        assertEquals(expected, SystemUtils.getUserHome());
    }

    // covers both branches of isJavaAwtHeadless() ternary via the documented contract
    @Test
    public void testIsJavaAwtHeadless_matchesContractUsingField() throws Throwable {
        boolean expected = SystemUtils.JAVA_AWT_HEADLESS != null
                ? SystemUtils.JAVA_AWT_HEADLESS.equals(Boolean.TRUE.toString()) : false;
        assertEquals(expected, SystemUtils.isJavaAwtHeadless());
    }

    // covers isJavaVersionAtLeast(float) true branch at the equality boundary
    @Test
    public void testIsJavaVersionAtLeastFloat_equalToCurrent_true() throws Throwable {
        assertTrue(SystemUtils.isJavaVersionAtLeast(SystemUtils.JAVA_VERSION_FLOAT));
    }

    // covers isJavaVersionAtLeast(float) false branch
    @Test
    public void testIsJavaVersionAtLeastFloat_greaterThanCurrent_false() throws Throwable {
        assertFalse(SystemUtils.isJavaVersionAtLeast(Float.MAX_VALUE));
    }

    // covers isJavaVersionAtLeast(int) true branch at the equality boundary
    @Test
    public void testIsJavaVersionAtLeastInt_equalToCurrent_true() throws Throwable {
        assertTrue(SystemUtils.isJavaVersionAtLeast(SystemUtils.JAVA_VERSION_INT));
    }

    // covers isJavaVersionAtLeast(int) false branch
    @Test
    public void testIsJavaVersionAtLeastInt_greaterThanCurrent_false() throws Throwable {
        assertFalse(SystemUtils.isJavaVersionAtLeast(Integer.MAX_VALUE));
    }

    // covers isJavaVersionMatch null-version branch
    @Test
    public void testIsJavaVersionMatch_nullVersion_false() throws Throwable {
        assertFalse(SystemUtils.isJavaVersionMatch(null, "1.6"));
    }

    // covers isJavaVersionMatch matching startsWith branch
    @Test
    public void testIsJavaVersionMatch_matchingPrefix_true() throws Throwable {
        assertTrue(SystemUtils.isJavaVersionMatch("1.6.0_23", "1.6"));
    }

    // covers isJavaVersionMatch non-matching startsWith branch
    @Test
    public void testIsJavaVersionMatch_nonMatchingPrefix_false() throws Throwable {
        assertFalse(SystemUtils.isJavaVersionMatch("1.5.0", "1.6"));
    }

    // covers isOSMatch osName==null branch
    @Test
    public void testIsOSMatch_nullOsName_false() throws Throwable {
        assertFalse(SystemUtils.isOSMatch(null, "5.1", "Windows", "5.1"));
    }

    // covers isOSMatch osVersion==null branch
    @Test
    public void testIsOSMatch_nullOsVersion_false() throws Throwable {
        assertFalse(SystemUtils.isOSMatch("Windows XP", null, "Windows", "5.1"));
    }

    // covers isOSMatch both startsWith true -> && true branch
    @Test
    public void testIsOSMatch_bothMatch_true() throws Throwable {
        assertTrue(SystemUtils.isOSMatch("Windows XP", "5.1.2600", "Windows", "5.1"));
    }

    // covers isOSMatch name mismatch short-circuiting &&
    @Test
    public void testIsOSMatch_nameMismatch_false() throws Throwable {
        assertFalse(SystemUtils.isOSMatch("Linux", "5.1", "Windows", "5.1"));
    }

    // covers isOSMatch name true but version mismatch in &&
    @Test
    public void testIsOSMatch_versionMismatch_false() throws Throwable {
        assertFalse(SystemUtils.isOSMatch("Windows XP", "6.0", "Windows", "5.1"));
    }

    // covers isOSNameMatch osName==null branch
    @Test
    public void testIsOSNameMatch_null_false() throws Throwable {
        assertFalse(SystemUtils.isOSNameMatch(null, "Windows"));
    }

    // covers isOSNameMatch matching startsWith branch
    @Test
    public void testIsOSNameMatch_matching_true() throws Throwable {
        assertTrue(SystemUtils.isOSNameMatch("Windows XP", "Windows"));
    }

    // covers isOSNameMatch non-matching startsWith branch
    @Test
    public void testIsOSNameMatch_nonMatching_false() throws Throwable {
        assertFalse(SystemUtils.isOSNameMatch("Linux", "Windows"));
    }

    // covers toVersionFloat length==1 branch via single-component version
    @Test
    public void testToJavaVersionFloat_oneComponent() throws Throwable {
        assertEquals(1f, SystemUtils.toJavaVersionFloat("1"), 0.0001f);
    }

    // covers toVersionFloat multi-component branch, Javadoc example 1.2f for Java 1.2
    @Test
    public void testToJavaVersionFloat_twoComponents() throws Throwable {
        assertEquals(1.2f, SystemUtils.toJavaVersionFloat("1.2"), 0.0001f);
    }

    // covers Javadoc example 1.31f for Java 1.3.1
    @Test
    public void testToJavaVersionFloat_threeComponents() throws Throwable {
        assertEquals(1.31f, SystemUtils.toJavaVersionFloat("1.3.1"), 0.0001f);
    }

    // covers trimming of patch release per Javadoc example 1.6f for Java 1.6.0_20
    @Test
    public void testToJavaVersionFloat_trimmedToThreeComponents() throws Throwable {
        assertEquals(1.6f, SystemUtils.toJavaVersionFloat("1.6.0_20"), 0.0001f);
    }

    // covers toVersionFloat null/empty-array branch returning zero
    @Test
    public void testToJavaVersionFloat_null_returnsZero() throws Throwable {
        assertEquals(0f, SystemUtils.toJavaVersionFloat(null), 0.0001f);
    }

    // covers toVersionInt len>=1,2,3 branches, Javadoc example 131 for Java 1.3.1
    @Test
    public void testToJavaVersionInt_threeComponents() throws Throwable {
        assertEquals(131f, SystemUtils.toJavaVersionInt("1.3.1"), 0.0001f);
    }

    // covers toVersionInt len>=2 but not len>=3 branch, Javadoc example 120 for Java 1.2
    @Test
    public void testToJavaVersionInt_twoComponents() throws Throwable {
        assertEquals(120f, SystemUtils.toJavaVersionInt("1.2"), 0.0001f);
    }

    // covers toVersionInt null-array branch returning zero
    @Test
    public void testToJavaVersionInt_null_returnsZero() throws Throwable {
        assertEquals(0f, SystemUtils.toJavaVersionInt(null), 0.0001f);
    }

    // covers toJavaVersionIntArray(String) version==null branch returning empty array
    @Test
    public void testToJavaVersionIntArray_null_returnsEmptyArray() throws Throwable {
        int[] result = SystemUtils.toJavaVersionIntArray(null);
        assertEquals(0, result.length);
    }

    // covers normal parse path with no trimming required
    @Test
    public void testToJavaVersionIntArray_twoComponents_noTrimNeeded() throws Throwable {
        int[] result = SystemUtils.toJavaVersionIntArray("1.2");
        assertTrue(Arrays.equals(new int[] {1, 2}, result));
    }

    // covers Javadoc example [1,3,1] for Java 1.3.1
    @Test
    public void testToJavaVersionIntArray_threeComponents() throws Throwable {
        int[] result = SystemUtils.toJavaVersionIntArray("1.3.1");
        assertTrue(Arrays.equals(new int[] {1, 3, 1}, result));
    }

    // Javadoc: "Converts the given Java version string to an int[] of MAXIMUM SIZE 3";
    // for Java 1.5.0_21-style input the array must be trimmed to 3 elements, e.g. [1,5,0]
    @Test
    public void testToJavaVersionIntArray_trimsToMaxSizeThree_bugCheck() throws Throwable {
        int[] result = SystemUtils.toJavaVersionIntArray("1.5.0.21");
        assertEquals(3, result.length);
        assertTrue(Arrays.equals(new int[] {1, 5, 0}, result));
    }

    // covers USER_COUNTRY ternary: prefers user.country, falls back to user.region
    @Test
    public void testUserCountry_derivedFromSystemPropertyTernary() throws Throwable {
        String country = System.getProperty("user.country");
        if (country != null) {
            assertEquals(country, SystemUtils.USER_COUNTRY);
        } else {
            assertEquals(System.getProperty("user.region"), SystemUtils.USER_COUNTRY);
        }
    }

}
