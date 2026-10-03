package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Random;

public class RandomStringUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        RandomStringUtils utils = new RandomStringUtils();
        assertNotNull(utils);
    }

    @Test
    public void testRandomCountZero() throws Throwable {
        String result = RandomStringUtils.random(0);
        assertEquals("", result);
    }

    @Test
    public void testRandomCountNegative() throws Throwable {
        try {
            RandomStringUtils.random(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("less than 0"));
        }
    }

    @Test
    public void testRandomAscii() throws Throwable {
        String result = RandomStringUtils.randomAscii(10);
        assertNotNull(result);
        assertEquals(10, result.length());
    }

    @Test
    public void testRandomAlphabetic() throws Throwable {
        String result = RandomStringUtils.randomAlphabetic(5);
        assertNotNull(result);
        assertEquals(5, result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            assertTrue(Character.isLetter(ch));
        }
    }

    @Test
    public void testRandomAlphanumeric() throws Throwable {
        String result = RandomStringUtils.randomAlphanumeric(8);
        assertNotNull(result);
        assertEquals(8, result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            assertTrue(Character.isLetterOrDigit(ch));
        }
    }

    @Test
    public void testRandomNumeric() throws Throwable {
        String result = RandomStringUtils.randomNumeric(6);
        assertNotNull(result);
        assertEquals(6, result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            assertTrue(Character.isDigit(ch));
        }
    }

    @Test
    public void testRandomWithLettersAndNumbersFlags() throws Throwable {
        String result = RandomStringUtils.random(5, true, true);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithStartAndEnd() throws Throwable {
        String result = RandomStringUtils.random(5, 65, 90, true, false);
        assertNotNull(result);
        assertEquals(5, result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            assertTrue(ch >= 65 && ch < 90);
        }
    }

    @Test
    public void testRandomWithCharArray() throws Throwable {
        char[] set = new char[] { 'a', 'b', 'c' };
        String result = RandomStringUtils.random(4, 0, 3, false, false, set);
        assertNotNull(result);
        assertEquals(4, result.length());
    }

    @Test
    public void testRandomWithStringChars() throws Throwable {
        String result = RandomStringUtils.random(5, "xyz");
        assertNotNull(result);
        assertEquals(5, result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            assertTrue(ch == 'x' || ch == 'y' || ch == 'z');
        }
    }

    @Test
    public void testRandomWithStringNullChars() throws Throwable {
        String result = RandomStringUtils.random(3, (String) null);
        assertNotNull(result);
        assertEquals(3, result.length());
    }

    @Test
    public void testRandomWithCharVarargs() throws Throwable {
        String result = RandomStringUtils.random(4, new char[] { 'p', 'q', 'r' });
        assertNotNull(result);
        assertEquals(4, result.length());
    }

    @Test
    public void testRandomWithNullCharVarargs() throws Throwable {
        char[] nullArray = null;
        String result = RandomStringUtils.random(3, nullArray);
        assertNotNull(result);
        assertEquals(3, result.length());
    }

    @Test
    public void testRandomWithCustomRandomGenerator() throws Throwable {
        Random rnd = new Random(12345L);
        String result = RandomStringUtils.random(10, 0, 0, false, false, null, rnd);
        assertNotNull(result);
        assertEquals(10, result.length());
    }

    @Test
    public void testSurrogateHandling() throws Throwable {
        // Force the character set and random to produce surrogate ranges
        // High surrogate range: 55296 to 56191
        // Low surrogate range: 56320 to 57343
        // Private high surrogate range: 56192 to 56319
        char[] surrogateSet = new char[100];
        int idx = 0;
        for (int i = 55296; i <= 57343; i += 20) {
            if (idx < surrogateSet.length) {
                surrogateSet[idx++] = (char) i;
            }
        }
        // Fill remainder safely
        while (idx < surrogateSet.length) {
            surrogateSet[idx++] = 'a';
        }

        Random rnd = new Random(42L);
        String result = RandomStringUtils.random(10, 0, surrogateSet.length, false, false, surrogateSet, rnd);
        assertNotNull(result);
    }
}